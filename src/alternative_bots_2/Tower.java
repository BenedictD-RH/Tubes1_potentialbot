package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower extends BaseRobot {

    private static int spawnCount = 0;
    private static MapLocation lastEnemyLoc = null;
    private static int lastEnemyRound = -1;

    // Chip saving
    private static boolean waitingForChips = false;
    private static int waitingStartRound = 0;
    private static final int WAIT_TIMEOUT = 40;
    private static final int UPGRADE_CHIPS_THRESHOLD = 3000;

    public static void run() throws GameActionException {
        int round = rc.getRoundNum();
        int chips = rc.getMoney();
        int towers = rc.getNumberTowers();

        processIncomingMessages();

        // Upgrade SEBELUM attack agar action cooldown belum habis
        if (rc.isActionReady() && chips >= UPGRADE_CHIPS_THRESHOLD) {
            if (rc.canUpgradeTower(rc.getLocation())) {
                rc.upgradeTower(rc.getLocation());
            }
        }

        if (rc.isActionReady()) {
            attackEnemies();
        }

        // Chip saving: keluar jika timeout atau cukup chips
        if (waitingForChips) {
            if (round - waitingStartRound >= WAIT_TIMEOUT || chips >= 1000) {
                waitingForChips = false;
            }
        }

        if (rc.isActionReady() && !waitingForChips) {
            spawnUnit(round, towers, chips);
        }

        // Broadcast lokasi tower secara berkala
        if (round <= 10 || round % 15 == 0) {
            broadcastMyLocation();
        }
    }

    // Attack: kill-focus HP terendah, tie-break unit type priority
    private static void attackEnemies() throws GameActionException {
        if (!rc.isActionReady()) return;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        // Estimasi attack power berdasarkan tipe tower
        int atkPower = isDefenseTower(rc.getType()) ? 150 : 100;

        // Cek apakah semua musuh bisa one-shot
        boolean allOneShot = true;
        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.getLocation())) continue;
            if (e.health >= atkPower) { allOneShot = false; break; }
        }

        RobotInfo target = null;
        int bestVal = allOneShot ? Integer.MIN_VALUE : Integer.MAX_VALUE;

        for (RobotInfo e : enemies) {
            if (!rc.canAttack(e.getLocation())) continue;
            int hp = e.health;
            int typePriority = getTypePriority(e.getType());

            if (allOneShot) {
                // Cegah overkill: pilih HP TERTINGGI (paling bernilai)
                int val = hp * 10 + typePriority;
                if (val > bestVal) { bestVal = val; target = e; }
            } else {
                // Kill-focus: HP TERENDAH (selesaikan target)
                int val = hp * 10 - typePriority;
                if (val < bestVal) { bestVal = val; target = e; }
            }
        }

        if (target != null && rc.canAttack(target.getLocation())) {
            rc.attack(target.getLocation());
        }
    }

    private static int getTypePriority(UnitType type) {
        if (type == UnitType.SOLDIER) return 3;
        if (type == UnitType.MOPPER) return 2;
        if (type == UnitType.SPLASHER) return 1;
        return 0;
    }

    private static void processIncomingMessages() throws GameActionException {
        Message[] msgs = rc.readMessages(-1);
        int curRound = rc.getRoundNum();
        for (Message m : msgs) {
            int raw = m.getBytes();
            int type = decodeType(raw);
            int x = decodeX(raw);
            int y = decodeY(raw);
            switch (type) {
                case MSG_ENEMY_LOC:
                case MSG_ENEMY_TOWER:
                    lastEnemyLoc = new MapLocation(x, y);
                    lastEnemyRound = curRound;
                    relayToNearbyTowers(raw);
                    break;
                case MSG_RUIN_READY:
                    waitingForChips = true;
                    waitingStartRound = curRound;
                    break;
                case MSG_MOPPER_REQ:
                case MSG_RUIN_LOC:
                    break;
                default:
                    break;
            }
        }
    }

    private static void relayToNearbyTowers(int msg) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo a : allies) {
            if (isTowerType(a.getType()) && !a.getLocation().equals(rc.getLocation())) {
                if (rc.canSendMessage(a.getLocation(), msg)) {
                    rc.sendMessage(a.getLocation(), msg);
                    return;
                }
            }
        }
    }

    private static void broadcastMyLocation() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int msg = encodeMessage(MSG_TOWER_LOC, myLoc.x, myLoc.y, rc.getRoundNum() % EXTRA_MASK);
        RobotInfo[] nearby = rc.senseNearbyRobots(-1, rc.getTeam());
        int sent = 0;
        for (RobotInfo u : nearby) {
            if (!isTowerType(u.getType()) && rc.canSendMessage(u.getLocation(), msg)) {
                rc.sendMessage(u.getLocation(), msg);
                if (++sent >= 3) break;
            }
        }
    }

    // Spawn strategy: 3 soldier dulu, lalu cycle S→M→SP
    private static void spawnUnit(int round, int towers, int chips) throws GameActionException {
        UnitType toSpawn = decideUnitType(chips);
        if (toSpawn == null) return;

        MapLocation spawnLoc = chooseBestSpawnLoc(toSpawn);
        if (spawnLoc == null) return;

        if (rc.canBuildRobot(toSpawn, spawnLoc)) {
            rc.buildRobot(toSpawn, spawnLoc);
            spawnCount++;
            postSpawnCommunication(spawnLoc);
        }
    }

    private static UnitType decideUnitType(int chips) {
        int paint = rc.getPaint();
        boolean canSoldier = (chips >= 250 && paint >= 200);
        boolean canMopper = (chips >= 300 && paint >= 100);
        boolean canSplasher = (chips >= 400 && paint >= 300);

        // 3 soldier pertama per tower
        if (spawnCount < 3) {
            return canSoldier ? UnitType.SOLDIER : null;
        }

        // Setelah 3: cycle SOLDIER → MOPPER → SPLASHER
        int cycleIdx = (spawnCount - 3) % 3;
        switch (cycleIdx) {
            case 0: return canSoldier ? UnitType.SOLDIER : null;
            case 1: return canMopper ? UnitType.MOPPER : null;
            case 2: return canSplasher ? UnitType.SPLASHER : null;
            default: return canSoldier ? UnitType.SOLDIER : null;
        }
    }

    // Spawn location: paling dekat ke CENTER peta
    private static MapLocation chooseBestSpawnLoc(UnitType type) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;

        for (Direction dir : directions) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (!rc.canBuildRobot(type, spawnLoc)) continue;
            int dist = spawnLoc.distanceSquaredTo(center);
            if (dist < minDist) {
                minDist = dist;
                best = spawnLoc;
            }
        }
        return best;
    }

    // Post-spawn: kirim MSG_PAINT_TOWER ke unit baru
    private static void postSpawnCommunication(MapLocation unitLoc) throws GameActionException {
        // Cari paint tower terdekat
        MapLocation paintTower = null;
        int minDist = Integer.MAX_VALUE;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) {
                int dist = unitLoc.distanceSquaredTo(ally.getLocation());
                if (dist < minDist) { minDist = dist; paintTower = ally.getLocation(); }
            }
        }
        // Fallback ke diri sendiri jika kita paint tower
        if (paintTower == null && isPaintTower(rc.getType())) {
            paintTower = rc.getLocation();
        }

        if (paintTower != null && rc.canSendMessage(unitLoc,
                encodeMessage(MSG_PAINT_TOWER, paintTower.x, paintTower.y, 0))) {
            rc.sendMessage(unitLoc,
                encodeMessage(MSG_PAINT_TOWER, paintTower.x, paintTower.y, 0));
        }
    }
}
