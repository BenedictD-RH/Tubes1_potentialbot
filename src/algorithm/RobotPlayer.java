package algorithm;

import algorithm.Units.Mopper;
import algorithm.Units.Soldier;
import algorithm.Units.Splasher;
import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class RobotPlayer {

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        BaseRobot.rc = rc;

        while (true) {
            try {
                switch (rc.getType()) {
                    case MOPPER:
                        Mopper.run();
                        break;
                    case SOLDIER:
                        Soldier.run();
                        break;
                    case SPLASHER:
                        Splasher.run();
                        break;
                    case LEVEL_ONE_PAINT_TOWER:
                    case LEVEL_ONE_MONEY_TOWER:
                    case LEVEL_ONE_DEFENSE_TOWER:
                    case LEVEL_TWO_PAINT_TOWER:
                    case LEVEL_TWO_MONEY_TOWER:
                    case LEVEL_TWO_DEFENSE_TOWER:
                    case LEVEL_THREE_PAINT_TOWER:
                    case LEVEL_THREE_MONEY_TOWER:
                    case LEVEL_THREE_DEFENSE_TOWER:
                        
                        Tower.run();
                        break;

                    default:
                        break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            }
            
            Clock.yield(); 
        }
    }
}
