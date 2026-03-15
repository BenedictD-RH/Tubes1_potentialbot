package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Tower extends BaseRobot {

    private static int turnCount       = 0;
    private static int soldierSpawned  = 0;
    private static int mopperSpawned   = 0;
    private static int splasherSpawned = 0;

    private static MapLocation lastEnemyLoc   = null;
    private static int         lastEnemyRound = -1;
    private static MapLocation pendingRuin    = null;

    // Fungsi menjalankan logic tower setiap giliran
    public static void run() throws GameActionException {
        turnCount++;

        int round  = rc.getRoundNum();
        int chips  = rc.getMoney();
        int paint  = rc.getPaint();
        int towers = rc.getNumberTowers();

        attackEnemies();

        processIncomingMessages();

        // Upgrade tower jika kondisi memungkinkan
        if (rc.isActionReady() && shouldUpgrade(chips, towers)) {
            if (rc.canUpgradeTower(rc.getLocation())) {
                rc.upgradeTower(rc.getLocation());
                return;
            }
        }

        if (rc.isActionReady()) {
            spawnUnit(round, towers, chips, paint);
        }

        // Broadcast lokasi tower secara berkala
        if (round <= 10 || round % 15 == 0) {
            broadcastMyLocation();
        }
    }

    // Fungsi menyerang musuh terdekat dengan prioritas unit non-tower
    private static void attackEnemies() throws GameActionException {
        if (!rc.isActionReady()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        RobotInfo target = null;
        int minHP = Integer.MAX_VALUE;

        // Prioritas utama: musuh bukan tower dengan HP paling rendah
        for (RobotInfo e : enemies) {
            if (!isTowerType(e.getType()) && e.health < minHP) {
                minHP = e.health;
                target = e;
            }
        }

        // Fallback ke tower musuh jika tidak ada unit lain
        if (target == null) {
            for (RobotInfo e : enemies) {
                if (e.health < minHP) { minHP = e.health; target = e; }
            }
        }

        if (target != null && rc.canAttack(target.getLocation())) {
            rc.attack(target.getLocation());
        }
    }

    // Fungsi memproses pesan masuk dari unit ally
    private static void processIncomingMessages() throws GameActionException {
        Message[] msgs = rc.readMessages(-1);
        int curRound   = rc.getRoundNum();

        for (Message m : msgs) {
            int raw  = m.getBytes();
            int type = decodeType(raw);
            int x    = decodeX(raw);
            int y    = decodeY(raw);

            switch (type) {
                case MSG_ENEMY_LOC:
                case MSG_ENEMY_TOWER:
                    lastEnemyLoc   = new MapLocation(x, y);
                    lastEnemyRound = curRound;
                    relayToNearbyTowers(raw); // teruskan ke tower lain
                    break;
                case MSG_MOPPER_REQ:
                case MSG_RUIN_LOC:
                    pendingRuin = new MapLocation(x, y);
                    break;
                default:
                    break;
            }
        }
    }

    // Fungsi meneruskan pesan ke tower ally terdekat
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

    // Fungsi broadcast lokasi tower ini ke unit ally terdekat
    private static void broadcastMyLocation() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int msg = encodeMessage(MSG_TOWER_LOC, myLoc.x, myLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] nearby = rc.senseNearbyRobots(-1, rc.getTeam());
        int sent = 0;
        for (RobotInfo u : nearby) {
            if (!isTowerType(u.getType()) && rc.canSendMessage(u.getLocation(), msg)) {
                rc.sendMessage(u.getLocation(), msg);
                if (++sent >= 3) break; // cukup 3 unit
            }
        }
    }

    // Fungsi spawn unit baru berdasarkan kondisi resources
    private static void spawnUnit(int round, int towers, int chips, int paint)
            throws GameActionException {

        UnitType toSpawn = decideUnitType(round, towers, chips, paint);
        if (toSpawn == null) return;

        MapLocation spawnLoc = chooseBestSpawnLoc(toSpawn);
        if (spawnLoc == null) return;

        if (rc.canBuildRobot(toSpawn, spawnLoc)) {
            rc.buildRobot(toSpawn, spawnLoc);
            countSpawn(toSpawn);
        }
    }

    // Fungsi memilih tipe unit berdasarkan chips, paint, dan fase permainan
    // Early game: rush 5 soldier untuk build tower secepat mungkin
    // Mid/late: adaptif berdasarkan sumber daya yang tersedia
    private static UnitType decideUnitType(int round, int towers, int chips, int paint) {

        boolean canSplasher = (chips >= 400 && paint >= 300);
        boolean canSoldier  = (chips >= 250 && paint >= 200);
        boolean canMopper   = (chips >= 300 && paint >= 100);

        // Early game: rush soldier dulu
        if (round < EARLY_GAME_END) {
            if (!canSoldier && !canMopper) return null;
            if (soldierSpawned < 5 && canSoldier)  return UnitType.SOLDIER;
            if (mopperSpawned < soldierSpawned / 4 && canMopper) return UnitType.MOPPER;
            if (canSoldier) return UnitType.SOLDIER;
            return null;
        }

        int total = soldierSpawned + mopperSpawned + splasherSpawned;
        if (total == 0) return canSoldier ? UnitType.SOLDIER : null;

        double soldierR  = (double) soldierSpawned  / total;
        double mopperR   = (double) mopperSpawned   / total;
        double splasherR = (double) splasherSpawned / total;

        // Prioritas khusus: ada ruin yang butuh mopper untuk bersihkan paint musuh
        if (pendingRuin != null && canMopper) {
            pendingRuin = null;
            return UnitType.MOPPER;
        }

        // Chips kaya + paint kaya: dominasi splasher untuk area control
        if (chips > 1200 && paint > 300) {
            if (splasherR < 0.45 && canSplasher) return UnitType.SPLASHER;
            if (soldierR  < 0.35 && canSoldier)  return UnitType.SOLDIER;
            if (mopperR   < 0.20 && canMopper)   return UnitType.MOPPER;
        }

        // Chips kaya + paint rendah: mopper hemat paint atau soldier
        if (chips > 1000 && paint <= 300) {
            if (mopperR  < 0.35 && canMopper)  return UnitType.MOPPER;
            if (soldierR < 0.50 && canSoldier) return UnitType.SOLDIER;
        }

        // Paint kaya + chips rendah: soldier untuk cat tile
        if (chips <= 1000 && paint > 250) {
            if (soldierR  < 0.55 && canSoldier)  return UnitType.SOLDIER;
            if (splasherR < 0.25 && canSplasher) return UnitType.SPLASHER;
        }

        // Default: rasio ideal berdasarkan unit yang tersedia
        if (soldierR  < 0.40 && canSoldier)  return UnitType.SOLDIER;
        if (splasherR < 0.35 && canSplasher) return UnitType.SPLASHER;
        if (mopperR   < 0.25 && canMopper)   return UnitType.MOPPER;

        // Fallback ke yang paling terjangkau
        if (canSoldier) return UnitType.SOLDIER;
        if (canMopper)  return UnitType.MOPPER;
        return null;
    }

    // Fungsi memilih lokasi spawn terbaik berdasarkan kondisi peta
    private static MapLocation chooseBestSpawnLoc(UnitType type) throws GameActionException {
        MapLocation myLoc  = rc.getLocation();
        MapLocation best   = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (!rc.canBuildRobot(type, spawnLoc)) continue;

            int score = 0;

            if (rc.canSenseLocation(spawnLoc)) {
                // Spawn di tile ally lebih aman
                if (rc.senseMapInfo(spawnLoc).getPaint().isAlly()) {
                    score += 20;
                }
            }

            // Dekatkan spawn ke arah musuh diketahui
            if (lastEnemyLoc != null && rc.getRoundNum() - lastEnemyRound < 50) {
                score -= spawnLoc.distanceSquaredTo(lastEnemyLoc) / 10;
            }

            // Dekatkan spawn ke ruin yang perlu dibantu
            if (pendingRuin != null) {
                score -= spawnLoc.distanceSquaredTo(pendingRuin) / 10;
            }

            if (score > bestScore) {
                bestScore = score;
                best = spawnLoc;
            }
        }
        return best;
    }

    // Fungsi cek apakah tower perlu di-upgrade
    private static boolean shouldUpgrade(int chips, int towers) {
        int round = rc.getRoundNum();
        if (isPaintTower(rc.getType()))   return chips >= 2500 && round > 100;
        if (isMoneyTower(rc.getType()))   return chips >= 3500 && round > 200;
        if (isDefenseTower(rc.getType())) return chips >= 2500;
        return false;
    }

    // Fungsi mencatat jumlah unit yang telah di-spawn
    private static void countSpawn(UnitType type) {
        if (type == UnitType.SOLDIER)       soldierSpawned++;
        else if (type == UnitType.MOPPER)   mopperSpawned++;
        else if (type == UnitType.SPLASHER) splasherSpawned++;
    }
}
