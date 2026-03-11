package algorithm;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower extends BaseRobot {
    
    public static void run() throws GameActionException {
        // Defense system for defense towers
        if (rc.getType() == UnitType.LEVEL_ONE_DEFENSE_TOWER ||
            rc.getType() == UnitType.LEVEL_TWO_DEFENSE_TOWER ||
            rc.getType() == UnitType.LEVEL_THREE_DEFENSE_TOWER) {
            
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length > 0 && rc.isActionReady()) {
                MapLocation targetEnemy = enemies[0].getLocation();
                if (rc.canAttack(targetEnemy)) {
                    rc.attack(targetEnemy);
                    System.out.println("Defense tower attacked enemy at " + targetEnemy);
                    return;
                }
            }
        }

        // Upgrade system 
        if (rc.getMoney() > 2500 && rc.isActionReady()) {
            if (rc.canUpgradeTower(rc.getLocation())) {
                rc.upgradeTower(rc.getLocation());
                return;
            }
        }

        // Robot production
        if (rc.isActionReady() && rc.getMoney() >= 500) {
            buildArmy();
        }
    }

    private static void buildArmy() throws GameActionException {
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
