package main_bot.Units;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import main_bot.Unit;

public class Mopper extends Unit {

    private static final int RETREAT_PAINT_PCT = 5;
    private static int lastBroadcastRound = 0;

    // Fungsi untuk menjalankan logika mopper
    public static void run() throws GameActionException {
        initUnit();
        processMessages();
        scanEnvironment();
        tryUpgradeNearbyTower();

        // refill paint ketika paint < 5%
        int pct = (rc.getPaint() * 100) / rc.getType().paintCapacity;
        if (pct <= RETREAT_PAINT_PCT) {
            if (rc.isMovementReady()) refillPaint();
            return;
        }

        // Mop swing ke arah dengan musuh terbanyak
        if (rc.isActionReady()) {
            if (!tryMopSwing()) {
                tryAttackTarget();
            }
        }

        // Transfer paint ke teman yang kritis
        if (rc.isActionReady()) {
            tryTransferPaint();
        }

        // Broadcast enemy info tiap 15 ronde
        int round = rc.getRoundNum();
        if (round - lastBroadcastRound >= 15) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length > 0) {
                broadcastEnemyLocation(enemies[0].getLocation());
                lastBroadcastRound = round;
            }
        }

        // Bergerak
        if (rc.isMovementReady()) {
            Direction pushDir = directionTowardMostEnemyPaint();
            if (pushDir != null && rc.canMove(pushDir)) {
                rc.move(pushDir);
            } else {
                MapLocation moveTarget = findMoveTarget();
                if (moveTarget != null) {
                    bugNavigateTo(moveTarget);
                } else {
                    explore();
                }
            }
        }

        // Mop lagi setelah bergerak
        if (rc.isActionReady()) {
            tryAttackTarget();
        }
    }

    // Fungsi untuk mop swing ke arah cardinal dengan musuh terbanyak (min 2 hit)
    private static boolean tryMopSwing() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(2, rc.getTeam().opponent());
        if (enemies.length < 2) return false;

        Direction bestDir = null;
        int maxHits = 0;
        MapLocation myLoc = rc.getLocation();

        for (Direction dir : cardinalDirs) {
            if (!rc.canMopSwing(dir)) continue;
            int hits = 0;
            MapLocation p1 = myLoc.add(dir);
            MapLocation p2 = p1.add(dir);
            for (RobotInfo e : enemies) {
                MapLocation eLoc = e.getLocation();
                if (eLoc.distanceSquaredTo(p1) <= 1 || eLoc.distanceSquaredTo(p2) <= 1) {
                    hits++;
                }
            }
            if (hits > maxHits) { maxHits = hits; bestDir = dir; }
        }

        if (bestDir != null && maxHits >= 2) {
            rc.mopSwing(bestDir);
            return true;
        }
        return false;
    }

    // Fungsi untuk menyerang
    private static boolean tryAttackTarget() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Robot musuh non-tower dengan paint paling sedikit
        RobotInfo[] enemies = rc.senseNearbyRobots(2, rc.getTeam().opponent());
        RobotInfo weakest = null;
        int minPaint = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (!isTowerType(e.getType()) && e.getPaintAmount() < minPaint) {
                minPaint = e.getPaintAmount();
                weakest = e;
            }
        }
        if (weakest != null && rc.canAttack(weakest.getLocation())) {
            rc.attack(weakest.getLocation());
            return true;
        }

        // Tile enemy paint terdekat
        MapInfo[] tiles = rc.senseNearbyMapInfos(2);
        MapLocation bestTile = null;
        int bestScore = Integer.MIN_VALUE;
        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (!paint.isAlly() && paint != PaintType.EMPTY) {
                MapLocation loc = tile.getMapLocation();
                if (!rc.canAttack(loc)) continue;
                int score = 10;
                if (rc.canSenseRobotAtLocation(loc)) {
                    RobotInfo r = rc.senseRobotAtLocation(loc);
                    if (r != null && r.getTeam() != rc.getTeam()) score += 20;
                }
                score -= myLoc.distanceSquaredTo(loc);
                if (score > bestScore) { bestScore = score; bestTile = loc; }
            }
        }
        if (bestTile != null) {
            rc.attack(bestTile);
            return true;
        }
        return false;
    }

    // Fungsi untuk transfer paint ke ally yang kritis (beri 25% milik sendiri)
    private static boolean tryTransferPaint() throws GameActionException {
        int myPct = (rc.getPaint() * 100) / rc.getType().paintCapacity;
        if (myPct < 50) return false;

        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) continue;
            if (ally.getType().paintCapacity == 0) continue;
            int allyPct = (ally.getPaintAmount() * 100) / ally.getType().paintCapacity;
            if (allyPct < 20) {
                int give = rc.getPaint() / 4;
                if (give > 0 && rc.canTransferPaint(ally.getLocation(), give)) {
                    rc.transferPaint(ally.getLocation(), give);
                    return true;
                }
            }
        }
        return false;
    }

    // Fungsi untuk mencari arah dengan konsentrasi enemy paint terbanyak
    private static Direction directionTowardMostEnemyPaint() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        int[] dirScore = new int[8];

        MapLocation myLoc = rc.getLocation();
        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (!paint.isAlly() && paint != PaintType.EMPTY) {
                Direction toTile = myLoc.directionTo(tile.getMapLocation());
                if (toTile == Direction.CENTER) continue;
                dirScore[toTile.ordinal()]++;
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist <= 4) dirScore[toTile.ordinal()] += 2;
            }
        }

        int bestScore = 0;
        Direction bestDir = null;
        for (int i = 0; i < 8; i++) {
            if (dirScore[i] > bestScore && rc.canMove(directions[i])) {
                bestScore = dirScore[i];
                bestDir = directions[i];
            }
        }
        return bestDir;
    }

    // Fungsi untuk mencari target pergerakan ( enemy paint -> robot musuh -> broadcast)
    private static MapLocation findMoveTarget() throws GameActionException {
        // Paint musuh terdekat dalam sensor
        MapLocation enemyPaint = findNearestEnemyPaint();
        if (enemyPaint != null) return enemyPaint;

        // Robot musuh
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo weakest = null;
        int minPaint = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (!isTowerType(e.getType()) && e.getPaintAmount() < minPaint) {
                minPaint = e.getPaintAmount();
                weakest = e;
            }
        }
        if (weakest != null) return weakest.getLocation();

        // Broadcast targets
        MapLocation mopReq = readBroadcastTarget(MSG_MOPPER_REQ);
        if (mopReq != null) return mopReq;

        MapLocation enemyLoc = readBroadcastTarget(MSG_ENEMY_LOC);
        if (enemyLoc != null) return enemyLoc;

        if (lastKnownEnemyLoc != null && rc.getRoundNum() - lastKnownEnemyRound < 100) {
            return lastKnownEnemyLoc;
        }
        return null;
    }

    // Fungsi untuk mencari tile enemy paint terdekat dalam sensor range
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
                if (dist < minDist) { minDist = dist; best = tile.getMapLocation(); }
            }
        }
        return best;
    }
}
