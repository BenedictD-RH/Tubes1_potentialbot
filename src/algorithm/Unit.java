package algorithm;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Unit extends BaseRobot {

    public static MapLocation spawnLocation;
    public static Direction currentExploreDirection = null;
    // Untuk informasi musuh
    private static final int COMM_ENEMY_ROBOT_TAG = 0x80000000;
    private static final int COMM_DEADEND_TAG     = 0x40000000; 
    private static final int COMM_ENEMY_TOWER_TAG = 0x20000000; 
    private static final int COORD_MASK = 0x3FF;
    private static final int ROUND_MASK = 0x7FF;
    private static Direction bugDirection = null; // Menyusuri tembok

    public static void initUnit() {
        if(spawnLocation == null){
            spawnLocation = rc.getLocation();
        }
    }

    public static void move(MapLocation target) throws GameActionException {
        if(!rc.isMovementReady()){
            return;
        }

        MapLocation myLoc = rc.getLocation();

        // Meminta informasi terkait jalur tembok
        MapLocation deadEnd = getDeadEndBroadcast();
        if (deadEnd != null && myLoc.distanceSquaredTo(deadEnd) < 30) { // Jika berada di arah area buntu
            target = myLoc.add(myLoc.directionTo(deadEnd).opposite());
        }

        Direction bestDir = myLoc.directionTo(target);
        if (bestDir == Direction.CENTER) return;

        // bergerak
        if (rc.canMove(bestDir)) {
            rc.move(bestDir);
            bugDirection = bestDir; 
            return;
        }

        // Mengikuti jalur tembok
        if (bugDirection == null) bugDirection = bestDir;

        Direction checkDir = bugDirection;
        boolean moved = false;

        for (int i = 0; i < 8; i++) {
            if (rc.canMove(checkDir)) {
                rc.move(checkDir);
                bugDirection = checkDir.rotateLeft().rotateLeft(); 
                moved = true;
                break;
            }
            checkDir = checkDir.rotateRight();
        }

        // Mmeberitahukan bot lainnya
        if (!moved) {
            broadcastDeadEnd(myLoc); 
        }
    }

    public static void buildTower() throws GameActionException {
        if(!rc.isActionReady()){
            return;
        }

        UnitType[] towersToBuild = {
            UnitType.LEVEL_ONE_PAINT_TOWER,
            UnitType.LEVEL_ONE_MONEY_TOWER,
            UnitType.LEVEL_ONE_DEFENSE_TOWER
        };

        // mengarahkan untuk melakukan pembuatan tower
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            MapLocation buildLoc = tile.getMapLocation();
            for(UnitType tower : towersToBuild){
                if (rc.canCompleteTowerPattern(tower, buildLoc)){
                    rc.completeTowerPattern(tower, buildLoc);
                    return;
                }
            }
            
            // memberikan mark pada ruin yang ingin dibuat
            if (tile.hasRuin() && !rc.canSenseRobotAtLocation(buildLoc)){
                for(UnitType tower : towersToBuild){
                    if(rc.canMarkTowerPattern(tower, buildLoc)){
                        rc.markTowerPattern(tower, buildLoc);
                        return;
                    }
                }
            }
        }
    }

    public static boolean refillPaint() throws GameActionException {
        int paintThreshold = rc.getType().paintCapacity / 5; // titik kritis di 20% kapasitas
        if (rc.getPaint() > paintThreshold) {
            return false;
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearestPaintTower = null;
        int minDistance = Integer.MAX_VALUE;

        for (RobotInfo ally : allies){ // cek paint tower terdekat
            if(ally.getType() == UnitType.LEVEL_ONE_PAINT_TOWER ||
               ally.getType() == UnitType.LEVEL_TWO_PAINT_TOWER ||
               ally.getType() == UnitType.LEVEL_THREE_PAINT_TOWER){
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < minDistance){
                    minDistance = dist;
                    nearestPaintTower = ally.getLocation();
                }
            }
        }

        if(nearestPaintTower != null){
            int dist = rc.getLocation().distanceSquaredTo(nearestPaintTower);
            if(dist > 2){
                if(rc.isMovementReady()){
                    move(nearestPaintTower);
                }
            } else {
                int needed = rc.getType().paintCapacity - rc.getPaint();
                if (needed > 0 && rc.canSenseRobotAtLocation(nearestPaintTower)) {
                    RobotInfo tower = rc.senseRobotAtLocation(nearestPaintTower);
                    int available = tower.getPaintAmount();
                    int transferAmount = Math.min(needed, available);
                    if (transferAmount > 0 && rc.canTransferPaint(nearestPaintTower, -transferAmount)) {
                        rc.transferPaint(nearestPaintTower, -transferAmount);
                    }
                }
            }
            return true;
        }

        if(spawnLocation != null && rc.isMovementReady()){
            move(spawnLocation);
        }
        return true;
    }



    // Broadcast Terkait Musuh
    public static void broadcastEnemyTarget(MapLocation loc) throws GameActionException {
        int msg = COMM_ENEMY_ROBOT_TAG | ((rc.getRoundNum() & ROUND_MASK) << 20) | ((loc.x & COORD_MASK) << 10) | (loc.y & COORD_MASK);
        sendToAllies(msg);
    }

    // Broadcast Terkait Tower Musuh
    public static void broadcastEnemyTower(MapLocation loc) throws GameActionException {
        int msg = COMM_ENEMY_TOWER_TAG | ((rc.getRoundNum() & ROUND_MASK) << 20) | ((loc.x & COORD_MASK) << 10) | (loc.y & COORD_MASK);
        sendToAllies(msg);
    }

    // Mengirim pesan
    private static void sendToAllies(int msg) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.getLocation(), msg)) {
                rc.sendMessage(ally.getLocation(), msg);
            }
        }
    }

    // Mendapatkan Informasi Teman (Tower > Robot > Jalan Buntu)
    public static MapLocation getBroadcastTarget() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        if (messages.length == 0) return null;

        MapLocation bestTowerLoc = null;
        MapLocation bestRobotLoc = null;
        int current = rc.getRoundNum() & ROUND_MASK;
        int bestTowerRound = -1;
        int bestRobotRound = -1;
        
        MapLocation myLoc = rc.getLocation();

        for (Message m : messages) {
            int msg = m.getBytes();
            int roundSent = (msg >> 20) & ROUND_MASK;
            int age = (current - roundSent) & ROUND_MASK;
            int x = (msg >> 10) & COORD_MASK;
            int y = msg & COORD_MASK;
            
            MapLocation targetLoc = new MapLocation(x, y);

            // Filter Jarak Maksimal
            if (myLoc.distanceSquaredTo(targetLoc) > 400) {
                continue; 
            }

            if ((msg & COMM_ENEMY_TOWER_TAG) != 0 && age < 30) {
                if (roundSent > bestTowerRound) {
                    bestTowerRound = roundSent;
                    bestTowerLoc = targetLoc;
                }
            } 
            else if ((msg & COMM_ENEMY_ROBOT_TAG) != 0 && age < 10) {
                if (roundSent > bestRobotRound) {
                    bestRobotRound = roundSent;
                    bestRobotLoc = targetLoc;
                }
            }
        }

        // Filter Jalan Buntu
        MapLocation deadEnd = getDeadEndBroadcast();
        if (deadEnd != null) {
            if (myLoc.distanceSquaredTo(deadEnd) <= 30) {
                return null; 
            }
            if (bestTowerLoc != null && bestTowerLoc.distanceSquaredTo(deadEnd) <= 16) {
                bestTowerLoc = null;
            }
            if (bestRobotLoc != null && bestRobotLoc.distanceSquaredTo(deadEnd) <= 16) {
                bestRobotLoc = null;
            }
        }

        if (bestTowerLoc != null) return bestTowerLoc;
        return bestRobotLoc; 
    }
    
    // Broadcast Jalan Buntu
    public static void broadcastDeadEnd(MapLocation loc) throws GameActionException {
        int msg = COMM_DEADEND_TAG
            | ((rc.getRoundNum() & ROUND_MASK) << 20)
            | ((loc.x & COORD_MASK) << 10)
            | (loc.y & COORD_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (rc.canSendMessage(ally.getLocation(), msg)) {
                rc.sendMessage(ally.getLocation(), msg);
            }
        }
    }

    public static MapLocation getDeadEndBroadcast() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            int msg = m.getBytes();
            if ((msg & COMM_DEADEND_TAG) != 0) {
                int roundSent = (msg >> 20) & ROUND_MASK;
                int age = (rc.getRoundNum() - roundSent) & ROUND_MASK;
                
                // Pesan jalan buntu dihiraukan jika sudah lebih dari 20 iterasi
                if (age < 20) { 
                    int x = (msg >> 10) & COORD_MASK;
                    int y = msg & COORD_MASK;
                    return new MapLocation(x, y);
                }
            }
        }
        return null;
    }

    // 
    public static void wander() throws GameActionException {
        if (!rc.isMovementReady()) return;
        if (currentExploreDirection != null && rc.canMove(currentExploreDirection)) {
            if (Math.random() > 0.2) {
                rc.move(currentExploreDirection);
                return;
            }
        }

        MapInfo[] visibleTiles = rc.senseNearbyMapInfos(-1);
        int[] paintDensity = new int[8];

        for (MapInfo tile : visibleTiles) {
            if (tile.getPaint().isAlly()) {
                Direction dir = rc.getLocation().directionTo(tile.getMapLocation());
                if (dir != Direction.CENTER) {
                    paintDensity[dir.ordinal()]++;
                }
            }
        }

        Direction bestExploreDir = null;
        int minPaint = Integer.MAX_VALUE;

        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                if (paintDensity[dir.ordinal()] < minPaint) {
                    minPaint = paintDensity[dir.ordinal()];
                    bestExploreDir = dir;
                }
            }
        }

        if (bestExploreDir != null) {
            rc.move(bestExploreDir);
            currentExploreDirection = bestExploreDir;
        } else {
            if (currentExploreDirection == null) {
                currentExploreDirection = directions[(int) (Math.random() * directions.length)];
            }
            int randomTurn = (Math.random() < 0.5) ? 3 : 5;
            for(int i = 0; i < randomTurn; i++){
                currentExploreDirection = currentExploreDirection.rotateRight();
            }
        }
    }
    // Membuat Tower
    public static boolean tryBuildTower() throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        MapLocation targetRuin = null;
        int minDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();

            if (rc.canSenseRobotAtLocation(ruinLoc)) {
                RobotInfo robotAtRuin = rc.senseRobotAtLocation(ruinLoc);
                if (robotAtRuin != null && robotAtRuin.getType().isTowerType()) {
                    continue; 
                }
            }

            int dist = myLoc.distanceSquaredTo(ruinLoc);
            if (dist < minDistance) {
                minDistance = dist;
                targetRuin = ruinLoc;
            }
        }

        if (targetRuin == null) {
            return false; 
        }

        int totalTowers = rc.getNumberTowers();
        int chips = rc.getMoney();
        int paint = rc.getPaint();
        UnitType towerToBuild;

        // Analisis Garis Depan 
        RobotInfo[] enemiesNearRuin = rc.senseNearbyRobots(targetRuin, 16, rc.getTeam().opponent());
        boolean isFrontline = enemiesNearRuin.length > 0;

        // Prioritas Pembuatan Tower
        if (isFrontline && chips >= 2500) {
            // Jika ini garis depan dan uang cukup, membuat buat Defense Tower
            towerToBuild = UnitType.LEVEL_ONE_DEFENSE_TOWER;
        } 
        else if (totalTowers < 3) {
            towerToBuild = UnitType.LEVEL_ONE_MONEY_TOWER;
        } 
        else if (totalTowers < 5) {
            towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;
        } 
        else if(totalTowers < 7){
            towerToBuild = UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        else if(totalTowers <= 8){
            towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        else {
            if (chips >= 2500 && paint >= 1000) {
                towerToBuild = UnitType.LEVEL_ONE_DEFENSE_TOWER;
            } else if (paint > 1500 && chips < 1000) {
                towerToBuild = UnitType.LEVEL_ONE_MONEY_TOWER;
            } else if (chips > 1500 && paint < 1000) {
                towerToBuild = UnitType.LEVEL_ONE_PAINT_TOWER;
            }
            else {
                towerToBuild = (totalTowers % 2 == 0) ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
            }
        }

        int requiredChips = (towerToBuild == UnitType.LEVEL_ONE_DEFENSE_TOWER) ? 2500 : 1000;
        
        if (chips < requiredChips) {
            if (myLoc.distanceSquaredTo(targetRuin) <= 2 && chips >= requiredChips - 200) {
                return true; 
            }
            return false; 
        }

        //  Eksekusi
        if (rc.canCompleteTowerPattern(towerToBuild, targetRuin)) {
            rc.completeTowerPattern(towerToBuild, targetRuin);
            return true; 
        }

        if (rc.canMarkTowerPattern(towerToBuild, targetRuin)) {
            rc.markTowerPattern(towerToBuild, targetRuin);
        }

        if (myLoc.distanceSquaredTo(targetRuin) > 2) {
            if (rc.isMovementReady()) {
                move(targetRuin);
                return true; 
            }
        }

        return false; 
    }
}