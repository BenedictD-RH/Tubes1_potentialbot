package mapSpammer;

import battlecode.common.*;

public class Tower {

    public static void runTower(RobotController rc) throws GameActionException {
        executeAttack(rc);
        executeBuild(rc);
    }

    // EXECUTE: Transfer attack enemy robots
    private static void executeAttack(RobotController rc) throws GameActionException {

        if (!rc.isActionReady()) return;

        MapLocation myLoc = rc.getLocation();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.getLocation())) continue;

            int score = 0;
            score -= enemy.getHealth();
            score -= myLoc.distanceSquaredTo(enemy.getLocation());

            if (score > bestScore) {
                bestScore = score;
                bestTarget = enemy.getLocation();
            }
        }

        if (bestTarget != null) {
            rc.attack(bestTarget);
        }
    }

    // EXECUTE: Build soldiers and moppers
    private static void executeBuild(RobotController rc) throws GameActionException {

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        int soldierCount = 0;
        int mopperCount  = 0;

        for (RobotInfo r : nearbyAllies) {
            if (r.getType() == UnitType.SOLDIER)     soldierCount++;
            else if (r.getType() == UnitType.MOPPER) mopperCount++;
        }

        UnitType toBuild = null;

        if (soldierCount < 5) {
            toBuild = UnitType.SOLDIER;
        } else if (mopperCount < 3) {
            toBuild = UnitType.MOPPER;
        }

        if (toBuild == null || rc.getChips() < 300) return;

        for (Direction d : RobotPlayer.directions) {
            MapLocation loc = rc.getLocation().add(d);
            if (rc.canBuildRobot(toBuild, loc)) {
                rc.buildRobot(toBuild, loc);
                break;
            }
        }
    }
}
