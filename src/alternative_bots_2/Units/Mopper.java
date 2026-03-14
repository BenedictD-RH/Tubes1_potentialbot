package alternative_bots_2.Units;

import alternative_bots_2.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Mopper - Unit pendukung untuk membersihkan paint musuh dan transfer paint.
 *
 * State Machine:
 *  MOP_RUIN    - Bersihkan paint musuh di sekitar ruin (bantu tower building)
 *  COMBAT      - Serang robot musuh dengan mop
 *  TRANSFER    - Transfer paint ke robot/tower sekutu yang membutuhkan
 *  MOP_AREA    - Bersihkan paint musuh di area umum
 *  REFILL      - Refill paint dari tower
 *  EXPLORE     - Eksplorasi cari target
 *
 * Strategi Greedy:
 *  - Prioritaskan membersihkan paint musuh di sekitar ruin (membantu build tower)
 *  - Serang robot musuh yang kekurangan paint (lemah)
 *  - Transfer paint ke ally yang hampir kehabisan paint
 *  - Gunakan mop swing untuk efisiensi saat ada banyak musuh
 */
public class Mopper extends Unit {

    private static final int STATE_MOP_RUIN  = 0;
    private static final int STATE_COMBAT    = 1;
    private static final int STATE_TRANSFER  = 2;
    private static final int STATE_MOP_AREA  = 3;
    private static final int STATE_REFILL    = 4;
    private static final int STATE_EXPLORE   = 5;

    private static int state = STATE_EXPLORE;
    private static MapLocation mopTarget = null;   // Tile musuh yang akan di-mop
    private static MapLocation combatTarget = null; // Robot musuh target

    public static void run() throws GameActionException {
        initUnit();
        scanEnvironment();
        processMessages();

        // Update state berdasarkan kondisi terkini
        updateState();

        // Eksekusi aksi
        executeState();
    }

    // =========================================================================
    //  STATE TRANSITION
    // =========================================================================

    private static void updateState() throws GameActionException {
        int paintPercent = (rc.getPaint() * 100) / rc.getType().paintCapacity;

        // PRIORITAS 1: Refill jika paint kritis
        if (paintPercent <= LOW_PAINT_PERCENT) {
            state = STATE_REFILL;
            return;
        }
        if (state == STATE_REFILL && paintPercent > 80) {
            state = STATE_EXPLORE;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, rc.getTeam());

        // PRIORITAS 2: Transfer paint ke ally yang sekarat
        RobotInfo dyingAlly = findDyingAlly(allies);
        if (dyingAlly != null && paintPercent > 40) {
            state = STATE_TRANSFER;
            return;
        }

        // PRIORITAS 3: Mop paint musuh di sekitar ruin (bantu tower building)
        MapLocation ruinWithEnemyPaint = findRuinWithEnemyPaint();
        if (ruinWithEnemyPaint != null) {
            mopTarget = ruinWithEnemyPaint;
            state = STATE_MOP_RUIN;
            // Broadcast request soldier untuk bantu painting
            broadcastRuin(ruinWithEnemyPaint);
            return;
        }

        // PRIORITAS 4: Serang robot musuh
        if (enemies.length > 0) {
            combatTarget = selectCombatTarget(enemies);
            if (combatTarget != null) {
                state = STATE_COMBAT;
                return;
            }
        }

        // PRIORITAS 5: Mop paint musuh di area umum
        MapLocation enemyPaint = findNearestEnemyPaint();
        if (enemyPaint != null) {
            mopTarget = enemyPaint;
            state = STATE_MOP_AREA;
            return;
        }

        // Default: explore
        if (state != STATE_TRANSFER && state != STATE_MOP_RUIN) {
            state = STATE_EXPLORE;
        }
    }

    // =========================================================================
    //  STATE EXECUTION
    // =========================================================================

    private static void executeState() throws GameActionException {
        switch (state) {
            case STATE_MOP_RUIN:
                executeMopRuin();
                break;
            case STATE_COMBAT:
                executeCombat();
                break;
            case STATE_TRANSFER:
                executeTransfer();
                break;
            case STATE_MOP_AREA:
                executeMopArea();
                break;
            case STATE_REFILL:
                refillPaint();
                break;
            case STATE_EXPLORE:
            default:
                executeExplore();
                break;
        }
    }

    // =========================================================================
    //  MOP RUIN - Bersihkan paint musuh di sekitar ruin
    // =========================================================================

    private static void executeMopRuin() throws GameActionException {
        if (mopTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(mopTarget);

        // Sudah di dekat target - mop!
        if (dist <= 2) {
            if (rc.isActionReady() && rc.canAttack(mopTarget)) {
                rc.attack(mopTarget);

                // Cek lagi apakah masih ada paint musuh di sekitar ruin
                mopTarget = findRuinWithEnemyPaint();
                if (mopTarget == null) {
                    state = STATE_EXPLORE;
                }
            }
        } else {
            // Bergerak ke target
            if (rc.isMovementReady()) {
                moveGreedy(mopTarget);
            }
        }
    }

    // =========================================================================
    //  COMBAT - Serang robot musuh
    // =========================================================================

    private static void executeCombat() throws GameActionException {
        if (combatTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(combatTarget);

        // Cek apakah masih ada musuh di sana
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        boolean targetStillExists = false;
        for (RobotInfo e : enemies) {
            if (e.getLocation().equals(combatTarget)) {
                targetStillExists = true;
                break;
            }
        }

        if (!targetStillExists) {
            // Cari target baru
            if (enemies.length > 0) {
                combatTarget = selectCombatTarget(enemies);
            } else {
                combatTarget = null;
                state = STATE_EXPLORE;
                return;
            }
        }

        // Coba mop swing jika ada banyak musuh (lebih efisien)
        if (rc.isActionReady() && enemies.length >= 2) {
            Direction swingDir = findBestSwingDirection(enemies);
            if (swingDir != null && rc.canMopSwing(swingDir)) {
                rc.mopSwing(swingDir);
                return;
            }
        }

        // Normal mop attack
        if (dist <= 2) {
            if (rc.isActionReady() && rc.canAttack(combatTarget)) {
                rc.attack(combatTarget);
                return;
            }
        }

        // Bergerak ke target
        if (rc.isMovementReady()) {
            moveGreedy(combatTarget);
        }
    }

    // =========================================================================
    //  TRANSFER - Transfer paint ke ally yang membutuhkan
    // =========================================================================

    private static void executeTransfer() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo dyingAlly = findDyingAlly(allies);

        if (dyingAlly == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation allyLoc = dyingAlly.getLocation();
        int dist = rc.getLocation().distanceSquaredTo(allyLoc);

        if (dist <= 2) {
            // Transfer paint
            if (rc.isActionReady()) {
                int allyNeed = dyingAlly.getType().paintCapacity / 2 - dyingAlly.getPaintAmount();
                int canGive  = rc.getPaint() - rc.getType().paintCapacity / 4; // Simpan 25%
                int amount   = Math.min(allyNeed, canGive);

                if (amount > 0 && rc.canTransferPaint(allyLoc, amount)) {
                    rc.transferPaint(allyLoc, amount);
                }
            }
            state = STATE_EXPLORE;
        } else {
            if (rc.isMovementReady()) {
                moveGreedy(allyLoc);
            }
        }
    }

    // =========================================================================
    //  MOP AREA - Bersihkan paint musuh di area umum
    // =========================================================================

    private static void executeMopArea() throws GameActionException {
        if (mopTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(mopTarget);

        if (dist <= 2) {
            if (rc.isActionReady() && rc.canAttack(mopTarget)) {
                rc.attack(mopTarget);
                // Cari target baru
                mopTarget = findNearestEnemyPaint();
                if (mopTarget == null) state = STATE_EXPLORE;
            }
        } else {
            if (rc.isMovementReady()) {
                moveGreedy(mopTarget);
            }
        }
    }

    // =========================================================================
    //  EXPLORE - Eksplorasi, cari target
    // =========================================================================

    private static void executeExplore() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            broadcastEnemyLocation(enemies[0].getLocation());
        }

        if (rc.isMovementReady()) {
            // Ikuti broadcast request mopper
            MapLocation mopReq = readBroadcastTarget(MSG_MOPPER_REQ);
            if (mopReq != null) {
                mopTarget = mopReq;
                state = STATE_MOP_AREA;
                moveGreedy(mopReq);
                return;
            }

            // Ikuti enemy location dari broadcast
            MapLocation enemyLoc = readBroadcastTarget(MSG_ENEMY_LOC);
            if (enemyLoc != null) {
                moveGreedy(enemyLoc);
                return;
            }

            explore();
        }
    }

    // =========================================================================
    //  HELPER METHODS
    // =========================================================================

    /**
     * Cari ally yang hampir kehabisan paint (butuh transfer).
     * Greedy: prioritaskan ally dengan paint paling sedikit.
     */
    private static RobotInfo findDyingAlly(RobotInfo[] allies) {
        RobotInfo worst = null;
        int minPaint = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            // Hanya robot (bukan tower)
            if (isTowerType(ally.getType())) continue;

            int paintPercent = (ally.getPaintAmount() * 100);
            if (ally.getType().paintCapacity > 0) {
                paintPercent /= ally.getType().paintCapacity;
            }

            // Ally dengan paint < 20% perlu bantuan
            if (paintPercent < 20 && ally.getPaintAmount() < minPaint) {
                minPaint = ally.getPaintAmount();
                worst = ally;
            }
        }
        return worst;
    }

    /**
     * Cari ruin dengan paint musuh di sekitarnya.
     * Greedy: prioritaskan ruin yang paling dekat.
     */
    private static MapLocation findRuinWithEnemyPaint() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();

            // Cek tile di sekitar ruin (5x5)
            MapInfo[] ruinArea = rc.senseNearbyMapInfos(ruinLoc, 8);
            for (MapInfo ruinTile : ruinArea) {
                if (!ruinTile.getPaint().isAlly() &&
                    ruinTile.getPaint() != PaintType.EMPTY &&
                    ruinTile.isPassable()) {
                    // Ada paint musuh di sekitar ruin ini
                    int dist = myLoc.distanceSquaredTo(ruinTile.getMapLocation());
                    if (dist < minDist) {
                        minDist = dist;
                        best = ruinTile.getMapLocation();
                    }
                    break;
                }
            }
        }
        return best;
    }

    /**
     * Cari tile dengan paint musuh terdekat.
     * Greedy: ambil yang paling dekat.
     */
    private static MapLocation findNearestEnemyPaint() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (!paint.isAlly() && paint != PaintType.EMPTY) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < minDist) {
                    minDist = dist;
                    best = tile.getMapLocation();
                }
            }
        }
        return best;
    }

    /**
     * Pilih target combat terbaik.
     * Greedy: musuh dengan paint paling sedikit (paling lemah).
     */
    private static MapLocation selectCombatTarget(RobotInfo[] enemies) {
        RobotInfo best = null;
        int minPaint = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (isTowerType(enemy.getType())) continue; // Skip tower musuh
            if (enemy.getPaintAmount() < minPaint) {
                minPaint = enemy.getPaintAmount();
                best = enemy;
            }
        }

        // Fallback ke enemy pertama
        if (best == null && enemies.length > 0) {
            best = enemies[0];
        }
        return best != null ? best.getLocation() : null;
    }

    /**
     * Cari arah swing terbaik untuk mop banyak musuh sekaligus.
     * Greedy: pilih arah yang mengenai paling banyak musuh.
     */
    private static Direction findBestSwingDirection(RobotInfo[] enemies) throws GameActionException {
        Direction bestDir = null;
        int maxHits = 0;

        for (Direction dir : cardinalDirs) {
            if (!rc.canMopSwing(dir)) continue;

            // Hitung musuh yang akan terkena swing di arah ini
            // Swing mengenai 3 tile di step 1 dan 3 tile di step 2
            MapLocation myLoc = rc.getLocation();
            int hits = 0;

            MapLocation step1 = myLoc.add(dir);
            MapLocation step2 = step1.add(dir);

            for (RobotInfo enemy : enemies) {
                MapLocation eLoc = enemy.getLocation();
                // Cek apakah musuh ada di area swing
                if (eLoc.distanceSquaredTo(step1) <= 1 ||
                    eLoc.distanceSquaredTo(step2) <= 1) {
                    hits++;
                }
            }

            if (hits > maxHits) {
                maxHits = hits;
                bestDir = dir;
            }
        }

        // Hanya swing jika mengenai >= 2 musuh
        return (maxHits >= 2) ? bestDir : null;
    }
}
