package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Tower - Logika untuk semua tipe menara.
 *
 * Prioritas per giliran:
 *  1. Serang musuh dalam jangkauan (semua tower bisa serang)
 *  2. Proses & relay pesan dari unit
 *  3. Upgrade jika ekonomi bagus
 *  4. Spawn unit strategis
 *  5. Broadcast lokasi tower ke unit baru
 *
 * Strategi spawn (greedy berdasarkan kondisi):
 *  Early game (round < 150):
 *    - Spawn soldier agresif (target: 5-6 soldier untuk rush tower)
 *    - 1 mopper setiap 3 soldier untuk bantu bersihkan area
 *  Mid game (round 150-600):
 *    - Rasio 40% soldier, 30% mopper, 30% splasher
 *    - Prioritaskan splasher jika sudah banyak tower
 *  Late game (round > 600):
 *    - Lebih banyak splasher untuk area denial
 *
 * Resource management:
 *  - Spawn soldier jika paint >= 200 dan chips >= 250
 *  - Spawn splasher hanya jika paint >= 300 dan chips >= 400
 *  - Simpan minimal 500 chips untuk darurat
 */
public class Tower extends BaseRobot {

    // Tracking per-tower (tidak bisa global karena setiap robot punya salinan sendiri)
    private static int turnCount       = 0;
    private static int soldierSpawned  = 0;
    private static int mopperSpawned   = 0;
    private static int splasherSpawned = 0;

    // Info dari pesan masuk
    private static MapLocation lastEnemyLoc   = null;
    private static int         lastEnemyRound = -1;
    private static MapLocation pendingRuin    = null; // ruin yang butuh builder

    public static void run() throws GameActionException {
        turnCount++;

        int round  = rc.getRoundNum();
        int chips  = rc.getMoney();
        int paint  = rc.getPaint();
        int towers = rc.getNumberTowers();

        // === 1. Serang musuh ===
        attackEnemies();

        // === 2. Proses pesan masuk ===
        processIncomingMessages();

        // === 3. Upgrade jika layak ===
        if (rc.isActionReady() && shouldUpgrade(chips, towers)) {
            if (rc.canUpgradeTower(rc.getLocation())) {
                rc.upgradeTower(rc.getLocation());
                return;
            }
        }

        // === 4. Spawn unit ===
        if (rc.isActionReady()) {
            spawnUnit(round, towers, chips, paint);
        }

        // === 5. Broadcast lokasi tower ke unit sekitar ===
        if (round <= 10 || round % 15 == 0) {
            broadcastMyLocation();
        }
    }

    // =========================================================================
    //  ATTACK
    // =========================================================================

    private static void attackEnemies() throws GameActionException {
        if (!rc.isActionReady()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        // Greedy: pilih musuh dengan HP terendah (eliminasi cepat)
        RobotInfo target = null;
        int minHP = Integer.MAX_VALUE;

        // Prioritas robot dulu, bukan tower musuh
        for (RobotInfo e : enemies) {
            if (!isTowerType(e.getType()) && e.health < minHP) {
                minHP = e.health;
                target = e;
            }
        }
        // Jika tidak ada robot, serang tower
        if (target == null) {
            for (RobotInfo e : enemies) {
                if (e.health < minHP) { minHP = e.health; target = e; }
            }
        }

        if (target != null && rc.canAttack(target.getLocation())) {
            rc.attack(target.getLocation());
        }
    }

    // =========================================================================
    //  KOMUNIKASI
    // =========================================================================

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
                    relayToNearbyTowers(raw);
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

    /** Relay pesan ke tower tetangga */
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

    /** Broadcast lokasi tower ini ke unit di dekat */
    private static void broadcastMyLocation() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int msgType = MSG_TOWER_LOC;
        int msg = encodeMessage(msgType, myLoc.x, myLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] nearby = rc.senseNearbyRobots(-1, rc.getTeam());
        int sent = 0;
        for (RobotInfo u : nearby) {
            if (!isTowerType(u.getType()) && rc.canSendMessage(u.getLocation(), msg)) {
                rc.sendMessage(u.getLocation(), msg);
                if (++sent >= 3) break;
            }
        }
    }

    // =========================================================================
    //  SPAWNING - Greedy: spawn unit yang paling dibutuhkan
    // =========================================================================

    private static void spawnUnit(int round, int towers, int chips, int paint)
            throws GameActionException {

        UnitType toSpawn = decideUnitType(round, towers, chips, paint);
        if (toSpawn == null) return;

        // Pilih lokasi spawn terbaik (greedy: pilih yang punya ally paint)
        MapLocation spawnLoc = chooseBestSpawnLoc(toSpawn);
        if (spawnLoc == null) return;

        if (rc.canBuildRobot(toSpawn, spawnLoc)) {
            rc.buildRobot(toSpawn, spawnLoc);
            countSpawn(toSpawn);
        }
    }

    /**
     * Greedy decision tree untuk memilih tipe unit.
     *
     * Early game (round < 150):
     *   - Prioritas: soldier sampai 5 unit, lalu 1 mopper per 3 soldier
     *   - Tujuan: rush semua ruin untuk bangun tower secepatnya
     *
     * Mid game (150-600):
     *   - Seimbangkan soldier (40%), mopper (25%), splasher (35%)
     *   - Splasher lebih banyak karena ekonomi sudah stabil
     *
     * Late game (>600):
     *   - Dominasi splasher (50%) untuk area control
     *   - Tetap jaga soldier (30%) untuk tower building sisa ruin
     */
    private static UnitType decideUnitType(int round, int towers, int chips, int paint) {

        // === EARLY GAME: Agresif spawn soldier untuk rush tower ===
        if (round < EARLY_GAME_END) {
            // Minimal chips+paint untuk soldier: 250 chips, 200 paint
            if (chips < 250 || paint < 200) return null;

            int totalUnit = soldierSpawned + mopperSpawned + splasherSpawned;

            // Prioritas utama: spawn soldier sampai 5 unit
            if (soldierSpawned < 5) {
                return UnitType.SOLDIER;
            }

            // 1 mopper setiap 3 soldier (untuk bantu bersihkan cat musuh di ruin)
            if (mopperSpawned < soldierSpawned / 3 && paint >= 100 && chips >= 300) {
                return UnitType.MOPPER;
            }

            // Lanjut spawn soldier
            if (chips >= 250 && paint >= 200) return UnitType.SOLDIER;
            return null;
        }

        // === MID GAME ===
        if (round < MID_GAME_END) {
            int total = soldierSpawned + mopperSpawned + splasherSpawned;
            if (total == 0) {
                return (chips >= 250 && paint >= 200) ? UnitType.SOLDIER : null;
            }

            double soldierR  = (double) soldierSpawned  / total;
            double mopperR   = (double) mopperSpawned   / total;
            double splasherR = (double) splasherSpawned / total;

            // Jika ada pending ruin request: kirim mopper
            if (pendingRuin != null && mopperR < 0.3 && paint >= 100 && chips >= 300) {
                pendingRuin = null;
                return UnitType.MOPPER;
            }

            // Target rasio: Soldier 40%, Splasher 35%, Mopper 25%
            if (soldierR < 0.40 && chips >= 250 && paint >= 200) return UnitType.SOLDIER;
            if (splasherR < 0.35 && chips >= 400 && paint >= 300) return UnitType.SPLASHER;
            if (mopperR  < 0.25 && chips >= 300 && paint >= 100) return UnitType.MOPPER;

            // Fallback: spawn apapun yang bisa diafford
            if (chips >= 250 && paint >= 200) return UnitType.SOLDIER;
            if (chips >= 300 && paint >= 100) return UnitType.MOPPER;
            return null;
        }

        // === LATE GAME ===
        int total = soldierSpawned + mopperSpawned + splasherSpawned;
        if (total == 0) {
            return (chips >= 250 && paint >= 200) ? UnitType.SOLDIER : null;
        }

        double splasherR = (double) splasherSpawned / total;
        double soldierR  = (double) soldierSpawned  / total;

        // Late game: dominasi splasher
        if (splasherR < 0.50 && chips >= 400 && paint >= 300) return UnitType.SPLASHER;
        if (soldierR  < 0.30 && chips >= 250 && paint >= 200) return UnitType.SOLDIER;
        if (chips >= 300 && paint >= 100) return UnitType.MOPPER;
        return null;
    }

    /**
     * Pilih lokasi spawn terbaik.
     * Greedy: prioritaskan tile dengan ally paint (unit tidak langsung rugi paint),
     * dan arahkan ke lokasi musuh jika diketahui.
     */
    private static MapLocation chooseBestSpawnLoc(UnitType type) throws GameActionException {
        MapLocation myLoc  = rc.getLocation();
        MapLocation best   = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (!rc.canBuildRobot(type, spawnLoc)) continue;

            int score = 0;

            if (rc.canSenseLocation(spawnLoc)) {
                // Bonus besar jika tile punya paint sekutu (kurangi cooldown awal)
                if (rc.senseMapInfo(spawnLoc).getPaint().isAlly()) {
                    score += 20;
                }
            }

            // Arahkan ke musuh jika diketahui (dalam 50 ronde terakhir)
            if (lastEnemyLoc != null && rc.getRoundNum() - lastEnemyRound < 50) {
                // Semakin dekat ke arah musuh, semakin baik
                score -= spawnLoc.distanceSquaredTo(lastEnemyLoc) / 10;
            }

            // Jika ada ruin yang pending, arahkan ke sana
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

    // =========================================================================
    //  UPGRADE
    // =========================================================================

    private static boolean shouldUpgrade(int chips, int towers) {
        int round = rc.getRoundNum();
        // Hanya upgrade jika chips berlimpah (jangan ganggu spawning)
        if (isPaintTower(rc.getType())) return chips >= 2500 && round > 100;
        if (isMoneyTower(rc.getType()))  return chips >= 3500 && round > 200;
        if (isDefenseTower(rc.getType())) return chips >= 2500;
        return false;
    }

    // =========================================================================
    //  UTILITY
    // =========================================================================

    private static void countSpawn(UnitType type) {
        if (type == UnitType.SOLDIER)  soldierSpawned++;
        else if (type == UnitType.MOPPER)   mopperSpawned++;
        else if (type == UnitType.SPLASHER) splasherSpawned++;
    }
}
