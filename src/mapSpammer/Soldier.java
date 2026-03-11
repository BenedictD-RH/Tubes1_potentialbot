package mapSpammer;

import battlecode.common.*;

public class Soldier {

    public static void runSoldier(RobotController rc) throws GameActionException {

        MapLocation myLoc = rc.getLocation();

        // LOW PAINT: find nearest tower and refill
        if (rc.getPaint() < 40) {
            for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
                if (ally.getType().isTowerType()) {
                    if (rc.canTransferPaint(ally.getLocation(), -50)) {
                        rc.transferPaint(ally.getLocation(), -50);
                    } else{
                        // Move toward tower if not adjacent
                        Direction toward = myLoc.directionTo(ally.getLocation());
                        if (rc.canMove(toward)) rc.move(toward);
                    }
                    
                    return;
                }
            }
        }

        for (RobotInfo ally : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (ally.getType().isTowerType()) {
                if (rc.canUpgradeTower(ally.getLocation())) {
                    rc.upgradeTower(ally.getLocation());
                    break;
                }
            }
        }

        // GREEDY: Scoring for each type of action
        MapLocation bestComplete = scoreBestComplete(rc, myLoc);
        MapLocation bestMark     = scoreBestMark(rc, myLoc);
        Direction   bestMove     = scoreBestMove(rc, myLoc, bestComplete, bestMark);

        // EXECUTE GREEDY ACTION
        executeComplete(rc, bestComplete);

        if (bestMove != null && rc.isMovementReady()) {
            rc.move(bestMove);
            myLoc = rc.getLocation();
        }

        executeAttackTower(rc, myLoc);

        if (rc.isActionReady() && rc.getPaint() >= 5) {
            MapInfo currentTile = rc.senseMapInfo(myLoc);
            if (currentTile.getPaint() == PaintType.EMPTY && rc.canAttack(myLoc)) {
                rc.attack(myLoc);
            }
        }

        executePaint(rc, myLoc);
        executeMark(rc, bestMark);
    }

    // SCORING ALGORITHM: Best complete
    private static MapLocation scoreBestComplete(RobotController rc, MapLocation myLoc) throws GameActionException {

        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        UnitType towerType = getTowerType(rc);

        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {

            if (rc.senseRobotAtLocation(ruin) != null) continue;
            if (!rc.canCompleteTowerPattern(towerType, ruin)) continue;

            int dist         = myLoc.distanceSquaredTo(ruin);
            int crowdPenalty = rc.senseNearbyRobots(ruin, 4, rc.getTeam()).length * 20;
            int score        = 1000 - dist - crowdPenalty;

            if (score > bestScore) {
                bestScore = score;
                best = ruin;
            }
        }
        return best;
    }

    // SCORING ALGORITHM: Best mark
    private static MapLocation scoreBestMark(RobotController rc, MapLocation myLoc) throws GameActionException {

        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        UnitType towerType = getTowerType(rc);

        for (MapLocation ruin : rc.senseNearbyRuins(-1)) {

            if (rc.senseRobotAtLocation(ruin) != null) continue;
            if (!rc.canMarkTowerPattern(towerType, ruin)) continue;

            int dist         = myLoc.distanceSquaredTo(ruin);
            int crowdPenalty = rc.senseNearbyRobots(ruin, 4, rc.getTeam()).length * 20;
            int score        = 500 - dist - crowdPenalty;

            if (score > bestScore) {
                bestScore = score;
                best = ruin;
            }
        }
        return best;
    }

    // SCORING ALGORITHM: Best move
    private static Direction scoreBestMove(RobotController rc, MapLocation myLoc,
                                           MapLocation bestComplete, MapLocation bestMark) throws GameActionException {

        if (!rc.isMovementReady()) return null;

        MapLocation targetRuin = bestComplete != null ? bestComplete
                               : bestMark    != null ? bestMark
                               : null;

        int unpaintedNearby = 0;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, 9)) {
            try {
                if (rc.senseMapInfo(loc).getPaint() == PaintType.EMPTY) unpaintedNearby++;
            } catch (Exception ignored) {}
        }

        Direction best = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction d : RobotPlayer.directions) {

            if (!rc.canMove(d)) continue;

            MapLocation next = myLoc.add(d);
            int score = 0;

            if (targetRuin != null) {
                int distNow  = myLoc.distanceSquaredTo(targetRuin);
                int distNext = next.distanceSquaredTo(targetRuin);
                score += (distNow - distNext) * 10;

                // Still prefer passing through empty tiles
                try {
                    MapInfo nextInfo = rc.senseMapInfo(next);
                    if (nextInfo.getPaint() == PaintType.EMPTY) score += 15;
                } catch (Exception ignored) {}

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
                MapInfo nextInfo = rc.senseMapInfo(next);
                if (nextInfo.getPaint() == PaintType.EMPTY) score += 40;

                // Small penalty for moving far from current spot
                score -= myLoc.distanceSquaredTo(next) * 2;
            } else {

                // No unpainted nearby --> find furthest empty tile direction
                MapLocation furthestEmpty = null;
                int furthestDist = Integer.MIN_VALUE;

                for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, GameConstants.VISION_RADIUS_SQUARED)) {
                    try {
                        MapInfo info = rc.senseMapInfo(loc);
                        if (info.getPaint() == PaintType.EMPTY && info.isPassable()) {
                            int dist = myLoc.distanceSquaredTo(loc);
                            if (dist > furthestDist) {
                                furthestDist = dist;
                                furthestEmpty = loc;
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (furthestEmpty != null) {

                    // Move toward furthest visible empty tile
                    int distNow  = myLoc.distanceSquaredTo(furthestEmpty);
                    int distNext = next.distanceSquaredTo(furthestEmpty);
                    score += (distNow - distNext) * 10;
                } else {

                    // Truly nothing visible --> push toward map center
                    int distNow  = myLoc.distanceSquaredTo(RobotPlayer.mapCenter);
                    int distNext = next.distanceSquaredTo(RobotPlayer.mapCenter);
                    score += (distNow - distNext) * 10;
                }

                // Strongly prefer non-ally tiles
                try {
                    MapInfo nextInfo = rc.senseMapInfo(next);
                    if (nextInfo.getPaint().isEnemy())               score += 30;
                    else if (nextInfo.getPaint() == PaintType.EMPTY) score += 20;
                } catch (Exception ignored) {}
            }

            score -= rc.senseNearbyRobots(next, 4, rc.getTeam()).length * 50;
            score += 3; // bias to always prefer moving

            if (score > bestScore) {
                bestScore = score;
                best = d;
            }
        }

        return best;
    }

    // EXECUTE: Complete tower pattern
    private static void executeComplete(RobotController rc, MapLocation bestComplete) throws GameActionException {
        UnitType towerType = getTowerType(rc);
        if (bestComplete != null && rc.canCompleteTowerPattern(towerType, bestComplete)) {
            rc.completeTowerPattern(towerType, bestComplete);
        }
    }

    // EXECUTE: Paint tiles
    private static void executePaint(RobotController rc, MapLocation myLoc) throws GameActionException {

        if (!rc.isActionReady() || rc.getPaint() < 5) return;

        MapLocation bestFill = null;
        int bestFillScore    = Integer.MIN_VALUE;
        boolean useSecondary = false;

        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, 9)) {

            if (!rc.canAttack(loc)) continue;

            MapInfo info = rc.senseMapInfo(loc);
            if (!info.isPassable()) continue;

            PaintType mark  = info.getMark();
            PaintType paint = info.getPaint();
            boolean sec     = false;
            int score       = 0;

            if (mark == PaintType.ALLY_PRIMARY) {
                if (paint == PaintType.ALLY_PRIMARY) continue;
                score += 300;
            } else if (mark == PaintType.ALLY_SECONDARY) {
                if (paint == PaintType.ALLY_SECONDARY) continue;
                sec    = true;
                score += 300;
            } else if (paint == PaintType.EMPTY) {
                score += 20;
            } else {
                continue;
            }

            score -= myLoc.distanceSquaredTo(loc);

            if (score > bestFillScore) {
                bestFillScore = score;
                bestFill      = loc;
                useSecondary  = sec;
            }
        }

        if (bestFill != null && rc.canAttack(bestFill)) {
            rc.attack(bestFill, useSecondary);
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

        if (!rc.isActionReady() || rc.getPaint() < 5) return;

        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        for (RobotInfo enemy : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {

            if (!enemy.getType().isTowerType()) continue;

            MapLocation loc = enemy.getLocation();
            if (!rc.canAttack(loc)) continue;

            int score = 0;
            score -= enemy.getHealth();              // prefer low HP towers
            score -= myLoc.distanceSquaredTo(loc);   // prefer closer towers

            if (score > bestScore) {
                bestScore = score;
                bestTarget = loc;
            }
        }

        if (bestTarget != null) {
            rc.attack(bestTarget);
        }
    }

    // HELPER: Determine which tower type to build based on current number of towers
    private static UnitType getTowerType(RobotController rc){
        int totalTowers = rc.getNumberTowers();
        if (totalTowers < 3) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (totalTowers < 6) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_DEFENSE_TOWER;
    }
}
