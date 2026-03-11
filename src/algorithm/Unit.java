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
    // Tag bit untuk menandai pesan broadcast kita agar tidak bentrok dengan pesan lain.
    private static final int COMM_TAG = 0x80000000;
    // Mask 10 bit untuk koordinat (0-1023).
    private static final int COORD_MASK = 0x3FF;
    // Mask 11 bit untuk ronde (0-2047).
    private static final int ROUND_MASK = 0x7FF;

    public static void initUnit() {
        if(spawnLocation == null){
            spawnLocation = rc.getLocation();
        }
    }

    public static void move(MapLocation target) throws GameActionException{
        if(!rc.isMovementReady()){
            return;
        }

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int minDistance = myLoc.distanceSquaredTo(target);
         for(Direction dir : directions) {
            if (rc.canMove(dir)){
                MapLocation nextLoc = myLoc.add(dir);
                int dist = nextLoc.distanceSquaredTo(target);

                if(dist < minDistance){
                    minDistance = dist;
                    bestDir = dir;
                }
            }
         }

         if( bestDir != null){
            rc.move(bestDir);
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
                    System.out.println("Tower built at " + buildLoc);
                    return;
                }
            }
            
            // memberikan mark pada ruin yang ingin dibuat
            if (tile.hasRuin() && !rc.canSenseRobotAtLocation(buildLoc)){
            for(UnitType tower : towersToBuild){
                if(rc.canMarkTowerPattern(tower, buildLoc)){
                    rc.markTowerPattern(tower, buildLoc);
                    System.out.println("Tower built at " + buildLoc);
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



    // Broadcast lokasi musuh ke ally terdekat.
    public static void broadcastEnemyTarget(MapLocation loc) throws GameActionException {
        int msg = COMM_TAG
            | ((rc.getRoundNum() & ROUND_MASK) << 20)
            | ((loc.x & COORD_MASK) << 10)
            | (loc.y & COORD_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            MapLocation allyLoc = ally.getLocation();
            if (rc.canSendMessage(allyLoc, msg)) {
                rc.sendMessage(allyLoc, msg);
            }
        }
    }

    // Membaca broadcast terbaru.
    public static MapLocation getBroadcastTarget() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        if (messages.length == 0) {
            return null;
        }

        int bestRound = -1;
        MapLocation bestLoc = null;
        int current = rc.getRoundNum() & ROUND_MASK;

        for (Message m : messages) {
            int msg = m.getBytes();
            if ((msg & COMM_TAG) == 0) {
                continue;
            }

            int roundSent = (msg >> 20) & ROUND_MASK;
            int age = (current - roundSent) & ROUND_MASK;
            if (age < 10) {
                int x = (msg >> 10) & COORD_MASK;
                int y = msg & COORD_MASK;
                if (roundSent > bestRound) {
                    bestRound = roundSent;
                    bestLoc = new MapLocation(x, y);
                }
            }
        }

        return bestLoc; // Mengembalikan null jika tidak ada sinyal darurat
    }
}


