package alternative_bots_2;

import battlecode.common.*;

public class Splasher {

    public static void runSplasher(RobotController rc) throws GameActionException {

        MapLocation myLoc = rc.getLocation();

        // GREEDY: Scoring
        MapLocation bestComplete = scoreBestComplete(rc, myLoc);
        MapLocation bestMark     = scoreBestMark(rc, myLoc);
        MapLocation bestSplash   = scoreBestSplash(rc, myLoc);
        Direction   bestMove     = scoreBestMove(rc, myLoc, bestComplete, bestMark, bestSplash);

        // EXECUTE
        executeComplete(rc, bestComplete);

        if (bestMove != null && rc.isMovementReady()) {
            rc.move(bestMove);
            myLoc = rc.getLocation();
        }

        executeAttackTower(rc, myLoc);
        executeSplash(rc, myLoc, bestSplash);
        executeMark(rc, bestMark);
    }

    // SCORING: Best splash target
    private static MapLocation scoreBestSplash(RobotController rc, MapLocation myLoc) throws GameActionException {

        if (!rc.isActionReady() || rc.getPaint() < 50) return null;

        MapLocation best = null;
        int bestScore    = Integer.MIN_VALUE;

        // Splash center can be up to 2 tiles away
        for (MapLocation center : rc.getAllLocationsWithinRadiusSquared(myLoc, 4)) {

            if (!rc.canAttack(center)) continue;

            int score        = 0;
            int enemyCount   = 0;
            int emptyCount   = 0;
            int allyCount    = 0;

            // Count tiles within splash radius (2) around center
            for (MapLocation splash : rc.getAllLocationsWithinRadiusSquared(center, 2)) {
                try {
                    MapInfo info = rc.senseMapInfo(splash);
                    if (!info.isPassable()) continue;
                    PaintType paint = info.getPaint();
                    if (paint.isEnemy())               enemyCount++;
                    else if (paint == PaintType.EMPTY) emptyCount++;
                    else if (paint.isAlly())           allyCount++;
                } catch (Exception ignored) {}
            }

            // Only splash if hitting at least 6 useful tiles
            int usefulTiles = enemyCount + emptyCount;
            if (usefulTiles < 6) continue;

            score += enemyCount * 40;  // enemy tiles worth more (overwrites enemy paint)
            score += emptyCount * 20;  // empty tiles worth less
            score -= allyCount  * 10;  // penalty for wasting paint on ally tiles
            score -= myLoc.distanceSquaredTo(center); // prefer closer centers

            if (score > bestScore) {
                bestScore = score;
                best = center;
            }
        }

        return best;
    }

    // SCORING: Best complete
    private static MapLocation scoreBestComplete(RobotController rc, MapLocation myLoc) throws GameActionException {

        MapLocation best = null;
        int bestScore    = Integer.MIN_VALUE;
        UnitType towerType = getTowerType(rc);

        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            if (rc.senseRobotAtLocation(ruin) != null) continue;
            if (!rc.canCompleteTowerPattern(towerType, ruin)) continue;

            int score = 1000 - myLoc.distanceSquaredTo(ruin)
                             - rc.senseNearbyRobots(ruin, 4, rc.getTeam()).length * 20;
            if (score > bestScore) { bestScore = score; best = ruin; }
        }
        return best;
    }

    // SCORING: Best mark
    private static MapLocation scoreBestMark(RobotController rc, MapLocation myLoc) throws GameActionException {

        MapLocation best = null;
        int bestScore    = Integer.MIN_VALUE;
        UnitType towerType = getTowerType(rc);

        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {
            if (rc.senseRobotAtLocation(ruin) != null) continue;
            if (!rc.canMarkTowerPattern(towerType, ruin)) continue;

            int score = 1000 - myLoc.distanceSquaredTo(ruin)
                             - rc.senseNearbyRobots(ruin, 4, rc.getTeam()).length * 20;
            if (score > bestScore) { bestScore = score; best = ruin; }
        }
        return best;
    }

    // SCORING: Best move
    private static Direction scoreBestMove(RobotController rc, MapLocation myLoc,
                                           MapLocation bestComplete, MapLocation bestMark,
                                           MapLocation bestSplash) throws GameActionException {

        if (!rc.isMovementReady()) return null;

        // Priority: complete > splash target > mark > explore
        MapLocation targetRuin = bestComplete != null ? bestComplete
                               : bestMark    != null ? bestMark
                               : null;

        int unpaintedNearby = 0;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, 9)) {
            try { if (rc.senseMapInfo(loc).getPaint() == PaintType.EMPTY) unpaintedNearby++;}
            catch (Exception ignored) {}
        }

        Direction best      = null;
        int bestScore       = Integer.MIN_VALUE;

        for (Direction d : RobotPlayer.directions) {
            if (!rc.canMove(d)) continue;

            MapLocation next  = myLoc.add(d);
            int score         = 0;

            if (targetRuin != null) {
                score += (myLoc.distanceSquaredTo(targetRuin) - next.distanceSquaredTo(targetRuin)) * 10;
                try { if (rc.senseMapInfo(next).getPaint() == PaintType.EMPTY) score += 15; }
                catch (Exception ignored) {}

            } else if (bestSplash != null) {
                // Move toward best splash center
                int distNow  = myLoc.distanceSquaredTo(bestSplash);
                int distNext = next.distanceSquaredTo(bestSplash);
                score += (distNow - distNext) * 15; // higher weight than ruin

            } else if (unpaintedNearby > 2) {
                int emptyCount = 0;
                MapLocation check = next;
                for (int i = 0; i < 3; i++) {
                    try {
                        if (rc.senseMapInfo(check).getPaint() == PaintType.EMPTY) emptyCount++;
                        check = check.add(d);
                    } catch (Exception ignored) { break; }
                }
                score += emptyCount * 15;
                try { if (rc.senseMapInfo(next).getPaint() == PaintType.EMPTY) score += 40; }
                catch (Exception ignored) {}
                score -= myLoc.distanceSquaredTo(next) * 2;

            } else {
                MapLocation furthestEmpty = null;
                int furthestDist          = Integer.MIN_VALUE;

                for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, GameConstants.VISION_RADIUS_SQUARED)) {
                    try {
                        MapInfo info = rc.senseMapInfo(loc);
                        if (info.getPaint() == PaintType.EMPTY && info.isPassable()) {
                            int dist = myLoc.distanceSquaredTo(loc);
                            if (dist > furthestDist) { furthestDist = dist; furthestEmpty = loc; }
                        }
                    } catch (Exception ignored) {}
                }

                if (furthestEmpty != null) {
                    int distNow  = myLoc.distanceSquaredTo(furthestEmpty);
                    int distNext = next.distanceSquaredTo(furthestEmpty);
                    score += (distNow - distNext) * 10;
                } else {
                    int distNow  = myLoc.distanceSquaredTo(RobotPlayer.mapCenter);
                    int distNext = next.distanceSquaredTo(RobotPlayer.mapCenter);
                    score += (distNow - distNext) * 10;
                }

                try {
                    MapInfo nextInfo = rc.senseMapInfo(next);
                    if (nextInfo.getPaint().isEnemy())               score += 30;
                    else if (nextInfo.getPaint() == PaintType.EMPTY) score += 20;
                } catch (Exception ignored) {}
            }

            score -= rc.senseNearbyRobots(next, 4, rc.getTeam()).length * 50;
            score += 3;

            if (score > bestScore) { bestScore = score; best = d; }
        }
        return best;
    }

    // EXECUTE: Splash attack
    private static void executeSplash(RobotController rc, MapLocation myLoc,
                                      MapLocation bestSplash) throws GameActionException {

        if (!rc.isActionReady() || rc.getPaint() < 50) return;
        if (bestSplash == null) return;

        if (rc.canAttack(bestSplash)) {
            rc.attack(bestSplash);
        }
    }

    // EXECUTE: Complete tower pattern
    private static void executeComplete(RobotController rc, MapLocation bestComplete) throws GameActionException {
        UnitType towerType = getTowerType(rc);
        if (bestComplete != null && rc.canCompleteTowerPattern(towerType, bestComplete)) {
            rc.completeTowerPattern(towerType, bestComplete);
        }
    }

    // EXECUTE: Mark tower pattern
    private static void executeMark(RobotController rc, MapLocation bestMark) throws GameActionException {
        UnitType towerType = getTowerType(rc);
        if (bestMark != null && rc.canMarkTowerPattern(towerType, bestMark)) {
            rc.markTowerPattern(towerType, bestMark);
        }
    }

    // EXECUTE: Attack enemy tower
    private static void executeAttackTower(RobotController rc, MapLocation myLoc) throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() < 50) return;

        MapLocation best = null;
        int bestScore    = Integer.MIN_VALUE;

        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (!enemy.getType().isTowerType()) continue;
            if (!rc.canAttack(enemy.getLocation())) continue;

            int score = -enemy.getHealth() - myLoc.distanceSquaredTo(enemy.getLocation());
            if (score > bestScore) { bestScore = score; best = enemy.getLocation(); }
        }

        if (best != null) rc.attack(best);
    }

    // HELPER: Tower type
    private static UnitType getTowerType(RobotController rc) {
        int total = rc.getNumberTowers();
        if (total < 3) return UnitType.LEVEL_ONE_PAINT_TOWER; // get 2 paint (start with 1)
        if (total < 4) return UnitType.LEVEL_ONE_MONEY_TOWER; // get 2 money (start with 1)
        return UnitType.LEVEL_ONE_DEFENSE_TOWER;              // rest are defense
    }
}