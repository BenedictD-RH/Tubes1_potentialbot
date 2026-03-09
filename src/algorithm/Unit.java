package algorithm;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;



public class Unit extends BaseRobot {

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

}