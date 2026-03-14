package alternative_bots_1.Units;

import alternative_bots_1.Unit;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;


public class Soldier extends Unit {
    
    public static void run() throws GameActionException{
        initUnit();
        if (tryBuildTower()) {
            return; 
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            broadcastEnemyTarget(enemies[0].getLocation());
        }

        if(refillPaint()){
            return;
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        MapLocation bestMarkedTile = null;
        boolean useSecondaryPaint = false;
        int minMarkDist = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            PaintType mark = tile.getMark();
            PaintType paint = tile.getPaint();
            
            if(mark != PaintType.EMPTY && mark != paint && tile.isPassable()){
                if (mark == PaintType.ALLY_PRIMARY || mark == PaintType.ALLY_SECONDARY){
                    int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                    if(dist < minMarkDist){
                        minMarkDist = dist;
                        bestMarkedTile = tile.getMapLocation();
                        useSecondaryPaint = mark == PaintType.ALLY_SECONDARY;
                    }
                }
            }
        }
        
        if (bestMarkedTile != null){
            if(rc.isActionReady() && rc.canAttack(bestMarkedTile)){
                rc.attack(bestMarkedTile, useSecondaryPaint);
            } else if (rc.isMovementReady()){
                move(bestMarkedTile);
            }
            return;
        }

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

}
