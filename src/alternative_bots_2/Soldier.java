package alternative_bots_2;

import battlecode.common.*;

public class Soldier {

    public static void runSoldier(RobotController rc) throws GameActionException {

        MapLocation myLoc = rc.getLocation();

        // Upgrade adjacent tower if possible
        for (RobotInfo ally : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (ally.getType().isTowerType() && rc.canUpgradeTower(ally.getLocation())) {
                rc.upgradeTower(ally.getLocation());
                break;
            }
        }

        // GREEDY: Scoring
        MapLocation bestComplete = scoreBestComplete(rc, myLoc);
        MapLocation bestMark     = scoreBestMark(rc, myLoc);
        Direction   bestMove     = scoreBestMove(rc, myLoc, bestComplete, bestMark);

        // EXECUTE
        executeComplete(rc, bestComplete);

        if (bestMove != null && rc.isMovementReady()) {
            rc.move(bestMove);
            myLoc = rc.getLocation();
        }

        executeAttackTower(rc, myLoc);

        if (rc.isActionReady() && rc.getPaint() >= 5) {
            MapInfo cur = rc.senseMapInfo(myLoc);
            if (cur.getPaint() == PaintType.EMPTY && rc.canAttack(myLoc))
                rc.attack(myLoc);
        }

        executePaint(rc, myLoc);
        executeMark(rc, bestMark);
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

            int score = 500 - myLoc.distanceSquaredTo(ruin)
                            - rc.senseNearbyRobots(ruin, 4, rc.getTeam()).length * 20;
            if (score > bestScore) { bestScore = score; best = ruin; }
        }
        return best;
    }

    // SCORING: Best move
    private static Direction scoreBestMove(RobotController rc, MapLocation myLoc,
                                           MapLocation bestComplete, MapLocation bestMark) throws GameActionException {

        if (!rc.isMovementReady()) return null;

        // Priority: complete > mark > explore
        MapLocation targetRuin = bestComplete != null ? bestComplete
                               : bestMark    != null ? bestMark 
                               : null;

        int unpaintedNearby = 0;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, 9)) {
            try { if (rc.senseMapInfo(loc).getPaint() == PaintType.EMPTY) unpaintedNearby++; }
            catch (Exception ignored) {}
        }

        Direction best = null;
        int bestScore  = Integer.MIN_VALUE;

        for (Direction d : RobotPlayer.directions) {
            if (!rc.canMove(d)) continue;

            MapLocation next = myLoc.add(d);
            int score        = 0;

            if (targetRuin != null) {
                score += (myLoc.distanceSquaredTo(targetRuin) - next.distanceSquaredTo(targetRuin)) * 10;
                try { if (rc.senseMapInfo(next).getPaint() == PaintType.EMPTY) score += 15; }
                catch (Exception ignored) {}

            } else if (unpaintedNearby > 2) {
                MapLocation check = next;
                for (int i = 0; i < 3; i++) {
                    try {
                        if (rc.senseMapInfo(check).getPaint() == PaintType.EMPTY) score += 15;
                        check = check.add(d);
                    } catch (Exception ignored) { break; }
                }
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

                MapLocation anchor = furthestEmpty != null ? furthestEmpty : RobotPlayer.mapCenter;
                score += (myLoc.distanceSquaredTo(anchor) - next.distanceSquaredTo(anchor)) * 10;

                try {
                    PaintType p = rc.senseMapInfo(next).getPaint();
                    if (p.isEnemy())               score += 30;
                    else if (p == PaintType.EMPTY) score += 20;
                } catch (Exception ignored) {}
            }

            score -= rc.senseNearbyRobots(next, 4, rc.getTeam()).length * 50;
            score += 3;

            if (score > bestScore) { bestScore = score; best = d; }
        }
        return best;
    }

    // EXECUTE: Complete tower pattern
    private static void executeComplete(RobotController rc, MapLocation best) throws GameActionException {
        UnitType towerType = getTowerType(rc);
        if (best != null && rc.canCompleteTowerPattern(towerType, best))
            rc.completeTowerPattern(towerType, best);
    }

    // EXECUTE: Paint tiles
    private static void executePaint(RobotController rc, MapLocation myLoc) throws GameActionException {

        if (!rc.isActionReady() || rc.getPaint() < 5) return;

        MapLocation best = null;
        int bestScore    = Integer.MIN_VALUE;
        boolean useSec   = false;

        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(myLoc, 9)) {
            if (!rc.canAttack(loc)) continue;

            MapInfo info    = rc.senseMapInfo(loc);
            if (!info.isPassable()) continue;

            PaintType mark  = info.getMark();
            PaintType paint = info.getPaint();
            boolean sec     = false;
            int score       = 0;

            if      (mark == PaintType.ALLY_PRIMARY   && paint != PaintType.ALLY_PRIMARY)   { score += 300; }
            else if (mark == PaintType.ALLY_SECONDARY && paint != PaintType.ALLY_SECONDARY) { score += 300; sec = true; }
            else if (paint == PaintType.EMPTY)                                               { score += 20; }
            else continue;

            score -= myLoc.distanceSquaredTo(loc);
            if (score > bestScore) { bestScore = score; best = loc; useSec = sec; }
        }

        if (best != null && rc.canAttack(best)) rc.attack(best, useSec);
    }

    // EXECUTE: Mark tower
    private static void executeMark(RobotController rc, MapLocation best) throws GameActionException {
        UnitType towerType = getTowerType(rc);
        if (best != null && rc.canMarkTowerPattern(towerType, best))
            rc.markTowerPattern(towerType, best);
    }

    // EXECUTE: Attack enemy tower
    private static void executeAttackTower(RobotController rc, MapLocation myLoc) throws GameActionException {
        if (!rc.isActionReady() || rc.getPaint() < 5) return;

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
        if (total < 3) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (total < 4) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_DEFENSE_TOWER;
    }
}