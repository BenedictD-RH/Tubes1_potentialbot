package main_bot.Units;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import main_bot.Unit;

public class Splasher extends Unit {

    // State untuk splasher
    private static final int STATE_ATTACK_TOWER = 0;
    private static final int STATE_SPLASH       = 1;
    private static final int STATE_RETREAT      = 2;
    private static final int STATE_EXPLORE      = 3;

    private static int state = STATE_EXPLORE; // default state
    private static MapLocation enemyTowerTarget = null;
    private static final int MIN_SPLASH_SCORE = 3;

    // Fungsi untuk menjalankan splasher
    public static void run() throws GameActionException {
        initUnit();
        processMessages();
        scanEnvironment();
        tryUpgradeNearbyTower();

        // Refiil paint ketika paint < 5%
        int pct = (rc.getPaint() * 100) / rc.getType().paintCapacity;
        if (pct <= 5) {
            state = STATE_RETREAT;
            refillPaint();
            return;
        }
        // bergerak setelah paint terisi
        if (state == STATE_RETREAT && pct > 8) {
            state = STATE_EXPLORE;
        }
        updateState();
        executeState();
    }

    // Fungsi untuk update state: cek enemy tower (sensor/cache/simetri) → splash → explore
    private static void updateState() throws GameActionException {
        if (state == STATE_RETREAT) return;

        // Enemy tower -> attack (sensor, cache, symmetry guess)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo e : enemies) {
            if (isTowerType(e.getType())) {
                enemyTowerTarget = e.getLocation();
                state = STATE_ATTACK_TOWER;
                broadcastEnemyTower(e.getLocation());
                return;
            }
        }

        // Cache dari broadcast
        if (enemyTowerTarget == null) {
            MapLocation towerBroadcast = readBroadcastTarget(MSG_ENEMY_TOWER);
            if (towerBroadcast != null) {
                enemyTowerTarget = towerBroadcast;
                state = STATE_ATTACK_TOWER;
                return;
            }
        }

        // Symmetry guess
        if (enemyTowerTarget == null) {
            MapLocation guess = guessEnemyTower();
            if (guess != null) {
                enemyTowerTarget = guess;
                state = STATE_ATTACK_TOWER;
                return;
            }
        }

        // Empty ruin -> paint
        MapLocation splashTarget = findBestSafeSplashTarget();
        if (splashTarget != null) {
            state = STATE_SPLASH;
            return;
        }

        // Explore
        state = STATE_EXPLORE;
    }

    // Fungsi untuk menjalankan aksi sesuai state saat ini
    private static void executeState() throws GameActionException {
        switch (state) {
            case STATE_ATTACK_TOWER: executeAttackTower(); break;
            case STATE_SPLASH:       executeSplash(); break;
            case STATE_RETREAT:      refillPaint(); break;
            default:                 executeExplore(); break;
        }
    }

    // Fungsi untuk menyerang tower musuh
    private static void executeAttackTower() throws GameActionException {
        if (enemyTowerTarget == null) { state = STATE_EXPLORE; return; }

        if (rc.canSenseLocation(enemyTowerTarget)) {
            if (!rc.canSenseRobotAtLocation(enemyTowerTarget)) {
                enemyTowerTarget = null;
                state = STATE_EXPLORE;
                return;
            }
        }

        if (rc.isActionReady() && rc.canAttack(enemyTowerTarget)) {
            rc.attack(enemyTowerTarget);
        }

        if (rc.isMovementReady()) {
            MapLocation myLoc = rc.getLocation();
            int dist = myLoc.distanceSquaredTo(enemyTowerTarget);
            if (dist > 4) {
                bugNavigateTo(enemyTowerTarget);
            } else if (dist <= 2) {
                retreatFrom(enemyTowerTarget);
            }
        }
    }

    // Fungsi untuk splash area
    private static void executeSplash() throws GameActionException {
        MapLocation splashTarget = findBestSafeSplashTarget();
        if (splashTarget != null && rc.isActionReady() && rc.canAttack(splashTarget)) {
            rc.attack(splashTarget);
            return;
        }

        // Mendekati tujuan
        if (rc.isMovementReady()) {
            MapLocation enemyConcentration = findEnemyPaintConcentration();
            if (enemyConcentration != null) {
                bugNavigateTo(enemyConcentration);
            } else {
                explore();
            }
        }

        // Try splash lagi setelah move
        if (rc.isActionReady()) {
            splashTarget = findBestSafeSplashTarget();
            if (splashTarget != null && rc.canAttack(splashTarget)) {
                rc.attack(splashTarget);
            }
        }
    }

    // Fungsi untuk eksplorasi
    private static void executeExplore() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            broadcastEnemyLocation(enemies[0].getLocation());
        }

        if (rc.isActionReady()) {
            MapLocation opp = findBestSafeSplashTarget();
            if (opp != null && rc.canAttack(opp)) {
                rc.attack(opp);
            }
        }

        if (rc.isMovementReady()) {
            MapLocation enemyConcentration = findEnemyPaintConcentration();
            if (enemyConcentration != null) {
                bugNavigateTo(enemyConcentration);
            } else {
                explore();
            }
        }
    }

    // Fungsi untuk splash yang aman
    private static boolean isSafeToSplashAt(MapLocation center) throws GameActionException {
        MapInfo[] area = rc.senseNearbyMapInfos(center, 2);
        for (MapInfo t : area) {
            if (t.hasRuin()) return false;     
            if (t.getPaint().isAlly()) return false;
        }
        RobotInfo[] allyNear = rc.senseNearbyRobots(center, 2, rc.getTeam());
        for (RobotInfo r : allyNear) {
            if (isTowerType(r.getType())) return false;
        }
        return true;
    }

    // Fungsi untuk mencari target splash terbaik yang aman (score >= 3, tidak ada ruin/ally)
    private static MapLocation findBestSafeSplashTarget() throws GameActionException {
        if (Clock.getBytecodesLeft() < 2000) return null;
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(4);
        MapLocation bestLoc = null;
        int bestScore = MIN_SPLASH_SCORE - 1;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.isPassable()) continue;
            MapLocation loc = tile.getMapLocation();
            if (!rc.canAttack(loc)) continue;
            if (Clock.getBytecodesLeft() < 1000) break;
            if (!isSafeToSplashAt(loc)) continue;

            int score = evaluateSplashScore(loc);
            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
            }
        }
        return bestLoc;
    }

    // Fungsi untuk menghitung skor splash: empty +1, enemy inner +4, enemy outer +2, ally 0
    private static int evaluateSplashScore(MapLocation center) throws GameActionException {
        int score = 0;
        MapInfo[] splashArea = rc.senseNearbyMapInfos(center, 4);
        for (MapInfo t : splashArea) {
            if (!t.isPassable()) continue;
            MapLocation tLoc = t.getMapLocation();
            PaintType paint = t.getPaint();
            int distSq = center.distanceSquaredTo(tLoc);

            // Robot musuh bonus
            if (rc.canSenseRobotAtLocation(tLoc)) {
                RobotInfo robot = rc.senseRobotAtLocation(tLoc);
                if (robot != null && robot.getTeam() != rc.getTeam()) {
                    score += isTowerType(robot.getType()) ? 10 : 4;
                    continue;
                }
            }

            if (paint.isAlly()) continue;
            if (paint == PaintType.EMPTY) {
                score += 1;
            } else {
                // Enemy paint
                score += (distSq <= 2) ? 4 : 2;
            }
        }
        return score;
    }

    // Fungsi untuk estimasi simetri musuh
    private static MapLocation guessEnemyTower() throws GameActionException {
        int W = rc.getMapWidth(), H = rc.getMapHeight();
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                MapLocation aLoc = ally.getLocation();
                return new MapLocation(W - 1 - aLoc.x, H - 1 - aLoc.y);
            }
        }
        return null;
    }

    // Fungsi untuk mencari konsentrasi paint musuh
    private static MapLocation findEnemyPaintConcentration() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int maxScore = 0;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (!paint.isAlly() && paint != PaintType.EMPTY) {
                MapLocation loc = tile.getMapLocation();
                int dist = myLoc.distanceSquaredTo(loc);
                int score = 100 - dist;
                if (score > maxScore) { maxScore = score; best = loc; }
            }
        }
        return best;
    }

    // Fungsi untuk mundur menjauhi target 
    private static void retreatFrom(MapLocation target) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        Direction awayDir = target.directionTo(myLoc);
        if (awayDir != Direction.CENTER && rc.canMove(awayDir)) {
            rc.move(awayDir);
            return;
        }
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
