package algorithm.Units;

import algorithm.Unit;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;

public class Soldier extends Unit {
    
    public static void run() throws GameActionException {
        initUnit();
        if (tryBuildTower()) {
            return; 
        }
        if (refillPaint()) {
            return;
        }

        // Penyerangan  
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapLocation targetEnemy = null;

        for (RobotInfo enemy : enemies) {
            if (enemy.getType().isTowerType()) {
                broadcastEnemyTower(enemy.getLocation());
                targetEnemy = enemy.getLocation();
                break; 
            }
        }

        if (targetEnemy == null && enemies.length > 0) {
            broadcastEnemyTarget(enemies[0].getLocation());
            targetEnemy = enemies[0].getLocation();
        }

        if (targetEnemy != null) {
            if (rc.isActionReady() && rc.canAttack(targetEnemy)) {
                rc.attack(targetEnemy);
            } else if (rc.isMovementReady()) {
                move(targetEnemy); 
            }
            return; 
        }

        // Make SRP
        if (tryBuildSRP()) {
            return; 
        }

        // Mewarnai
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        MapLocation bestMarkedTile = null;
        boolean useSecondaryPaint = false;
        int minMarkDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            PaintType mark = tile.getMark();
            PaintType paint = tile.getPaint();
            
            if (mark != PaintType.EMPTY && mark != paint && tile.isPassable()) {
                if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY) {
                    int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                    if (dist < minMarkDist) {
                        minMarkDist = dist;
                        bestMarkedTile = tile.getMapLocation();
                        useSecondaryPaint = (mark == PaintType.ALLY_SECONDARY);
                    }
                }
            }
        }
        
        if (bestMarkedTile != null) {
            if (rc.isActionReady() && rc.canAttack(bestMarkedTile)) {
                rc.attack(bestMarkedTile, useSecondaryPaint);
            } else if (rc.isMovementReady()) {
                move(bestMarkedTile);
            }
            return;
        }

        // Cari lantai kosong untuk mark
        MapLocation bestTileToPaint = null;
        int minDistance = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles) {
            PaintType paint = tile.getPaint();
            if (!paint.isAlly() && tile.isPassable()) {
                int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    bestTileToPaint = tile.getMapLocation();
                }
            }
        }

        if (bestTileToPaint != null) {
            if (rc.isActionReady() && rc.canAttack(bestTileToPaint)) {
                rc.attack(bestTileToPaint);
            } else if (rc.isMovementReady()) {
                move(bestTileToPaint);
            }
        } else if (rc.isMovementReady()) {
            MapLocation sosTarget = getBroadcastTarget();
            if (sosTarget != null) {
                move(sosTarget);
            } else {
                wander();
            }
        }
    }

    // Pembuatan SRP
    private static boolean tryBuildSRP() throws GameActionException {
        boolean isSRPBuilder = (rc.getID() % 4 == 0); 
        
        // Bukan Bot untuk SRP
        if (!isSRPBuilder) {
            return false;
        }
        // Syarat pembuatan SRP
        if (rc.getRoundNum() < 500 || rc.getNumberTowers() < 5) {
            return false;
        }
        // Memiliki Money cukup
        if (rc.getMoney() < 2500) {
            return false;
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                return true;
            }
            if (rc.canMarkResourcePattern(loc)) {
                rc.markResourcePattern(loc);
                return true; 
            }
        }

        return false;
    }
}