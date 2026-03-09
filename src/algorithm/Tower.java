package algorithm;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.UnitType;


public class Tower extends BaseRobot{
    public static void run() throws GameActionException {
        spawnRobot(UnitType.MOPPER);
    }

    private static void spawnRobot(UnitType type) throws GameActionException {
        for(Direction dir : directions){
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if(rc.canBuildRobot(type, spawnLoc)){
                rc.buildRobot(type, spawnLoc);
                System.out.println("Spawned " + type + " at " + spawnLoc);
                break;
            }
        }
    }

}
