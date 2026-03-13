package mapSpammer;

import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {

    static int turnCount = 0;
    static final Random rng = new Random(6147);
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };
    
    @SuppressWarnings("unused")
    static MapLocation mapCenter;

    public static void run(RobotController rc) throws GameActionException {

        if (mapCenter == null) {
            mapCenter = new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2);
        }

        rc.setIndicatorString("Hello world!");

        while (true) {
            turnCount += 1;

            try {
                switch (rc.getType()) {
                    case SOLDIER:  Soldier.runSoldier(rc); break;
                    case MOPPER:   Mopper.runMopper(rc);   break;
                    case SPLASHER: Splasher.runSplasher(rc); break;
                    default:       Tower.runTower(rc);     break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
