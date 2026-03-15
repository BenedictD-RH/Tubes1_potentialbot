package towerRusher;

import battlecode.common.*;
public class RobotMovement {

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

    static boolean clockwiseRotation = true;

    static Direction[] pathfindStack = new Direction[8];

    static int stackCount = 0;

    private static boolean huggingWall = false;
    private static Direction wallDir = null;
    private static int bugStartDist = 0;
    private static MapLocation bugTarget = null;

    public static void addToPFS(Direction dir) {
        pathfindStack[stackCount] = dir;
        stackCount++;
    }

    public static void popFromPFS() {
        stackCount--;
    }

    private static void popAll() {
        stackCount = 0;
    }

    private static Direction topPathfindDir() {
        if (stackCount > 0) return pathfindStack[stackCount - 1];
        else return null;
    }

    // public static void smartMove(RobotController rc, MapLocation targetLocation) throws GameActionException {
    //     boolean fullstack = true;
    //     Direction dir1 = rc.getLocation().directionTo(targetLocation);
    //     while (fullstack) {
    //         fullstack = false;
    //         if (stackCount == 0) {
    //             if (rc.canMove(dir1)) {
    //                 rc.move(dir1);
    //                 return;
    //             }
    //             else {
    //                 addToPFS(dir1);
    //             }
    //         }
    //         else {
    //             if (rc.canMove(topPathfindDir())) {
    //                 rc.move(topPathfindDir());
    //                 popFromPFS();
    //                 return;
    //             }
    //         }
    //         Direction dirNow = topPathfindDir();
    //         while(!rc.canMove(dirNow)) {
    //             if (dirNow != topPathfindDir()) {
    //                 addToPFS(dirNow);
    //                 if (stackCount == 8) {
    //                     fullstack = true;
    //                     popAll();
    //                     break;
    //                 }
    //             }
    //             if (clockwiseRotation) dirNow = dirNow.rotateRight();
    //             else dirNow = dirNow.rotateLeft();
    //         }
    //         if (!fullstack) {
    //             rc.move(dirNow);
    //         }
    //     }
    // }
    // public static void smartMove(RobotController rc, MapLocation targetLocation) throws GameActionException {
    //     Direction dir1 = rc.getLocation().directionTo(targetLocation);
    //     int dir1Idx = 0;
    //     for (int i = 0; i < directions.length;i++) {
    //         if (dir1 == directions[i]) dir1Idx = i;
    //     }
    //     if (rc.canMove(dir1)) {
    //         rc.move(dir1);
    //     }
    //     else {
    //         int i = 1;
    //         while (!rc.canMove(directions[Math.floorMod((dir1Idx + i), directions.length)])) {
    //             if (i > 0) {
    //                 i*= -1;
    //             }
    //             else {
    //                 i = i* - 1 + 1;
    //             }
    //         }
    //         rc.move(directions[Math.floorMod((dir1Idx + i), directions.length)]);
    //     }
    // }
    public static void smartMove(RobotController rc, MapLocation targetLocation) throws GameActionException {
        Direction dir1 = rc.getLocation().directionTo(targetLocation);
        if (huggingWall) {
            bugNavigateTo(rc, targetLocation);
        }
        if (rc.canMove(dir1)) {
            rc.move(dir1);
        }
        else {
            if (rc.canMove(dir1.rotateLeft())) {
                rc.move(dir1.rotateLeft());
            }
            else if (rc.canMove(dir1.rotateRight())) {
                rc.move(dir1.rotateRight());
            }
            else {
                bugNavigateTo(rc, targetLocation);
            }
        }
    }

    public static void bugNavigateTo(RobotController rc, MapLocation target) throws GameActionException {
        if (!rc.isMovementReady() || target == null) return;
        MapLocation myLoc = rc.getLocation();
        if (myLoc.equals(target)) return;

        // Auto-reset saat target berubah
        if (bugTarget == null || !bugTarget.equals(target)) {
            huggingWall = false;
            bugTarget = target;
        }

        Direction dir = myLoc.directionTo(target);
        if (rc.canMove(dir)) {
            huggingWall = false;
            rc.move(dir);
        } else {
            if (!huggingWall) {
                huggingWall = true;
                wallDir = dir;
                bugStartDist = myLoc.distanceSquaredTo(target);
            }
            for (int i = 0; i < 8; i++) {
                wallDir = wallDir.rotateRight();
                if (rc.canMove(wallDir)) {
                    rc.move(wallDir);
                    break;
                }
            }
            MapLocation newLoc = rc.getLocation();
            Direction nd = newLoc.directionTo(target);
            if (rc.canMove(nd) && newLoc.distanceSquaredTo(target) < bugStartDist) {
                huggingWall = false;
            }
        }
    }

    public static void mopperMove(RobotController rc, MapLocation targetLocation) throws GameActionException {
        Direction dir1 = rc.getLocation().directionTo(targetLocation);
        Direction dirNow = dir1;
        int n = 0;
        while (!rc.senseMapInfo(rc.getLocation().add(dirNow)).getPaint().isEnemy() && n < 8) {
            dirNow = dirNow.rotateRight();
            n++;
            while (!rc.onTheMap(rc.getLocation().add(dirNow)) && n < 8) {
                dirNow = dirNow.rotateRight();
                n++;
            }
        }
        if (n == 8) {
            smartMove(rc, rc.getLocation().add(dir1));
        }
        else {
            smartMove(rc, rc.getLocation().add(dirNow));
        }
    }
}
