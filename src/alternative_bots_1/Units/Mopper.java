package alternative_bots_1.Units;

import alternative_bots_1.Unit;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;

public class Mopper extends Unit {
    
    public static void run() throws GameActionException{
        initUnit();
        if (refillPaint()) {
            return;
        }

    
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
            targetEnemy = enemies[0].getLocation();
            broadcastEnemyTarget(targetEnemy);
        }
        if (targetEnemy != null) {
            if (rc.isActionReady() && rc.canAttack(targetEnemy)) {
                rc.attack(targetEnemy);
            } else if (rc.isMovementReady()) {
                move(targetEnemy);
            }
            return;
        }

        // Membersihkan area musuh
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1); 
        
        MapLocation bestTileToMop = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (MapInfo tile : nearbyTiles) {
            PaintType paint = tile.getPaint();
            if (paint.isEnemy()) {
                int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                if (dist < minDistance) {
                    minDistance = dist;
                    bestTileToMop = tile.getMapLocation();
                }
            }
        }
        if (bestTileToMop != null) {
            if (rc.isActionReady() && rc.canAttack(bestTileToMop)) {
                rc.attack(bestTileToMop);
            } else if (rc.isMovementReady()) {
                move(bestTileToMop);
            }
        }
        else if (rc.isMovementReady()) {
            MapLocation sosTarget = getBroadcastTarget();
            if (sosTarget != null) {
                move(sosTarget);
            } else {
                wander();
            }
        }
    }
}