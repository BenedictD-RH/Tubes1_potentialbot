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
                    return;
                }
            }
        }

        if (rc.isActionReady() && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
            return;
        }

        if (rc.isActionReady()) {
            boolean isSavingMoney = (rc.getRoundNum() > 200 && rc.getMoney() > 1000);
            if (!isSavingMoney && rc.getMoney() >= 500) {
                buildStrategicArmy();
            }
        }
    }

    private static void buildStrategicArmy() throws GameActionException {
        double rand = Math.random();
        UnitType typeToBuild;

        if (rand < 0.65) {
            typeToBuild = UnitType.SOLDIER;
        } else if (rand < 0.95) {
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
