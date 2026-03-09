package algorithm.Units;

import algorithm.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;


public class Mopper extends Unit {
    public static void run() throws GameActionException{
        RobotInfo[] enemies = rc.senseNearbyRobots(-1,rc.getTeam().opponent());

        if(enemies.length > 0){
            MapLocation targetEnemy = enemies[0].getLocation();

            if(rc.canAttack(targetEnemy)){
                rc.attack(targetEnemy);
            }
            else{
                move(targetEnemy);
            }
        }
        else{
            wander();
        }
    }

    private static void wander() throws GameActionException {
        if (!rc.isMovementReady()) {
            return;
        }

        int randomIndex = (int) (Math.random() * directions.length);
        Direction randomDir = directions[randomIndex];

        if(rc.canMove(randomDir)) {
            rc.move(randomDir);
        }
    }
}