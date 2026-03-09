package mapSpammer;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
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

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")

    static MapLocation mapCenter;

    public static void run(RobotController rc) throws GameActionException {

        if (mapCenter == null) {
            mapCenter = new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2);
        }

        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{

        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        int soldierCount = 0;
        int mopperCount = 0;
        for (RobotInfo r : nearbyAllies) {
            if (r.getType() == UnitType.SOLDIER) soldierCount++;
            else if (r.getType() == UnitType.MOPPER) mopperCount++;
        }

        UnitType toBuild = null;

        if (soldierCount < 3) {
            toBuild = UnitType.SOLDIER;
        } else if (mopperCount < 3) {
            toBuild = UnitType.MOPPER;
        }

        if (toBuild != null) {
            for (Direction d : directions) {
                MapLocation loc = rc.getLocation().add(d);
                if (rc.canBuildRobot(toBuild, loc)) {
                    rc.buildRobot(toBuild, loc);
                    break;
                }
            }
        }
    }


    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */

    public static void runSoldier(RobotController rc) throws GameActionException{

        MapLocation myLoc = rc.getLocation();

        // MapLocation bestAttack = null;
        // int bestAttackScore = -9999;

        MapLocation bestComplete = null;
        int bestCompleteScore = -9999;
        
        MapLocation bestMark = null;
        int bestMarkScore = -9999;
        
        Direction bestMove = null;
        int bestMoveScore = -9999;

        MapLocation[] ruins = rc.senseNearbyRuins(-1);

        // =========================
        // GREEDY: RUIN TARGET
        // =========================
        for (MapLocation ruin : ruins) {

            if (rc.senseRobotAtLocation(ruin) != null) continue;

            int dist = myLoc.distanceSquaredTo(ruin);

            RobotInfo[] robotsNear = rc.senseNearbyRobots(ruin, 20, rc.getTeam());
            int crowdPenalty = robotsNear.length * 80;

            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin)) {
                int score = 1000 - dist - crowdPenalty;
                if (score > bestCompleteScore) {
                    bestCompleteScore = score;
                    bestComplete = ruin;
                }
            }

            if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin)) {
                int score = 500 - dist - crowdPenalty;
                if (score > bestMarkScore) {
                    bestMarkScore = score;
                    bestMark = ruin;
                }
            }     
        }

        // =========================
        // GREEDY: ATTACK / PAINT
        // =========================
        MapLocation targetRuin;

        if (bestComplete != null) {
            targetRuin = bestComplete;
        } 
        else if (bestMark != null) {
            targetRuin = bestMark;
        } 
        else {
            targetRuin = null;
        }

        // =========================
        // GREEDY: MOVEMENT
        // =========================
        if (rc.isMovementReady()) {

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;

                MapLocation next = myLoc.add(d);
                int score = 0;

                // mendekat ke ruin
                if (targetRuin != null) {
                    int distNow  = myLoc.distanceSquaredTo(targetRuin);
                    int distNext = next.distanceSquaredTo(targetRuin);
                    score += (distNow - distNext) * 10;
                    if (distNext <= 2) score -= 30;
                } else {
                    // Exploration: prefer tiles we haven't seen (less paint)
                    MapInfo nextInfo = rc.senseMapInfo(next);
                    if (nextInfo.getPaint() == PaintType.EMPTY) score += 5;
                    // Small random nudge to break ties
                    // score += (rc.getID() + d.ordinal()) % 4;
                }

                // penalti jika robot lain dekat
                RobotInfo[] nearby = rc.senseNearbyRobots(next, 4, rc.getTeam());
                score -= nearby.length * 25;

                score += 3;

                if (score > bestMoveScore) {
                    bestMoveScore = score;
                    bestMove = d;
                }
            }
        }

        // =========================
        // EXECUTE GREEDY ACTION
        // =========================
        if (bestComplete != null && rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, bestComplete)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, bestComplete);
        }

        if (bestMove != null && rc.isMovementReady()) {
            rc.move(bestMove);
            myLoc = rc.getLocation();
        }

        if (rc.isActionReady() && rc.getPaint() >= 5) {

            MapLocation[] tiles = rc.getAllLocationsWithinRadiusSquared(myLoc, 9);

            MapLocation bestFill = null;
            int bestFillScore = Integer.MIN_VALUE;
            boolean bestFillSecondary = false;


            for (MapLocation loc : tiles) {

                if (!rc.canAttack(loc)) continue;

                MapInfo info = rc.senseMapInfo(loc);

                if (!info.isPassable()) continue;

                int score = 0;
                PaintType mark = info.getMark();
                PaintType paint = info.getPaint();

                boolean useSecondary = false;

                if (mark == PaintType.ALLY_PRIMARY) {
                    if (paint == PaintType.ALLY_PRIMARY) continue;
                    useSecondary = false;
                    score += 300;
                } else if (mark == PaintType.ALLY_SECONDARY) {
                    if (paint == PaintType.ALLY_SECONDARY) continue;
                    useSecondary = true;
                    score += 300;
                } else if (paint.isEnemy()) {
                    useSecondary = false;
                    score += 150;
                } else if (paint == PaintType.EMPTY) {
                    useSecondary = false;
                    score += 20;
                } else {
                    continue;
                }

                score -= rc.getLocation().distanceSquaredTo(loc);

                if (score > bestFillScore) {
                    bestFillScore = score;
                    bestFill = loc;
                    bestFillSecondary = useSecondary;
                }
            }

            if (bestFill != null && rc.canAttack(bestFill)) {
                rc.attack(bestFill, bestFillSecondary);
            }
        }

        if (bestMark != null && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, bestMark)) {
            rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, bestMark);
        }        
    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException{
        MapLocation myLoc = rc.getLocation();

        Direction bestMove = null;
        int bestMoveScore = -9999;

        MapLocation bestAttack = null;
        int bestAttackScore = -9999;
        boolean bestAttackSplash = false;

        // =========================
        // GREEDY: ATTACK TARGET
        // =========================
        if (rc.isActionReady()) {
            MapLocation[] tiles = rc.getAllLocationsWithinRadiusSquared(myLoc, 9);
            for (MapLocation loc : tiles) {

                if (!rc.canAttack(loc)) continue;

                MapInfo info = rc.senseMapInfo(loc);

                if (!info.isPassable()) continue;

                PaintType paint = info.getPaint();

                int score = 0;

                if (paint.isEnemy()) {
                    score += 200;

                    // Bonus: count nearby enemy tiles for splash value
                    MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(loc, 1);
                    for (MapLocation n : nearby) {
                        try {
                            MapInfo nInfo = rc.senseMapInfo(n);
                            if (nInfo.getPaint().isEnemy()) score += 30;
                        } catch (Exception ignored) {}
                    }
                } else {
                    continue;
                }

                score -= myLoc.distanceSquaredTo(loc);

                if (score > bestAttackScore) {
                    bestAttackScore = score;
                    bestAttack = loc;
                }
            }
        }
        // =========================
        // GREEDY: MOVEMENT
        // =========================
        if (rc.isMovementReady()) {

            // Find closest enemy paint tile to chase
            MapLocation closestEnemyPaint = null;
            int closestDist = 10000;

            MapLocation[] allTiles = rc.getAllLocationsWithinRadiusSquared(myLoc, GameConstants.VISION_RADIUS_SQUARED);
            for (MapLocation loc : allTiles) {
                try {
                    MapInfo info = rc.senseMapInfo(loc);
                    if (info.getPaint().isEnemy()) {
                        int d = myLoc.distanceSquaredTo(loc);
                        if (d < closestDist) {
                            closestDist = d;
                            closestEnemyPaint = loc;
                        }
                    }
                } catch (Exception ignored) {}
            }

            for (Direction d : directions) {

                if (!rc.canMove(d)) continue;

                MapLocation next = myLoc.add(d);
                int score = 0;

                if (closestEnemyPaint != null) {
                    // Chase enemy paint
                    int distNow  = myLoc.distanceSquaredTo(closestEnemyPaint);
                    int distNext = next.distanceSquaredTo(closestEnemyPaint);
                    score += (distNow - distNext) * 10;
                } else {
                    // Explore: prefer unpainted tiles
                    try {
                        MapInfo nextInfo = rc.senseMapInfo(next);
                        if (nextInfo.getPaint() == PaintType.EMPTY) score += 5;
                    } catch (Exception ignored) {}
                    // score += rc.getRoundNum() % 3 == 0 ? (d.ordinal() % 3) : 0;
                }

                // Avoid crowding teammates
                RobotInfo[] nearby = rc.senseNearbyRobots(next, 2, rc.getTeam());
                score -= nearby.length * 15;

                if (score > bestMoveScore) {
                    bestMoveScore = score;
                    bestMove = d;
                }
            }
        }

        // =========================
        // EXECUTE
        // =========================

        // Move first for better positioning
        if (bestMove != null && rc.isMovementReady()) {
            rc.move(bestMove);
            myLoc = rc.getLocation();
        }

        // Then attack
        if (rc.isActionReady()) {
            // Re-scan after moving
            MapLocation bestFill = null;
            int bestFillScore = Integer.MIN_VALUE;

            for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, 9)) {
                if (!rc.canAttack(loc)) continue;
                MapInfo info = rc.senseMapInfo(loc);
                if (!info.isPassable()) continue;
                if (!info.getPaint().isEnemy()) continue;

                int score = 200;

                // Splash bonus: reward hitting clusters
                for (MapLocation n : rc.getAllLocationsWithinRadiusSquared(loc, 1)) {
                    try {
                        if (rc.senseMapInfo(n).getPaint().isEnemy()) score += 30;
                    } catch (Exception ignored) {}
                }

                score -= myLoc.distanceSquaredTo(loc);

                if (score > bestFillScore) {
                    bestFillScore = score;
                    bestFill = loc;
                }
            }

            if (bestFill != null && rc.canAttack(bestFill)) {
                rc.attack(bestFill);
            }
        }
    }
}
