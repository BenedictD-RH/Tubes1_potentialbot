package alternative_bots_2;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Unit extends BaseRobot {

    
    public static MapLocation spawnLocation;
    public static Direction currentExploreDirection = null;

    
    public static MapLocation[] knownPaintTowers = new MapLocation[25];
    public static int knownPaintTowerCount = 0;

    
    public static MapLocation[] knownAllyTowers = new MapLocation[25];
    public static int knownAllyTowerCount = 0;

    
    public static MapLocation[] knownRuins = new MapLocation[30];
    public static int knownRuinCount = 0;

    
    public static MapLocation returnLocation = null;

    
    public static MapLocation lastKnownEnemyLoc = null;
    public static int lastKnownEnemyRound = -1;

    
    public static void initUnit() {
        if (spawnLocation == null) {
            spawnLocation = rc.getLocation();
        }
    }

    
    
    

    
    public static void moveGreedy(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation nextLoc = myLoc.add(dir);
            double score = evaluateTile(nextLoc, target);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
        }
    }

    
    private static double evaluateTile(MapLocation loc, MapLocation target) throws GameActionException {
        double score = 0;

        
        int distToTarget = loc.distanceSquaredTo(target);
        score -= distToTarget * 2.0;

        
        if (rc.canSenseLocation(loc)) {
            MapInfo info = rc.senseMapInfo(loc);
            PaintType paint = info.getPaint();

            if (paint.isAlly()) {
                score += 8; 
            } else if (paint == PaintType.EMPTY) {
                score += 2; 
            } else {
                
                score -= 10;
            }

            
            if (isNearEnemyTower(loc)) {
                score -= 30; 
            }
        }

        return score;
    }

    
    private static boolean isNearEnemyTower(MapLocation loc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(loc, 9, rc.getTeam().opponent());
        for (RobotInfo enemy : nearbyEnemies) {
            if (isTowerType(enemy.getType())) {
                return true;
            }
        }
        return false;
    }

    
    public static void moveSimple(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int minDist = myLoc.distanceSquaredTo(target);

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = myLoc.add(dir);
            int dist = next.distanceSquaredTo(target);
            if (dist < minDist) {
                minDist = dist;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
        }
    }

    
    
    

    
    public static boolean tryBuildTower() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        
        MapLocation targetRuin = null;
        int minDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();

            
            if (rc.canSenseRobotAtLocation(ruinLoc)) {
                RobotInfo robot = rc.senseRobotAtLocation(ruinLoc);
                if (robot != null && isTowerType(robot.getType())) {
                    continue;
                }
            }

            int dist = myLoc.distanceSquaredTo(ruinLoc);
            if (dist < minDistance) {
                minDistance = dist;
                targetRuin = ruinLoc;
            }
        }

        if (targetRuin == null) return false;

        
        UnitType towerToBuild = chooseTowerType();
        int requiredChips = (towerToBuild == UnitType.LEVEL_ONE_DEFENSE_TOWER) ? 2500 : 1000;

        
        if (rc.getMoney() < requiredChips) {
            
            if (myLoc.distanceSquaredTo(targetRuin) <= 2 && rc.getMoney() >= requiredChips - 300) {
                return true; 
            }
            return false;
        }

        
        if (rc.canCompleteTowerPattern(towerToBuild, targetRuin)) {
            rc.completeTowerPattern(towerToBuild, targetRuin);
            
            if (isPaintTower(towerToBuild)) {
                addKnownPaintTower(targetRuin);
            }
            addKnownAllyTower(targetRuin);
            return true;
        }

        
        if (rc.canMarkTowerPattern(towerToBuild, targetRuin)) {
            rc.markTowerPattern(towerToBuild, targetRuin);
        }

        
        if (myLoc.distanceSquaredTo(targetRuin) > 2) {
            if (rc.isMovementReady()) {
                moveGreedy(targetRuin);
                return true;
            }
        }

        
        paintMarkedTilesAround(targetRuin);

        return true;
    }

    
    private static UnitType chooseTowerType() throws GameActionException {
        int totalTowers = rc.getNumberTowers();
        int chips = rc.getMoney();
        int round = rc.getRoundNum();

        
        if (totalTowers < 2) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        
        int paintCount = 0;
        int moneyCount = 0;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) paintCount++;
            else if (isMoneyTower(ally.getType())) moneyCount++;
        }

        
        if (totalTowers < 4) {
            if (paintCount == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        if (totalTowers < 7) {
            
            if (paintCount <= moneyCount) return UnitType.LEVEL_ONE_PAINT_TOWER;
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        
        if (totalTowers >= 10 && chips > 2000) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        
        if (chips > 2000) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    
    private static void paintMarkedTilesAround(MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(center, 8);
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            PaintType mark = tile.getMark();
            PaintType paint = tile.getPaint();

            
            if (mark != PaintType.EMPTY && mark != paint && tile.isPassable()) {
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                if (rc.canAttack(loc)) {
                    rc.attack(loc, useSecondary);
                    return;
                }
            }
        }
    }

    
    
    

    
    public static boolean shouldBuildSRP() throws GameActionException {
        int totalTowers = rc.getNumberTowers();
        int chips = rc.getMoney();
        int round = rc.getRoundNum();

        
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();
        int minTowers = Math.max(4, 6 - Math.min(mapWidth, mapHeight) / 10);

        return totalTowers >= minTowers && chips >= 500 && round > EARLY_GAME_END;
    }

    
    public static boolean tryBuildSRP() throws GameActionException {
        if (!shouldBuildSRP()) return false;

        MapLocation myLoc = rc.getLocation();

        
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                return true;
            }
        }

        
        if (rc.canMarkResourcePattern(myLoc)) {
            rc.markResourcePattern(myLoc);
            return true;
        }

        return false;
    }

    
    
    

    
    public static boolean refillPaint() throws GameActionException {
        int paintCapacity = rc.getType().paintCapacity;
        int currentPaint = rc.getPaint();
        int paintPercent = (currentPaint * 100) / paintCapacity;

        if (paintPercent > REFILL_PAINT_PERCENT) return false;

        
        MapLocation nearestPaintTower = findNearestPaintTower();

        
        if (nearestPaintTower == null) {
            nearestPaintTower = findNearestKnownPaintTower();
        }

        
        if (nearestPaintTower == null && spawnLocation != null) {
            nearestPaintTower = spawnLocation;
        }

        if (nearestPaintTower == null) return false;

        int dist = rc.getLocation().distanceSquaredTo(nearestPaintTower);

        if (dist <= 2) {
            
            withdrawPaint(nearestPaintTower);
        } else {
            
            if (rc.isMovementReady()) {
                moveGreedy(nearestPaintTower);
            }
        }

        return true;
    }

    
    private static MapLocation findNearestPaintTower() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) {
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = ally.getLocation();
                }
                
                addKnownPaintTower(ally.getLocation());
            }
            
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
            }
        }
        return nearest;
    }

    
    private static MapLocation findNearestKnownPaintTower() {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (int i = 0; i < knownPaintTowerCount; i++) {
            int dist = myLoc.distanceSquaredTo(knownPaintTowers[i]);
            if (dist < minDist) {
                minDist = dist;
                nearest = knownPaintTowers[i];
            }
        }
        return nearest;
    }

    
    private static void withdrawPaint(MapLocation towerLoc) throws GameActionException {
        if (!rc.isActionReady()) return;

        int needed = rc.getType().paintCapacity - rc.getPaint();
        if (needed <= 0) return;

        if (rc.canSenseRobotAtLocation(towerLoc)) {
            RobotInfo tower = rc.senseRobotAtLocation(towerLoc);
            if (tower != null && tower.getTeam() == rc.getTeam()) {
                int available = tower.getPaintAmount();
                int transfer = Math.min(needed, available);
                if (transfer > 0 && rc.canTransferPaint(towerLoc, -transfer)) {
                    rc.transferPaint(towerLoc, -transfer);
                }
            }
        }
    }

    
    
    

    
    public static void broadcastEnemyLocation(MapLocation enemyLoc) throws GameActionException {
        int msg = encodeMessage(MSG_ENEMY_LOC, enemyLoc.x, enemyLoc.y, rc.getRoundNum() % EXTRA_MASK);

        
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return; 
                }
            }
        }
    }

    
    public static void broadcastEnemyTower(MapLocation towerLoc) throws GameActionException {
        int msg = encodeMessage(MSG_ENEMY_TOWER, towerLoc.x, towerLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    
    public static void requestMopper(MapLocation loc) throws GameActionException {
        int msg = encodeMessage(MSG_MOPPER_REQ, loc.x, loc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    
    public static void broadcastRuin(MapLocation ruinLoc) throws GameActionException {
        int msg = encodeMessage(MSG_RUIN_LOC, ruinLoc.x, ruinLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    
    public static MapLocation readBroadcastTarget(int msgType) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        if (messages.length == 0) return null;

        MapLocation bestLoc = null;
        int bestDist = Integer.MAX_VALUE;
        int currentRound = rc.getRoundNum();

        for (Message m : messages) {
            int msg = m.getBytes();
            int type = decodeType(msg);

            if (type != msgType) continue;

            int sentRound = decodeExtra(msg);
            int age = (currentRound - sentRound) % EXTRA_MASK;

            
            if (age <= 10 || age > 2000) {
                int x = decodeX(msg);
                int y = decodeY(msg);
                MapLocation loc = new MapLocation(x, y);
                int dist = rc.getLocation().distanceSquaredTo(loc);

                if (dist < bestDist) {
                    bestDist = dist;
                    bestLoc = loc;
                }
            }
        }

        return bestLoc;
    }

    
    public static void processMessages() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        int currentRound = rc.getRoundNum();

        for (Message m : messages) {
            int msg = m.getBytes();
            int type = decodeType(msg);
            int x = decodeX(msg);
            int y = decodeY(msg);
            MapLocation loc = new MapLocation(x, y);

            switch (type) {
                case MSG_TOWER_LOC:
                    addKnownAllyTower(loc);
                    break;
                case MSG_ENEMY_LOC:
                    lastKnownEnemyLoc = loc;
                    lastKnownEnemyRound = currentRound;
                    break;
                case MSG_RUIN_LOC:
                    addKnownRuin(loc);
                    break;
                default:
                    break;
            }
        }
    }

    
    
    

    
    public static void scanEnvironment() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
                if (isPaintTower(ally.getType())) {
                    addKnownPaintTower(ally.getLocation());
                }
            }
        }

        
        if (enemies.length > 0) {
            lastKnownEnemyLoc = enemies[0].getLocation();
            lastKnownEnemyRound = rc.getRoundNum();

            
            for (RobotInfo enemy : enemies) {
                if (isTowerType(enemy.getType())) {
                    broadcastEnemyTower(enemy.getLocation());
                    break;
                }
            }
        }

        
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                if (!rc.canSenseRobotAtLocation(ruinLoc) ||
                    (rc.canSenseRobotAtLocation(ruinLoc) &&
                     !isTowerType(rc.senseRobotAtLocation(ruinLoc).getType()))) {
                    addKnownRuin(ruinLoc);
                }
            }
        }
    }

    
    
    

    
    public static void explore() throws GameActionException {
        if (!rc.isMovementReady()) return;

        
        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation next = myLoc.add(dir);
            int score = 0;

            
            score += next.distanceSquaredTo(spawnLocation);

            
            if (rc.canSenseLocation(next)) {
                MapInfo info = rc.senseMapInfo(next);
                if (!info.getPaint().isAlly()) {
                    score += 15; 
                }
                if (info.getPaint() == PaintType.EMPTY) {
                    score += 5;
                }
            }

            
            if (currentExploreDirection != null && dir == currentExploreDirection.opposite()) {
                score -= 20;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            currentExploreDirection = bestDir;
            rc.move(bestDir);
        } else {
            
            wander();
        }
    }

    
    public static void wander() throws GameActionException {
        if (!rc.isMovementReady()) return;

        if (currentExploreDirection == null || !rc.canMove(currentExploreDirection)) {
            int randomIndex = (int) (Math.random() * directions.length);
            currentExploreDirection = directions[randomIndex];
        }

        if (rc.canMove(currentExploreDirection)) {
            rc.move(currentExploreDirection);
        } else {
            
            for (int i = 0; i < 8; i++) {
                currentExploreDirection = currentExploreDirection.rotateRight();
                if (rc.canMove(currentExploreDirection)) {
                    rc.move(currentExploreDirection);
                    return;
                }
            }
        }
    }

    
    
    

    
    public static void paintUnderSelf() throws GameActionException {
        if (!rc.isActionReady()) return;

        MapLocation myLoc = rc.getLocation();
        if (!rc.canSenseLocation(myLoc)) return;
        MapInfo info = rc.senseMapInfo(myLoc);
        if (!info.getPaint().isAlly() && info.isPassable() && rc.canAttack(myLoc)) {
            PaintType mark = info.getMark();
            boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
            rc.attack(myLoc, useSecondary);
        }
    }

    
    
    

    public static void addKnownPaintTower(MapLocation loc) {
        for (int i = 0; i < knownPaintTowerCount; i++) {
            if (knownPaintTowers[i].equals(loc)) return;
        }
        if (knownPaintTowerCount < knownPaintTowers.length) {
            knownPaintTowers[knownPaintTowerCount++] = loc;
        }
    }

    public static void addKnownAllyTower(MapLocation loc) {
        for (int i = 0; i < knownAllyTowerCount; i++) {
            if (knownAllyTowers[i].equals(loc)) return;
        }
        if (knownAllyTowerCount < knownAllyTowers.length) {
            knownAllyTowers[knownAllyTowerCount++] = loc;
        }
    }

    public static void addKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) return;
        }
        if (knownRuinCount < knownRuins.length) {
            knownRuins[knownRuinCount++] = loc;
        }
    }

    
    public static MapLocation findNearestKnownRuin() {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (int i = 0; i < knownRuinCount; i++) {
            int dist = myLoc.distanceSquaredTo(knownRuins[i]);
            if (dist < minDist) {
                minDist = dist;
                nearest = knownRuins[i];
            }
        }
        return nearest;
    }

    
    public static void removeKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) {
                knownRuins[i] = knownRuins[knownRuinCount - 1];
                knownRuinCount--;
                return;
            }
        }
    }

    
    
    

    
    public static int getGamePhase() {
        int round = rc.getRoundNum();
        if (round < EARLY_GAME_END) return 0;
        if (round < MID_GAME_END) return 1;
        return 2;
    }

    
    public static boolean isLowPaint() {
        int paintCapacity = rc.getType().paintCapacity;
        int currentPaint = rc.getPaint();
        return (currentPaint * 100 / paintCapacity) < LOW_PAINT_PERCENT;
    }

    
    public static int countNearbyAllies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam()).length;
    }

    
    public static int countNearbyEnemies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;
    }
}
