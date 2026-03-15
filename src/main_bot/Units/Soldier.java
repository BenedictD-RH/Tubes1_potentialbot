package main_bot.Units;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;
import main_bot.Unit;

public class Soldier extends Unit {

    // State untuk soldier
    private static final int STATE_EXPLORE      = 0;
    private static final int STATE_BUILD_RUIN   = 1;
    private static final int STATE_ATTACK_TOWER = 2;
    private static final int STATE_BUILD_SRP    = 3;
    private static final int STATE_EXPAND_SRP   = 4;
    private static final int STATE_RETREAT      = 5;

    private static int state = STATE_EXPLORE; // default state
    private static MapLocation modeTarget = null;

    // SRP
    private static MapLocation[] srpCheckLocs = null;
    private static int srpCheckIdx = 0;

    // Chip saving wait
    private static int ruinWaitStart = 0;
    private static final int RUIN_WAIT_MAX = 35;

    // Fungsi untuk menjalankan soldier
    public static void run() throws GameActionException {
        initUnit();
        processMessages();
        scanEnvironment();
        tryUpgradeNearbyTower();

        // Refill paint ketika paint < 5%
        int paint = rc.getPaint();
        int paintMax = rc.getType().paintCapacity;
        if (paint * 20 < paintMax) {
            doRetreat();
            return;
        }
        // bergerak setelah paint terisi
        if (state == STATE_RETREAT && paint * 100 / paintMax >= 8) {
            state = STATE_EXPLORE;
        }

        // Mencari target penting
        if (state == STATE_EXPLORE || state == STATE_EXPAND_SRP) {
            checkForImportantTargets();
        }
        executeState();
    }

    // Fungsi untuk memetakan state yang ada
    private static void executeState() throws GameActionException {
        switch (state) {
            case STATE_BUILD_RUIN:   executeBuildRuin(); break;
            case STATE_ATTACK_TOWER: executeAttackTower(); break;
            case STATE_BUILD_SRP:    executeBuildSRP(); break;
            case STATE_EXPAND_SRP:   executeExpandSRP(); break;
            case STATE_RETREAT:      doRetreat(); break;
            default:                 executeExplore(); break;
        }
    }

    // Fungsi untuk mengecek target penting (Enemy Tower > Empty ruin > SRP)
    private static void checkForImportantTargets() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Enemy tower -> attack
        for (RobotInfo e : enemies) {
            if (isTowerType(e.getType())) {
                modeTarget = e.getLocation();
                state = STATE_ATTACK_TOWER;
                broadcastEnemyTower(e.getLocation());
                return;
            }
        }

        // Empty ruin -> paint
        MapLocation nearRuin = findNearbyEmptyRuin();
        if (nearRuin != null) {
            modeTarget = nearRuin;
            state = STATE_BUILD_RUIN;
            broadcastRuin(nearRuin);
            return;
        }

        // Membuat SRP
        MapLocation myLoc = rc.getLocation();
        MapLocation srpCand = snapToSRPGrid(myLoc);
        if (srpCand.distanceSquaredTo(myLoc) <= 2 && canStartSRP(srpCand)) {
            modeTarget = srpCand;
            state = STATE_BUILD_SRP;
            return;
        }
    }

    // Fungsi untuk eksplorasi
    private static void executeExplore() throws GameActionException {
        // Paint tile saat explore
        if (rc.isActionReady()) paintNearbyTile();

        // Cek broadcast ruin
        MapLocation ruinMsg = readBroadcastTarget(MSG_RUIN_LOC);
        if (ruinMsg != null) {
            addKnownRuin(ruinMsg);
            modeTarget = ruinMsg;
            state = STATE_BUILD_RUIN;
        }

        // Cek known ruin
        if (state == STATE_EXPLORE) {
            MapLocation knownRuin = findNearestKnownRuin();
            if (knownRuin != null && rc.getMoney() >= 700) {
                modeTarget = knownRuin;
                state = STATE_BUILD_RUIN;
            }
        }
        if (rc.isMovementReady()) explore();
        // Paint lagi setelah move
        if (rc.isActionReady()) paintNearbyTile();
    }

    // Fungsi untuk membangun tower di ruin 
    private static void executeBuildRuin() throws GameActionException {
        if (modeTarget == null) { state = STATE_EXPLORE; return; }
        MapLocation myLoc = rc.getLocation();
        MapLocation ruinLoc = modeTarget;

        if (rc.canSenseLocation(ruinLoc)) {
            if (rc.canSenseRobotAtLocation(ruinLoc)) {
                RobotInfo r = rc.senseRobotAtLocation(ruinLoc);
                if (r != null && r.getTeam() == rc.getTeam() && isTowerType(r.getType())) {
                    removeKnownRuin(ruinLoc);
                    modeTarget = null;
                    state = STATE_EXPLORE;
                    return;
                }
            }
        }

        // dekati
        if (!rc.canSenseLocation(ruinLoc)) {
            if (rc.isMovementReady()) bugNavigateTo(ruinLoc);
            return;
        }
        // Claim ruin 
        ensureRuinClaimed(ruinLoc);

        // Menentukan tipe tower dan mengeksekusinya
        UnitType towerType = getNewTowerType();
        if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
            rc.completeTowerPattern(towerType, ruinLoc);
            onTowerBuilt(towerType, ruinLoc);
            return;
        }
        positionAroundRuin(myLoc, ruinLoc);
        if (rc.isActionReady() && rc.getPaint() >= 5) {
            boolean[][] pattern = rc.getTowerPattern(towerType);
            if (!trySelfPaint(myLoc, ruinLoc, pattern)) {
                MapLocation enemyRef = resolveEnemyLocation();
                paintBestPatternTile(ruinLoc, pattern, enemyRef);
            }
            if (rc.canCompleteTowerPattern(towerType, ruinLoc)) {
                rc.completeTowerPattern(towerType, ruinLoc);
                onTowerBuilt(towerType, ruinLoc);
                return;
            }
        }

        // Chip saving jika pattern penuh tapi chip kurang
        int dist = myLoc.distanceSquaredTo(ruinLoc);
        if (dist <= 8 && rc.getPaint() >= 5) {
            boolean[][] pattern = rc.getTowerPattern(towerType);
            if (isPatternFullyPainted(ruinLoc, pattern)) {
                broadcastRuinReady(ruinLoc);
                if (ruinWaitStart == 0) ruinWaitStart = rc.getRoundNum();
                if (rc.getRoundNum() - ruinWaitStart > RUIN_WAIT_MAX) {
                    ruinWaitStart = 0;
                }
            }
        }
    }

    // Fungsi untuk menangani setelah tower berhasil dibangun
    private static void onTowerBuilt(UnitType type, MapLocation loc) {
        if (isPaintTower(type)) addKnownPaintTower(loc);
        addKnownAllyTower(loc);
        removeKnownRuin(loc);
        modeTarget = null;
        ruinWaitStart = 0;
        state = STATE_EXPLORE;
    }

    // Fungsi untuk memastikan ruin sudah diklaim
    private static void ensureRuinClaimed(MapLocation ruinLoc) throws GameActionException {
        for (Direction d : Direction.values()) {
            MapLocation adj = ruinLoc.add(d);
            if (rc.canSenseLocation(adj)) {
                MapInfo info = rc.senseMapInfo(adj);
                if (info.getMark() != PaintType.EMPTY) return;
            }
        }
        MapLocation northLoc = ruinLoc.add(Direction.NORTH);
        if (rc.canMark(northLoc)) {
            rc.mark(northLoc, false);
        }
    }

    //  Fungsi untuk posisi setiap ruin
    private static void positionAroundRuin(MapLocation myLoc, MapLocation ruinLoc) throws GameActionException {
        if (!rc.isMovementReady()) return;
        if (myLoc.distanceSquaredTo(ruinLoc) > 2) {
            bugNavigateTo(ruinLoc);
            return;
        }
        Direction ruinToMe = ruinLoc.directionTo(myLoc);
        int cardIdx = ((ruinToMe.ordinal() / 2) + 1) % 4;
        MapLocation nextSpot = ruinLoc.add(cardinalDirs[cardIdx]);
        Direction moveDir = myLoc.directionTo(nextSpot);
        if (moveDir != Direction.CENTER && rc.canMove(moveDir)) {
            rc.move(moveDir);
        }
    }

    // Fungsi untuk soldier memberikan paint mark
    private static boolean trySelfPaint(MapLocation myLoc, MapLocation ruinLoc, boolean[][] pattern)
            throws GameActionException {
        int dx = myLoc.x - ruinLoc.x + 2;
        int dy = myLoc.y - ruinLoc.y + 2;
        if (dx < 0 || dx > 4 || dy < 0 || dy > 4) return false;
        boolean wantSec = pattern[dx][dy];
        PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
        if (!rc.canSenseLocation(myLoc)) return false;
        PaintType cur = rc.senseMapInfo(myLoc).getPaint();
        if (cur == want || cur.isEnemy()) return false;
        if (rc.canAttack(myLoc)) {
            rc.attack(myLoc, wantSec);
            return true;
        }
        return false;
    }

    // Fungsi untuk mengecat tile pattern terbaik
    private static void paintBestPatternTile(MapLocation ruinLoc, boolean[][] pattern, MapLocation enemyRef)
            throws GameActionException {
        int offset = rand() % 25;
        MapLocation bestLoc = null;
        boolean bestSec = false;
        int bestEnemyDist = Integer.MAX_VALUE;

        for (int i = 24; i >= 0; i--) {
            int idx = (i + offset) % 25;
            int dx = idx % 5;
            int dy = idx / 5;
            if (dx == 2 && dy == 2) continue;
            MapLocation tileLoc = ruinLoc.translate(dx - 2, dy - 2);
            if (!rc.canSenseLocation(tileLoc)) continue;
            if (!rc.canAttack(tileLoc)) continue;
            MapInfo info = rc.senseMapInfo(tileLoc);
            if (!info.isPassable()) continue;
            if (info.getPaint().isEnemy()) continue;
            boolean wantSec = pattern[dx][dy];
            PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
            if (info.getPaint() == want) continue;

            int d = tileLoc.distanceSquaredTo(enemyRef);
            if (d < bestEnemyDist) {
                bestEnemyDist = d;
                bestLoc = tileLoc;
                bestSec = wantSec;
            }
        }
        if (bestLoc != null && rc.canAttack(bestLoc)) {
            rc.attack(bestLoc, bestSec);
        }
    }

    // Fungsi untuk mengecek apakah pattern penuh
    private static boolean isPatternFullyPainted(MapLocation ruinLoc, boolean[][] pattern)
            throws GameActionException {
        for (int dx = 0; dx < 5; dx++) {
            for (int dy = 0; dy < 5; dy++) {
                if (dx == 2 && dy == 2) continue;
                MapLocation loc = ruinLoc.translate(dx - 2, dy - 2);
                if (!rc.canSenseLocation(loc)) continue;
                MapInfo info = rc.senseMapInfo(loc);
                if (!info.isPassable()) continue;
                PaintType want = pattern[dx][dy] ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                if (info.getPaint() != want) return false;
            }
        }
        return true;
    }

    // Fungsi untuk memilih tipe tower
    private static UnitType getNewTowerType() {
        int n = rc.getNumberTowers();
        if (n < 4) return UnitType.LEVEL_ONE_MONEY_TOWER;
        int cycle = (n - 4) % 3;
        if (cycle == 0) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    // Fungsi untuk menyerang tower musuh
    private static void executeAttackTower() throws GameActionException {
        if (modeTarget == null) { state = STATE_EXPLORE; return; }
        MapLocation myLoc = rc.getLocation();
        MapLocation tLoc = modeTarget;
        // Cek tower musuh
        if (rc.canSenseLocation(tLoc)) {
            if (!rc.canSenseRobotAtLocation(tLoc)) {
                modeTarget = null;
                state = STATE_EXPLORE;
                return;
            }
            RobotInfo r = rc.senseRobotAtLocation(tLoc);
            if (r == null || r.getTeam() == rc.getTeam()) {
                modeTarget = null;
                state = STATE_EXPLORE;
                return;
            }
        }

        int dist = myLoc.distanceSquaredTo(tLoc);
        // dekati
        if (dist > rc.getType().actionRadiusSquared) {
            if (rc.isMovementReady()) bugNavigateTo(tLoc);
        }
        // Attack
        if (rc.isActionReady() && rc.canAttack(tLoc)) {
            rc.attack(tLoc);
        }
        // mundur setelah serang
        if (rc.isMovementReady() && myLoc.distanceSquaredTo(tLoc) <= 9) {
            Direction away = tLoc.directionTo(myLoc);
            if (!tryMove(away)) {
                if (!tryMove(away.rotateLeft())) {
                    tryMove(away.rotateRight());
                }
            }
        }
    }

    // Fungsi untuk membangun SRP
    private static void executeBuildSRP() throws GameActionException {
        if (modeTarget == null) { state = STATE_EXPLORE; return; }
        MapLocation srpLoc = modeTarget;
        MapLocation myLoc = rc.getLocation();
        // Mark center sebagai klaim
        if (rc.canSenseLocation(srpLoc)) {
            MapInfo centerInfo = rc.senseMapInfo(srpLoc);
            if (centerInfo.getMark() == PaintType.EMPTY && rc.canMark(srpLoc)) {
                rc.mark(srpLoc, true);
            }
        }

        boolean[][] pattern = rc.getResourcePattern();
        boolean noTilePainted = true;

        if (rc.isActionReady() && rc.getPaint() >= 5) {
            int sdx = myLoc.x - srpLoc.x + 2;
            int sdy = myLoc.y - srpLoc.y + 2;
            if (sdx >= 0 && sdx <= 4 && sdy >= 0 && sdy <= 4) {
                boolean wantSec = pattern[sdx][sdy];
                PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                if (rc.canSenseLocation(myLoc)) {
                    PaintType cur = rc.senseMapInfo(myLoc).getPaint();
                    if (cur != want && !cur.isEnemy() && rc.canAttack(myLoc)) {
                        rc.attack(myLoc, wantSec);
                        noTilePainted = false;
                    }
                }
            }

            // Loop 25 tile, random offset
            if (rc.isActionReady()) {
                int offset = rand() % 25;
                for (int i = 24; i >= 0; i--) {
                    int idx = (i + offset) % 25;
                    int dx = idx % 5, dy = idx / 5;
                    MapLocation tileLoc = srpLoc.translate(dx - 2, dy - 2);
                    if (!rc.canSenseLocation(tileLoc) || !rc.canAttack(tileLoc)) continue;
                    MapInfo info = rc.senseMapInfo(tileLoc);
                    if (!info.isPassable() || info.getPaint().isEnemy()) continue;
                    boolean wantSec = pattern[dx][dy];
                    PaintType want = wantSec ? PaintType.ALLY_SECONDARY : PaintType.ALLY_PRIMARY;
                    if (info.getPaint() == want) { noTilePainted = false; continue; }
                    rc.attack(tileLoc, wantSec);
                    noTilePainted = false;
                    break;
                }
            }
        }

        // Menyelesaikan pattern
        if (rc.canCompleteResourcePattern(srpLoc)) {
            rc.completeResourcePattern(srpLoc);
            setupSRPExpand(srpLoc);
            return;
        }

        if (noTilePainted && rc.isMovementReady()) {
            bugNavigateTo(srpLoc);
        }
    }

    // Fungsi untuk menyiapkan 8 lokasi tetangga SRP yang akan dicek untuk ekspansi
    private static void setupSRPExpand(MapLocation srp) {
        srpCheckLocs = new MapLocation[]{
            srp.translate(4, 0), srp.translate(-4, 0),
            srp.translate(0, 4), srp.translate(0, -4),
            srp.translate(4, 4), srp.translate(4, -4),
            srp.translate(-4, 4), srp.translate(-4, -4),
        };
        srpCheckIdx = 0;
        state = STATE_EXPAND_SRP;
        modeTarget = null;
    }

    // Fungsi untuk melakukan expand SRP
    private static void executeExpandSRP() throws GameActionException {
        if (srpCheckLocs == null || srpCheckIdx >= srpCheckLocs.length) {
            state = STATE_EXPLORE;
            return;
        }
        MapLocation candidate = srpCheckLocs[srpCheckIdx];
        int W = rc.getMapWidth(), H = rc.getMapHeight();

        // Skip jika out of bounds
        if (candidate.x < 2 || candidate.x >= W - 2 || candidate.y < 2 || candidate.y >= H - 2) {
            srpCheckIdx++;
            return;
        }

        if (rc.canSenseLocation(candidate)) {
            if (canStartSRP(candidate)) {
                modeTarget = candidate;
                state = STATE_BUILD_SRP;
                return;
            }
            srpCheckIdx++;
            return;
        }
        if (rc.isMovementReady()) bugNavigateTo(candidate);
        if (rc.isActionReady()) paintNearbyTile();
    }

    // Fungsi untuk mundur ke paint tower terdekat dan mengisi ulang paint
    private static void doRetreat() throws GameActionException {
        state = STATE_RETREAT;
        // Cari paint tower visible
        MapLocation paintTower = null;
        int minDist = Integer.MAX_VALUE;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) {
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < minDist) { minDist = dist; paintTower = ally.getLocation(); }
            }
        }
        if (paintTower == null) paintTower = cachedPaintTowerLoc;
        if (paintTower == null && spawnLocation != null) paintTower = spawnLocation;

        if (paintTower != null) {
            int dist = rc.getLocation().distanceSquaredTo(paintTower);
            if (dist <= 2) {
                // Withdraw paint
                if (rc.canSenseRobotAtLocation(paintTower)) {
                    int needed = rc.getType().paintCapacity - rc.getPaint();
                    if (needed > 0 && rc.canTransferPaint(paintTower, -needed)) {
                        rc.transferPaint(paintTower, -needed);
                    }
                }
            } else if (rc.isMovementReady()) {
                bugNavigateTo(paintTower);
            }
        } else if (rc.isMovementReady()) {
            explore();
        }
    }

    // Fungsi pencarian ruin terdekat (A*)
    private static MapLocation findNearbyEmptyRuin() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;
        for (MapInfo tile : tiles) {
            if (!tile.hasRuin()) continue;
            MapLocation loc = tile.getMapLocation();
            if (rc.canSenseRobotAtLocation(loc)) {
                RobotInfo r = rc.senseRobotAtLocation(loc);
                if (r != null && isTowerType(r.getType())) {
                    removeKnownRuin(loc);
                    continue;
                }
            }
            int dist = myLoc.distanceSquaredTo(loc);
            if (dist < minDist) { minDist = dist; best = loc; }
        }
        return best;
    }
}
