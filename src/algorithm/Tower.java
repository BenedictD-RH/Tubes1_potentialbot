package algorithm;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.UnitType;

public class Tower extends BaseRobot {
    
    public static void run() throws GameActionException {
        if (rc.isActionReady()) {
            spawnBot();
        }
    }

    private static void spawnBot() throws GameActionException {
        double rand = Math.random();
        UnitType typeToBuild;

        if (rand < 0.50) {
            typeToBuild = UnitType.SOLDIER;
        } else if (rand < 0.80) {
            typeToBuild = UnitType.SPLASHER;
        } else {
            typeToBuild = UnitType.MOPPER;
        }

        for (Direction dir : directions) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(typeToBuild, spawnLoc)) {
                rc.buildRobot(typeToBuild, spawnLoc);
                break;
            }
        }
    }

}