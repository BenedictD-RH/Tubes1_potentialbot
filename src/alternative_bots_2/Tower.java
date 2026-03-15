package alternative_bots_2;

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

        if (rc.getChips() < 1300) return; // 1000 tower reserve + 300 buffer

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        int soldierCount  = 0;
        int mopperCount   = 0;
        int splasherCount = 0;

        for (RobotInfo r : nearbyAllies) {
            if (r.getType() == UnitType.SOLDIER)       soldierCount++;
            else if (r.getType() == UnitType.MOPPER)   mopperCount++;
            else if (r.getType() == UnitType.SPLASHER) splasherCount++;
        }

        
        // Scale with map size — bigger map needs more soldiers
        int mapSize = rc.getMapWidth() * rc.getMapHeight();
        boolean lateGame  = rc.getRoundNum() > 500;
        boolean bigMap    = mapSize > 1600;

        int soldierTarget  = bigMap ? (lateGame ? 2 : 3) : (lateGame ? 3 : 5);
        int splasherTarget = bigMap ? (lateGame ? 1 : 0) : (lateGame ? 2 : 0);
        int mopperTarget   = bigMap ? 2 : 3;

        UnitType toBuild = null;

        if (lateGame && splasherCount < splasherTarget)  toBuild = UnitType.SPLASHER;
        else if (soldierCount < soldierTarget)           toBuild = UnitType.SOLDIER;
        else if (mopperCount  < mopperTarget)            toBuild = UnitType.MOPPER;

        if (toBuild == null) return;

        for (Direction d : RobotPlayer.directions) {
            MapLocation loc = rc.getLocation().add(d);
            if (rc.canBuildRobot(toBuild, loc)) {
                rc.buildRobot(toBuild, loc);
                break;
            }
        }
    }
}
