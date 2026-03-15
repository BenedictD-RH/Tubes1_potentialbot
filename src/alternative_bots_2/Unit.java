package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Unit extends BaseRobot {

    public static MapLocation spawnLocation;
    public static Direction currentExploreDirection = null;
    public static MapLocation[] knownPaintTowers = new MapLocation[25];
    public static int knownPaintTowerCount = 0;
    public static MapLocation[] knownAllyTowers = new MapLocation[25];
    public static int knownAllyTowerCount = 0;
    public static MapLocation[] knownRuins = new MapLocation[30];
    public static int knownRuinCount = 0;
    public static MapLocation returnLocation = null;
    public static MapLocation lastKnownEnemyLoc = null;
    public static int lastKnownEnemyRound = -1;
    public static MapLocation cachedPaintTowerLoc = null;

    // Bug2 pathfinding
    private static boolean huggingWall = false;
    private static Direction wallDir = null;
    private static int bugStartDist = 0;
    private static MapLocation bugTarget = null;

    // XOR-shift RNG
    private static int rngState = 6147;

    // 9-point exploration
    private static MapLocation[] exploreTargets9 = null;
    private static int exploreIdx9 = 0;

    // Fungsi untuk inisialisasi unit
    public static void initUnit() {
        if (spawnLocation == null) {
            spawnLocation = rc.getLocation();
            rngState ^= rc.getID();
        }
    }

    public static int rand() {
        rngState ^= rngState << 13;
        rngState ^= rngState >> 17;
        rngState ^= rngState << 15;
        return rngState & 0x7FFFFFFF;
    }

    // Fungsi untuk pathfinding Bug2
    public static void bugNavigateTo(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) return;
        MapLocation myLoc = rc.getLocation();
        if (myLoc.equals(target)) return;

        // Auto-reset saat target berubah
        if (bugTarget == null || !bugTarget.equals(target)) {
            huggingWall = false;
            bugTarget = target;
        }

        Direction dir = myLoc.directionTo(target);
        if (rc.canMove(dir)) {
            huggingWall = false;
            rc.move(dir);
        } else {
            if (!huggingWall) {
                huggingWall = true;
                wallDir = dir;
                bugStartDist = myLoc.distanceSquaredTo(target);
            }
            for (int i = 0; i < 8; i++) {
                wallDir = wallDir.rotateRight();
                if (rc.canMove(wallDir)) {
                    rc.move(wallDir);
                    break;
                }
            }
            MapLocation newLoc = rc.getLocation();
            Direction nd = newLoc.directionTo(target);
            if (rc.canMove(nd) && newLoc.distanceSquaredTo(target) < bugStartDist) {
                huggingWall = false;
            }
        }
    }

    // Backward compat — delegate ke Bug2
    public static void moveGreedy(MapLocation target) throws GameActionException {
        bugNavigateTo(target);
    }

    public static void moveSimple(MapLocation target) throws GameActionException {
        bugNavigateTo(target);
    }

    public static boolean tryMove(Direction dir) throws GameActionException {
        if (rc.canMove(dir)) { rc.move(dir); return true; }
        return false;
    }

    // Fungsi untuk eksplorasi 9 titik
    private static MapLocation currentExploreTarget9() {
        if (exploreTargets9 == null) {
            int W = rc.getMapWidth(), H = rc.getMapHeight();
            int mX = W / 2, mY = H / 2;
            exploreTargets9 = new MapLocation[]{
                new MapLocation(2, 2),
                new MapLocation(W - 3, H - 3),
                new MapLocation(2, H - 3),
                new MapLocation(W - 3, 2),
                new MapLocation(mX, mY),
                new MapLocation(mX, 2),
                new MapLocation(2, mY),
                new MapLocation(W - 3, mY),
                new MapLocation(mX, H - 3),
            };
            exploreIdx9 = rand() % exploreTargets9.length;
        }
        MapLocation t = exploreTargets9[exploreIdx9 % exploreTargets9.length];
        if (rc.getLocation().distanceSquaredTo(t) <= 8) {
            exploreIdx9 = (exploreIdx9 + 1) % exploreTargets9.length;
            t = exploreTargets9[exploreIdx9];
        }
        return t;
    }

    public static void explore() throws GameActionException {
        if (!rc.isMovementReady()) return;
        bugNavigateTo(currentExploreTarget9());
    }

    // Fungsi untuk upgrade tower oleh unit
    public static boolean tryUpgradeNearbyTower() throws GameActionException {
        if (rc.getMoney() < 2500 || rc.getNumberTowers() < 3) return false;
        if (!rc.isActionReady()) return false;

        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        RobotInfo bestTower = null;
        int bestPriority = 0;

        for (RobotInfo ally : allies) {
            if (!isTowerType(ally.getType())) continue;
            int priority = 0;
            if (ally.getType() == UnitType.LEVEL_ONE_PAINT_TOWER) priority = 100;
            else if (ally.getType() == UnitType.LEVEL_ONE_MONEY_TOWER) priority = 80;
            else if (ally.getType() == UnitType.LEVEL_TWO_PAINT_TOWER) priority = 60;
            else if (ally.getType() == UnitType.LEVEL_TWO_MONEY_TOWER) priority = 50;
            else if (ally.getType() == UnitType.LEVEL_ONE_DEFENSE_TOWER) priority = 40;
            else if (ally.getType() == UnitType.LEVEL_TWO_DEFENSE_TOWER) priority = 30;

            if (priority > bestPriority) {
                bestPriority = priority;
                bestTower = ally;
            }
        }

        if (bestTower != null && rc.canUpgradeTower(bestTower.getLocation())) {
            rc.upgradeTower(bestTower.getLocation());
            return true;
        }
        return false;
    }

    // Fungsi untuk mengecat tile saat eksplorasi
    public static void paintNearbyTile() throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation myLoc = rc.getLocation();

        // Cat tile di bawah diri dulu
        if (rc.canSenseLocation(myLoc)) {
            MapInfo info = rc.senseMapInfo(myLoc);
            if (info.isPassable() && !info.getPaint().isAlly()) {
                PaintType mark = info.getMark();
                boolean useSec = (mark == PaintType.ALLY_SECONDARY);
                if (rc.canAttack(myLoc)) {
                    rc.attack(myLoc, useSec);
                    return;
                }
            }
        }

        // Cat tile kosong terdekat dalam attack range
        MapInfo[] tiles = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;
        boolean bestSec = false;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (paint == PaintType.EMPTY) {
                MapLocation loc = tile.getMapLocation();
                int dist = myLoc.distanceSquaredTo(loc);
                if (dist < minDist && rc.canAttack(loc)) {
                    minDist = dist;
                    best = loc;
                    bestSec = (tile.getMark() == PaintType.ALLY_SECONDARY);
                }
            }
        }

        if (best != null) {
            rc.attack(best, bestSec);
        }
    }

    // Fungsi untuk estimasi lokasi musuh
    public static MapLocation resolveEnemyLocation() throws GameActionException {
        if (lastKnownEnemyLoc != null && rc.getRoundNum() - lastKnownEnemyRound < 100) {
            return lastKnownEnemyLoc;
        }
        int W = rc.getMapWidth(), H = rc.getMapHeight();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                MapLocation aLoc = ally.getLocation();
                return new MapLocation(W - 1 - aLoc.x, H - 1 - aLoc.y);
            }
        }
        return new MapLocation(W / 2, H / 2);
    }

    // Fungsi untuk helper SRP
    public static boolean isResourcePatternCenter(MapLocation loc) {
        return loc.x % 4 == 2 && loc.y % 4 == 2;
    }

    public static MapLocation snapToSRPGrid(MapLocation loc) {
        int x = (loc.x / 4) * 4 + 2;
        int y = (loc.y / 4) * 4 + 2;
        int W = rc.getMapWidth(), H = rc.getMapHeight();
        x = Math.max(2, Math.min(W - 3, x));
        y = Math.max(2, Math.min(H - 3, y));
        return new MapLocation(x, y);
    }

    public static boolean canStartSRP(MapLocation loc) throws GameActionException {
        if (!isResourcePatternCenter(loc)) return false;
        if (!rc.canSenseLocation(loc)) return false;
        if (rc.senseMapInfo(loc).getMark() != PaintType.EMPTY) return false;
        MapInfo[] area = rc.senseNearbyMapInfos(loc, 8);
        for (MapInfo t : area) {
            if (t.hasRuin()) return false;
        }
        return rc.canMarkResourcePattern(loc);
    }

    // Fungsi untuk mengisi ulang paint
    public static boolean refillPaint() throws GameActionException {
        int paintCapacity = rc.getType().paintCapacity;
        int currentPaint = rc.getPaint();
        int paintPercent = (currentPaint * 100) / paintCapacity;
        if (paintPercent > REFILL_PAINT_PERCENT) return false;

        MapLocation nearestPaintTower = findNearestPaintTower();
        if (nearestPaintTower == null) nearestPaintTower = findNearestKnownPaintTower();
        if (nearestPaintTower == null && cachedPaintTowerLoc != null) nearestPaintTower = cachedPaintTowerLoc;
        if (nearestPaintTower == null && spawnLocation != null) nearestPaintTower = spawnLocation;
        if (nearestPaintTower == null) return false;

        int dist = rc.getLocation().distanceSquaredTo(nearestPaintTower);
        if (dist <= 2) {
            withdrawPaint(nearestPaintTower);
        } else if (rc.isMovementReady()) {
            bugNavigateTo(nearestPaintTower);
        }
        return true;
    }

    private static MapLocation findNearestPaintTower() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) {
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = ally.getLocation();
                }
                addKnownPaintTower(ally.getLocation());
            }
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
            }
        }
        return nearest;
    }

    private static MapLocation findNearestKnownPaintTower() {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < knownPaintTowerCount; i++) {
            int dist = myLoc.distanceSquaredTo(knownPaintTowers[i]);
            if (dist < minDist) { minDist = dist; nearest = knownPaintTowers[i]; }
        }
        return nearest;
    }

    private static void withdrawPaint(MapLocation towerLoc) throws GameActionException {
        if (!rc.isActionReady()) return;
        int needed = rc.getType().paintCapacity - rc.getPaint();
        if (needed <= 0) return;
        if (rc.canSenseRobotAtLocation(towerLoc)) {
            RobotInfo tower = rc.senseRobotAtLocation(towerLoc);
            if (tower != null && tower.getTeam() == rc.getTeam()) {
                int available = tower.getPaintAmount();
                int transfer = Math.min(needed, available);
                if (transfer > 0 && rc.canTransferPaint(towerLoc, -transfer)) {
                    rc.transferPaint(towerLoc, -transfer);
                }
            }
        }
    }

    // Fungsi untuk broadcast dan komunikasi
    public static void broadcastEnemyLocation(MapLocation enemyLoc) throws GameActionException {
        int msg = encodeMessage(MSG_ENEMY_LOC, enemyLoc.x, enemyLoc.y, rc.getRoundNum() % EXTRA_MASK);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    public static void broadcastEnemyTower(MapLocation towerLoc) throws GameActionException {
        int msg = encodeMessage(MSG_ENEMY_TOWER, towerLoc.x, towerLoc.y, rc.getRoundNum() % EXTRA_MASK);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    public static void requestMopper(MapLocation loc) throws GameActionException {
        int msg = encodeMessage(MSG_MOPPER_REQ, loc.x, loc.y, rc.getRoundNum() % EXTRA_MASK);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    public static void broadcastRuin(MapLocation ruinLoc) throws GameActionException {
        int msg = encodeMessage(MSG_RUIN_LOC, ruinLoc.x, ruinLoc.y, rc.getRoundNum() % EXTRA_MASK);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int sent = 0;
        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.getLocation(), msg)) {
                rc.sendMessage(ally.getLocation(), msg);
                if (++sent >= 3) break;
            }
        }
    }

    public static void broadcastRuinReady(MapLocation ruinLoc) throws GameActionException {
        int msg = encodeMessage(MSG_RUIN_READY, ruinLoc.x, ruinLoc.y, rc.getRoundNum() % EXTRA_MASK);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    public static MapLocation readBroadcastTarget(int msgType) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        if (messages.length == 0) return null;
        MapLocation bestLoc = null;
        int bestDist = Integer.MAX_VALUE;
        int currentRound = rc.getRoundNum();

        for (Message m : messages) {
            int msg = m.getBytes();
            int type = decodeType(msg);
            if (type != msgType) continue;
            int sentRound = decodeExtra(msg);
            int age = (currentRound - sentRound) % EXTRA_MASK;
            if (age <= 10 || age > 2000) {
                int x = decodeX(msg);
                int y = decodeY(msg);
                MapLocation loc = new MapLocation(x, y);
                int dist = rc.getLocation().distanceSquaredTo(loc);
                if (dist < bestDist) { bestDist = dist; bestLoc = loc; }
            }
        }
        return bestLoc;
    }

    public static void processMessages() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        int currentRound = rc.getRoundNum();
        for (Message m : messages) {
            int msg = m.getBytes();
            int type = decodeType(msg);
            int x = decodeX(msg);
            int y = decodeY(msg);
            MapLocation loc = new MapLocation(x, y);
            switch (type) {
                case MSG_TOWER_LOC:
                    addKnownAllyTower(loc);
                    break;
                case MSG_ENEMY_LOC:
                    lastKnownEnemyLoc = loc;
                    lastKnownEnemyRound = currentRound;
                    break;
                case MSG_RUIN_LOC:
                    addKnownRuin(loc);
                    break;
                case MSG_PAINT_TOWER:
                    addKnownPaintTower(loc);
                    addKnownAllyTower(loc);
                    cachedPaintTowerLoc = loc;
                    break;
                default:
                    break;
            }
        }
    }

    // Fungsi untuk memindai lingkungan
    public static void scanEnvironment() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
                if (isPaintTower(ally.getType())) {
                    addKnownPaintTower(ally.getLocation());
                }
            }
        }

        if (enemies.length > 0) {
            lastKnownEnemyLoc = enemies[0].getLocation();
            lastKnownEnemyRound = rc.getRoundNum();
            for (RobotInfo enemy : enemies) {
                if (isTowerType(enemy.getType())) {
                    broadcastEnemyTower(enemy.getLocation());
                    break;
                }
            }
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                if (!rc.canSenseRobotAtLocation(ruinLoc) ||
                    (rc.canSenseRobotAtLocation(ruinLoc) &&
                     !isTowerType(rc.senseRobotAtLocation(ruinLoc).getType()))) {
                    addKnownRuin(ruinLoc);
                }
            }
        }
    }

    // Fungsi utilitas
    public static void wander() throws GameActionException {
        if (!rc.isMovementReady()) return;
        if (currentExploreDirection == null || !rc.canMove(currentExploreDirection)) {
            currentExploreDirection = directions[rand() % directions.length];
        }
        if (rc.canMove(currentExploreDirection)) {
            rc.move(currentExploreDirection);
        } else {
            for (int i = 0; i < 8; i++) {
                currentExploreDirection = currentExploreDirection.rotateRight();
                if (rc.canMove(currentExploreDirection)) {
                    rc.move(currentExploreDirection);
                    return;
                }
            }
        }
    }

    public static void paintUnderSelf() throws GameActionException {
        if (!rc.isActionReady()) return;
        MapLocation myLoc = rc.getLocation();
        if (!rc.canSenseLocation(myLoc)) return;
        MapInfo info = rc.senseMapInfo(myLoc);
        if (!info.getPaint().isAlly() && info.isPassable() && rc.canAttack(myLoc)) {
            PaintType mark = info.getMark();
            boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
            rc.attack(myLoc, useSecondary);
        }
    }

    // Fungsi untuk manajemen cache
    public static void addKnownPaintTower(MapLocation loc) {
        for (int i = 0; i < knownPaintTowerCount; i++) {
            if (knownPaintTowers[i].equals(loc)) return;
        }
        if (knownPaintTowerCount < knownPaintTowers.length) {
            knownPaintTowers[knownPaintTowerCount++] = loc;
        }
    }

    public static void addKnownAllyTower(MapLocation loc) {
        for (int i = 0; i < knownAllyTowerCount; i++) {
            if (knownAllyTowers[i].equals(loc)) return;
        }
        if (knownAllyTowerCount < knownAllyTowers.length) {
            knownAllyTowers[knownAllyTowerCount++] = loc;
        }
    }

    public static void addKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) return;
        }
        if (knownRuinCount < knownRuins.length) {
            knownRuins[knownRuinCount++] = loc;
        }
    }

    public static MapLocation findNearestKnownRuin() {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < knownRuinCount; i++) {
            int dist = myLoc.distanceSquaredTo(knownRuins[i]);
            if (dist < minDist) { minDist = dist; nearest = knownRuins[i]; }
        }
        return nearest;
    }

    public static void removeKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) {
                knownRuins[i] = knownRuins[knownRuinCount - 1];
                knownRuinCount--;
                return;
            }
        }
    }

    public static int getGamePhase() {
        int round = rc.getRoundNum();
        if (round < EARLY_GAME_END) return 0;
        if (round < MID_GAME_END) return 1;
        return 2;
    }

    public static boolean isLowPaint() {
        return (rc.getPaint() * 100 / rc.getType().paintCapacity) < LOW_PAINT_PERCENT;
    }

    public static int countNearbyAllies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam()).length;
    }

    public static int countNearbyEnemies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;
    }
}
