package mapSpammer;

import battlecode.common.*;

public class Mopper {

    public static void runMopper(RobotController rc) throws GameActionException {

        MapLocation myLoc = rc.getLocation();

        // GREEDY: Scoring
        Direction bestMove = scoreBestMove(rc, myLoc);

        // EXECUTE
        if (bestMove != null && rc.isMovementReady()) {
            rc.move(bestMove);
            myLoc = rc.getLocation();
        }

        executeMopSwing(rc, myLoc);
        executeTransfer(rc);
    }

    // SCORING: Closest enemy paint location
    private static MapLocation findClosestEnemyPaint(RobotController rc, MapLocation myLoc) throws GameActionException {
        MapLocation closest = null;
        int closestDist = Integer.MAX_VALUE;

        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, GameConstants.VISION_RADIUS_SQUARED)) {
            MapInfo info = rc.senseMapInfo(loc);
            if (info.getPaint().isEnemy()) {
                int d = myLoc.distanceSquaredTo(loc);
                if (d < closestDist) {
                    closestDist = d;
                    closest = loc;
                }
            }
        }
        return closest;
    }

    // SCORING: Best move
    private static Direction scoreBestMove(RobotController rc, MapLocation myLoc) throws GameActionException {

        if (!rc.isMovementReady()) return null;

        MapLocation closestEnemyPaint = findClosestEnemyPaint(rc, myLoc);

        Direction best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : RobotPlayer.directions) {

            if (!rc.canMove(d)) continue;

            MapLocation next = myLoc.add(d);
            int score = 0;

            if (closestEnemyPaint != null) {
                // Chase enemy paint
                int distNow  = myLoc.distanceSquaredTo(closestEnemyPaint);
                int distNext = next.distanceSquaredTo(closestEnemyPaint);
                score += (distNow - distNext) * 10;
            } else {
                // Exploration mode
                int distNow  = myLoc.distanceSquaredTo(RobotPlayer.mapCenter);
                int distNext = next.distanceSquaredTo(RobotPlayer.mapCenter);
                score += (distNow - distNext) * 10;

                try {
                    MapInfo nextInfo = rc.senseMapInfo(next);
                    PaintType nextPaint = nextInfo.getPaint();
                    if (nextPaint.isEnemy())               score += 30;
                    else if (nextPaint == PaintType.EMPTY) score += 15;
                } catch (Exception ignored) {}
            }

            score -= rc.senseNearbyRobots(next, 2, rc.getTeam()).length * 15;

            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }

        return best;
    }

    // EXECUTE: Mop swing
    private static void executeMopSwing(RobotController rc, MapLocation myLoc) throws GameActionException {

        if (!rc.isActionReady()) return;

        Direction bestSwing    = null;
        int bestSwingScore     = Integer.MIN_VALUE;

        Direction[] cardinals = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };

        for (Direction d : cardinals) {
            // mopSwing only works in 4 cardinal directions
            if (!rc.canMopSwing(d)) continue;

            int score = 0;
            // Count enemies in this swing direction (2 tiles deep, 3 wide)
            MapLocation step1 = myLoc.add(d);
            MapLocation step2 = step1.add(d);

            for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
                MapLocation eLoc = enemy.getLocation();
                if (eLoc.distanceSquaredTo(step1) <= 1 ||
                    eLoc.distanceSquaredTo(step2) <= 1) {
                    score += 50;
                }
            }

            if (score > bestSwingScore) {
                bestSwingScore = score;
                bestSwing = d;
            }
        }

        if (bestSwing != null && bestSwingScore > 0) {
            rc.mopSwing(bestSwing);
            return;
        }

        MapLocation bestFill = null;
        int bestFillScore = Integer.MIN_VALUE;

        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, 2)) {
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

    // EXECUTE: Transfer paint to nearby soldiers
    private static void executeTransfer(RobotController rc) throws GameActionException {
        if (rc.getPaint() < 20) return; // keep some for self

        for (RobotInfo ally : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (ally.getType() == UnitType.MOPPER) continue;
            if (ally.getPaintAmount() < 150) {
                if (rc.canTransferPaint(ally.getLocation(), 30)) {
                    rc.transferPaint(ally.getLocation(), 30);
                    return;
                }
            }
        }
    }
}
