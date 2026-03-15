package alternative_bots_2.Units;

import alternative_bots_2.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;

public class Mopper extends Unit {
    private static final int MOPPER_REFILL_PCT = 30;
    private static MapLocation moveTarget = null;

    public static void run() throws GameActionException {
        initUnit();
        scanEnvironment();
        processMessages();

        // Membersihkan area musuh
        if (rc.isActionReady()) {
            if (!tryMopSwing()) {
                tryAttackTarget();
            }
        }

        // transfer paint ke teman yang kritis
        if (rc.isActionReady()) {
            tryTransferPaint();
        }

        // refill paint
        int pct = (rc.getPaint() * 100) / rc.getType().paintCapacity;
        if (pct <= MOPPER_REFILL_PCT) {
            if (rc.isMovementReady()) refillPaint();
            return;
        }

        // bergerak 
        if (rc.isMovementReady()) {
            updateMoveTarget();
            if (moveTarget != null) {
                moveGreedy(moveTarget);
            } else {
                explore();
            }
        }
    }

    // Fungsi Membersihkan
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

            if (hits > maxHits) {
                maxHits = hits;
                bestDir = dir;
            }
        }

        if (bestDir != null && maxHits >= 2) {
            rc.mopSwing(bestDir);
            return true;
        }
        return false;
    }

    // Fungsi attack ke mush
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

        // Tile dengan paint musuh terdekat dalam attack radius
        MapInfo[] tiles = rc.senseNearbyMapInfos(2);
        MapLocation bestTile = null;
        int minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (!paint.isAlly() && paint != PaintType.EMPTY) {
                MapLocation loc = tile.getMapLocation();
                int d = myLoc.distanceSquaredTo(loc);
                if (d < minDist && rc.canAttack(loc)) {
                    minDist = d;
                    bestTile = loc;
                }
            }
        }
        if (bestTile != null) {
            rc.attack(bestTile);
            return true;
        }

        return false;
    }

    // Fungsi transfer paint ke teman yang kritis
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

    // Fungsi bergeak
    private static void updateMoveTarget() throws GameActionException {
        // Paint musuh terdekat
        MapLocation enemyPaint = findNearestEnemyPaint();
        if (enemyPaint != null) {
            moveTarget = enemyPaint;
            return;
        }

        // Robot musuh non-tower terlemah dalam sensor
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo weakest = null;
        int minPaint = Integer.MAX_VALUE;
        for (RobotInfo e : enemies) {
            if (!isTowerType(e.getType()) && e.getPaintAmount() < minPaint) {
                minPaint = e.getPaintAmount();
                weakest = e;
            }
        }
        if (weakest != null) {
            moveTarget = weakest.getLocation();
            return;
        }

        // Broadcast (ada ruin butuh dibersihkan)
        MapLocation mopReq = readBroadcastTarget(MSG_MOPPER_REQ);
        if (mopReq != null) {
            moveTarget = mopReq;
            return;
        }

        //  Broadcast ( lokasi musuh umum )
        MapLocation enemyLoc = readBroadcastTarget(MSG_ENEMY_LOC);
        if (enemyLoc != null) {
            moveTarget = enemyLoc;
            return;
        }

        // Last known enemy
        if (lastKnownEnemyLoc != null && rc.getRoundNum() - lastKnownEnemyRound < 100) {
            moveTarget = lastKnownEnemyLoc;
            return;
        }

        // Tidak ada target
        moveTarget = null;
    }

    // Fungsi cari paint musuh terdekat
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
}
