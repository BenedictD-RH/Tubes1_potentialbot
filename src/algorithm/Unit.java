package algorithm;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;




public class Unit extends BaseRobot {

    public static MapLocation spawnLocation;

    public static void initUnit() {
        if(spawnLocation == null){
            spawnLocation = rc.getLocation();
        }
    }

    public static void move(MapLocation target) throws GameActionException{
        if(!rc.isMovementReady()){
            return;
        }

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int minDistance = myLoc.distanceSquaredTo(target);
         for(Direction dir : directions) {
            if (rc.canMove(dir)){
                MapLocation nextLoc = myLoc.add(dir);
                int dist = nextLoc.distanceSquaredTo(target);

                if(dist < minDistance){
                    minDistance = dist;
                    bestDir = dir;
                }
            }
         }

         if( bestDir != null){
            rc.move(bestDir);
         }

    }

    public static void buildTower() throws GameActionException {
        if(!rc.isActionReady()){
            return;
        }

        UnitType[] towersToBuild = {
            UnitType.LEVEL_ONE_PAINT_TOWER,
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_DEFENSE_TOWER
        };

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            MapLocation buildLoc = tile.getMapLocation();
            for(UnitType tower : towersToBuild){
                if (rc.canBuildRobot(tower, buildLoc)){
                    rc.buildRobot(tower, buildLoc);
                    System.out.println("Tower built at " + buildLoc);
                    return;
                }
            }
        }
    }

    public static boolean refillPaint() throws GameActionException {
        int paintThreshold = rc.getType().paintCapacity / 5; // titik kritis di 20% kapasitas
        if (rc.getPaint() > paintThreshold) {
            return false;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearestPaintTower = null;
        int minDistance = Integer.MAX_VALUE;

        for (RobotInfo ally : allies){ // cek paint tower terdekat
            if(ally.getType() == UnitType.LEVEL_ONE_PAINT_TOWER ||
               ally.getType() == UnitType.LEVEL_TWO_PAINT_TOWER ||
               ally.getType() == UnitType.LEVEL_THREE_PAINT_TOWER){
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < minDistance){
                    minDistance = dist;
                    nearestPaintTower = ally.getLocation();
                }
            }
        }

        if(nearestPaintTower != null){
            int dist = rc.getLocation().distanceSquaredTo(nearestPaintTower);
            if(dist > 2){
                if(rc.isMovementReady()){
                    move(nearestPaintTower);
                }
            } else {
                int needed = rc.getType().paintCapacity - rc.getPaint();
                if (needed > 0 && rc.canSenseRobotAtLocation(nearestPaintTower)) {
                    RobotInfo tower = rc.senseRobotAtLocation(nearestPaintTower);
                    int available = tower.getPaintAmount();
                    int transferAmount = Math.min(needed, available);
                    if (transferAmount > 0 && rc.canTransferPaint(nearestPaintTower, -transferAmount)) {
                        rc.transferPaint(nearestPaintTower, -transferAmount);
                    }
                }
            }
            return true;
        }

        if(spawnLocation != null && rc.isMovementReady()){
            move(spawnLocation);
        }
        return true;
    }

}
