package alternative_bots_2.Units;

import alternative_bots_2.Unit;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Splasher - Unit area control untuk painting massal dan menghancurkan tower musuh.
 *
 * State Machine:
 *  ATTACK_TOWER - Splash tower musuh (100 damage, sangat efektif)
 *  AREA_PAINT   - Paint area kosong massal
 *  OVERWRITE    - Timpa paint musuh dengan area splash
 *  REFILL       - Refill paint
 *  EXPLORE      - Eksplorasi cari target
 *
 * Strategi Greedy:
 *  - Prioritaskan splash yang mengenai tile terbanyak (maximize area per aksi)
 *  - Semakin banyak tile musuh dalam radius, semakin tinggi nilai splash
 *  - Tower musuh diberi bobot 5x lebih tinggi dari tile biasa
 *  - Minimum threshold: splash hanya jika mengenai >= 3 tile bermanfaat
 *
 * Greedy Heuristic untuk splash targeting:
 *  score = (kosong * 1) + (enemy_paint * 3) + (enemy_tower * 10)
 *  Pilih lokasi dengan score tertinggi dalam radius aksi.
 */
public class Splasher extends Unit {

    private static final int STATE_ATTACK_TOWER = 0;
    private static final int STATE_AREA_PAINT   = 1;
    private static final int STATE_OVERWRITE    = 2;
    private static final int STATE_REFILL       = 3;
    private static final int STATE_EXPLORE      = 4;

    private static int state = STATE_EXPLORE;
    private static MapLocation splashTarget = null;  // Target splash terbaik
    private static MapLocation enemyTowerTarget = null;

    // Threshold minimum score untuk splash (jika terlalu rendah, lebih baik gerak dulu)
    private static final int MIN_SPLASH_SCORE = 3;

    public static void run() throws GameActionException {
        initUnit();
        scanEnvironment();
        processMessages();

        // Update state
        updateState();

        // Eksekusi
        executeState();
    }

    // =========================================================================
    //  STATE TRANSITION
    // =========================================================================

    private static void updateState() throws GameActionException {
        int paintPercent = (rc.getPaint() * 100) / rc.getType().paintCapacity;

        // PRIORITAS 1: Refill jika paint rendah (splasher pakai 50 paint per attack)
        if (paintPercent <= 25) {
            state = STATE_REFILL;
            return;
        }
        if (state == STATE_REFILL && paintPercent > 75) {
            state = STATE_EXPLORE;
        }

        // PRIORITAS 2: Serang tower musuh jika ada (damage 100 per splash)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo enemyTower = findEnemyTower(enemies);
        if (enemyTower != null) {
            enemyTowerTarget = enemyTower.getLocation();
            state = STATE_ATTACK_TOWER;
            return;
        }

        // PRIORITAS 3: Splash area musuh (overwrite)
        MapLocation bestOverwrite = findBestSplashTarget(true);
        if (bestOverwrite != null) {
            splashTarget = bestOverwrite;
            state = STATE_OVERWRITE;
            return;
        }

        // PRIORITAS 4: Splash area kosong
        MapLocation bestPaint = findBestSplashTarget(false);
        if (bestPaint != null) {
            splashTarget = bestPaint;
            state = STATE_AREA_PAINT;
            return;
        }

        // Default: explore
        state = STATE_EXPLORE;
    }

    // =========================================================================
    //  STATE EXECUTION
    // =========================================================================

    private static void executeState() throws GameActionException {
        switch (state) {
            case STATE_ATTACK_TOWER:
                executeAttackTower();
                break;
            case STATE_OVERWRITE:
            case STATE_AREA_PAINT:
                executeAreaSplash();
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
    //  ATTACK TOWER - Splash tower musuh
    // =========================================================================

    private static void executeAttackTower() throws GameActionException {
        if (enemyTowerTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        // Verifikasi tower masih ada
        if (rc.canSenseLocation(enemyTowerTarget)) {
            if (!rc.canSenseRobotAtLocation(enemyTowerTarget)) {
                enemyTowerTarget = null;
                state = STATE_EXPLORE;
                return;
            }
        }

        MapLocation myLoc = rc.getLocation();

        // Splasher attack: titik pusat max 2 petak dari posisi
        // Ingin splash langsung di atas tower jika bisa
        if (rc.isActionReady() && rc.canAttack(enemyTowerTarget)) {
            rc.attack(enemyTowerTarget);
            broadcastEnemyTower(enemyTowerTarget);
            return;
        }

        // Bergerak mendekat (tapi jaga jarak aman - hindari attack range tower)
        if (rc.isMovementReady()) {
            // Splash radius = 2, splasher harus dalam range (titik pusat maks 2 petak)
            // Tower attack range = 3-4, jadi approach dari sudut
            int dist = myLoc.distanceSquaredTo(enemyTowerTarget);
            if (dist > 4) {
                moveGreedy(enemyTowerTarget);
            } else if (dist <= 2) {
                // Terlalu dekat - mundur sedikit ke jarak optimal
                retreatFrom(enemyTowerTarget);
            }
        }
    }

    // =========================================================================
    //  AREA SPLASH - Splash area kosong atau paint musuh
    // =========================================================================

    private static void executeAreaSplash() throws GameActionException {
        if (splashTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();

        // Cek apakah dalam range untuk splash
        if (rc.isActionReady() && rc.canAttack(splashTarget)) {
            rc.attack(splashTarget);
            // Cari target baru setelah splash
            splashTarget = findBestSplashTarget(state == STATE_OVERWRITE);
            if (splashTarget == null) state = STATE_EXPLORE;
            return;
        }

        // Bergerak ke posisi yang bisa menyerang target
        if (rc.isMovementReady()) {
            int dist = myLoc.distanceSquaredTo(splashTarget);
            if (dist <= 4) {
                // Sudah cukup dekat tapi belum bisa attack - gerak optimial
                moveTowardForSplash(splashTarget);
            } else {
                moveGreedy(splashTarget);
            }
        }
    }

    // =========================================================================
    //  EXPLORE
    // =========================================================================

    private static void executeExplore() throws GameActionException {
        // Broadcast musuh jika ada
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            broadcastEnemyLocation(enemies[0].getLocation());
        }

        if (rc.isMovementReady()) {
            // Ikuti enemy tower dari broadcast
            MapLocation towerLoc = readBroadcastTarget(MSG_ENEMY_TOWER);
            if (towerLoc != null) {
                enemyTowerTarget = towerLoc;
                state = STATE_ATTACK_TOWER;
                moveGreedy(towerLoc);
                return;
            }

            // Cari area paint musuh dari broadcast
            MapLocation enemyLoc = readBroadcastTarget(MSG_ENEMY_LOC);
            if (enemyLoc != null) {
                splashTarget = enemyLoc;
                state = STATE_OVERWRITE;
                moveGreedy(enemyLoc);
                return;
            }

            // Bergerak ke area yang belum di-paint
            MapLocation unpaintedArea = findUnpaintedArea();
            if (unpaintedArea != null) {
                moveGreedy(unpaintedArea);
            } else {
                explore();
            }
        }
    }

    // =========================================================================
    //  SPLASH TARGET SELECTION - Core greedy algorithm
    // =========================================================================

    /**
     * Cari lokasi splash dengan score tertinggi.
     *
     * Greedy Heuristic:
     *   score(loc) = Σ tile_value(t) untuk setiap t dalam radius 2 dari loc
     *   tile_value:
     *     - Enemy tower  : +10 (highest value - 100 damage)
     *     - Enemy paint  : +3  (overwrite territory)
     *     - Empty tile   : +1  (gain territory)
     *     - Ally paint   : 0   (no benefit)
     *
     * Ini adalah greedy karena kita memilih lokasi dengan nilai
     * lokal tertinggi tanpa mempertimbangkan urutan splash ke depan.
     *
     * @param preferEnemy true jika prioritaskan overwrite musuh
     */
    private static MapLocation findBestSplashTarget(boolean preferEnemy) throws GameActionException {
        // Batasi bytecode - hanya scan area yang relevan
        if (Clock.getBytecodesLeft() < 2000) return null;

        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        MapLocation bestLoc = null;
        int bestScore = MIN_SPLASH_SCORE - 1; // Harus exceed threshold

        for (MapInfo tile : nearbyTiles) {
            if (!tile.isPassable()) continue;
            MapLocation loc = tile.getMapLocation();

            // Hanya evaluasi lokasi yang dalam jangkauan attack splasher (radius 4)
            if (myLoc.distanceSquaredTo(loc) > 4) continue;
            if (!rc.canAttack(loc)) continue;

            if (Clock.getBytecodesLeft() < 1000) break; // Safety check

            int score = evaluateSplashScore(loc, preferEnemy);

            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
            }
        }

        return bestLoc;
    }

    /**
     * Evaluasi score splash di satu lokasi.
     * Memeriksa semua tile dalam radius 2 dari titik pusat splash.
     */
    private static int evaluateSplashScore(MapLocation center, boolean preferEnemy)
            throws GameActionException {
        int score = 0;

        MapInfo[] splashArea = rc.senseNearbyMapInfos(center, 2);
        for (MapInfo t : splashArea) {
            if (!t.isPassable()) continue;

            PaintType paint = t.getPaint();

            // Cek apakah ada robot musuh di tile ini
            if (rc.canSenseRobotAtLocation(t.getMapLocation())) {
                RobotInfo robot = rc.senseRobotAtLocation(t.getMapLocation());
                if (robot != null && robot.getTeam() != rc.getTeam()) {
                    if (isTowerType(robot.getType())) {
                        score += 10; // Tower musuh: sangat berharga!
                    } else {
                        score += 4;  // Robot musuh dalam area
                    }
                    continue;
                }
            }

            if (!paint.isAlly()) {
                if (paint == PaintType.EMPTY) {
                    score += 1; // Kosong: gain territory
                } else {
                    // Paint musuh
                    score += preferEnemy ? 4 : 2; // Overwrite lebih berharga
                }
            }
            // Ally paint: score 0
        }

        return score;
    }

    // =========================================================================
    //  HELPER METHODS
    // =========================================================================

    /**
     * Cari tower musuh dari array enemies.
     */
    private static RobotInfo findEnemyTower(RobotInfo[] enemies) {
        RobotInfo best = null;
        int minHP = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (isTowerType(enemy.getType()) && enemy.health < minHP) {
                minHP = enemy.health;
                best = enemy;
            }
        }
        return best;
    }

    /**
     * Bergerak mundur dari target.
     */
    private static void retreatFrom(MapLocation target) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        // Arah dari target ke kita = arah mundur
        Direction awayDir = target.directionTo(myLoc);
        if (awayDir != null && rc.canMove(awayDir)) {
            rc.move(awayDir);
        } else {
            // Coba arah lain yang menjauh
            for (Direction dir : directions) {
                MapLocation next = myLoc.add(dir);
                if (rc.canMove(dir) && next.distanceSquaredTo(target) > myLoc.distanceSquaredTo(target)) {
                    rc.move(dir);
                    return;
                }
            }
            wander();
        }
    }

    /**
     * Bergerak ke posisi optimal untuk splash target.
     * Splasher attack radius: titik pusat maks 4 petak (sqrt(4) = 2).
     */
    private static void moveTowardForSplash(MapLocation target) throws GameActionException {
        // Jika bisa langsung attack, tidak perlu gerak
        if (rc.canAttack(target)) return;

        // Bergerak mendekat
        moveGreedy(target);
    }

    /**
     * Cari area yang belum di-paint untuk expand.
     * Greedy: pilih yang paling jauh dari spawn (area baru).
     */
    private static MapLocation findUnpaintedArea() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation best = null;
        int maxDist = 0;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            if (tile.getPaint() == PaintType.EMPTY) {
                MapLocation loc = tile.getMapLocation();
                int dist = loc.distanceSquaredTo(spawnLocation != null ? spawnLocation : rc.getLocation());
                if (dist > maxDist) {
                    maxDist = dist;
                    best = loc;
                }
            }
        }
        return best;
    }
}
