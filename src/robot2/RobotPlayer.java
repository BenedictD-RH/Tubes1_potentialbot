package robot2;

import battlecode.common.*;
import scala.Char;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;



public class RobotPlayer {
   
    static int turnCount = 0;

   
    static final Random rng = new Random(6147);

    static int mapWidth;
    static int mapHeight;
    static boolean mapDimensionFound = false;

    static private int cornerIdx = rng.nextInt(4);

    static private LocationMemory knownTTL = new LocationMemory();

    static private LocationMemory knownETL = new LocationMemory();

    static private LocationMemory teamTowerLocations = new LocationMemory();

    static private LocationMemory enemyTowerLocations = new LocationMemory();

    static private LocationMemory destroyedETL = new LocationMemory();

    static private boolean goBackToTower = false;

    static private MapLocation targetTower;

    static private boolean towerPatternComplete = false;

    static private int robotRole = 0;

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
    public static void run(RobotController rc) throws GameActionException {
        updateLocationMemory(rc);
        System.out.println("I'm alive");
        boolean scout = false;
        rc.setIndicatorString("Hello world!");
        while (true) {
            
            turnCount += 1;  
            
            try {
                switch (rc.getType()){
                    case SOLDIER:
                        rc.setIndicatorString(Integer.toString(robotRole));
                        Message[] m = rc.readMessages(-1);
                        for (int i = 0; i < m.length; i++) {
                            int message = m[i].getBytes();
                            decodeMessage(message);
                        }
                        
                        if (robotRole == 0) {
                            completeTowerMark(rc);
                        }
                        else if (robotRole == 1) {
                            scoutMap(rc);
                        }
                        else if (robotRole == 2) {
                            rushTower(rc, targetTower);
                        }
                        else if (robotRole == 3) {
                            messageBackToTower(rc);
                        }
                        else {
                            System.out.println("how bro : "  + robotRole);
                        }
                        break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: break; 
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                
                
                
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

   
    public static void runTower(RobotController rc) throws GameActionException{
        if (!mapDimensionFound) {
            int i = 60;
            MapLocation towerLoc = rc.getLocation();
            while(!rc.onTheMap(new MapLocation(i, towerLoc.y))) {
                i--;
            }
            mapWidth = i;
            i = 60;
            while(!rc.onTheMap(new MapLocation(towerLoc.x, i))) {
                i--;
            }
            mapHeight = i;
            mapDimensionFound = true;
            System.out.println("Map Dimension found : " + mapWidth + "x" + mapHeight);
        }

        if (!towerPatternComplete) {
            if (isTower5x5Complete(rc)) {
                towerPatternComplete = true;
                RobotInfo[] nearbyRobots = rc.senseNearbyRobots(); 
                for (RobotInfo robot : nearbyRobots) {
                    if (rc.canSendMessage(robot.getLocation())) {
                        rc.sendMessage(robot.getLocation(), encodeScoutInfo());
                        System.out.println("Turned robot " + robot.getID() + " to a Scout");
                    }
                }
            }
        }

        Message[] recievedMessages = rc.readMessages(-1);
        int mType = -1;
        for (int i = 0; i < recievedMessages.length; i++) {
            int message = recievedMessages[i].getBytes();
            mType = decodeMessage(message);
        }
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        
        int robotType = 0;
        if (robotType == 0 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)){
            rc.buildRobot(UnitType.SOLDIER, nextLoc);
            System.out.println("BUILT A SOLDIER");

            if (rc.canSendMessage(nextLoc)) {
                if (knownETL.amountSaved == 0) {
                    rc.sendMessage(nextLoc, encodeScoutInfo());
                }
                else {
                    rc.sendMessage(nextLoc, encodeRusherInfo(knownETL.savedLocations[0]));
                }
                sendTowerLocationsTo(rc, nextLoc, 97);
                sendTowerLocationsTo(rc, nextLoc, 92);
            }
        }
        else if (robotType == 1 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)){
            rc.buildRobot(UnitType.MOPPER, nextLoc);
            System.out.println("BUILT A MOPPER");
        }
        else if (robotType == 2 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)){
            
            
            rc.setIndicatorString("SPLASHER NOT IMPLEMENTED YET");
        }
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (RobotInfo robot : nearbyRobots) {

            if (rc.canSendMessage(robot.getLocation())) {
                if (knownETL.amountSaved > 0) {
                    
                    rc.sendMessage(robot.getLocation(), encodeRusherInfo(knownETL.savedLocations[0]));
                }
                else if (towerPatternComplete){
                    rc.sendMessage(robot.getLocation(), encodeScoutInfo());
                }
            }
        }
        if (rc.getType() == UnitType.LEVEL_ONE_MONEY_TOWER || rc.getType() == UnitType.LEVEL_TWO_MONEY_TOWER || rc.getType() == UnitType.LEVEL_THREE_MONEY_TOWER) {
            rc.setIndicatorString(Integer.toString(rc.getMoney()));
        }
        
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        
    }

   
    public static void runSoldier(RobotController rc) throws GameActionException{
        scoutMap(rc);
    }


    public static void runMopper(RobotController rc) throws GameActionException{
        
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (rc.canMove(dir)){
            rc.move(dir);
        }
        if (rc.canMopSwing(dir)){
            rc.mopSwing(dir);
            System.out.println("Mop Swing! Booyah!");
        }
        else if (rc.canAttack(nextLoc)){
            rc.attack(nextLoc);
        }
        
        updateEnemyRobots(rc);
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException {
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }

    private static void smartMove(RobotController rc, MapLocation targetLocation) throws GameActionException {
        Direction dir1 = rc.getLocation().directionTo(targetLocation);
            int dir1Idx = 0;
            for (int i = 0; i < directions.length;i++) {
                if (dir1 == directions[i]) dir1Idx = i;
            }
            if (rc.canMove(dir1)) {
                rc.move(dir1);
            }
            else {
                int i = 1;
                while (!rc.canMove(directions[Math.floorMod((dir1Idx + i), directions.length)])) {
                    if (i > 0) {
                        i*= -1;
                    }
                    else {
                        i = i* - 1 + 1;
                    }
                }
                rc.move(directions[Math.floorMod((dir1Idx + i), directions.length)]);
            }
    }


    public static void rushTower(RobotController rc, MapLocation towerLocation) throws GameActionException {
        boolean found = false;
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for(int i = 0; i < nearbyRobots.length;i++) {
            if (nearbyRobots[i].type.isTowerType() && rc.canAttack(nearbyRobots[i].getLocation())) {
                found = true;
                rc.attack(nearbyRobots[i].getLocation());
                break;
            };
        }
        
        if (!found) {
            if (rc.canSenseLocation(towerLocation) && !rc.canSenseRobotAtLocation(towerLocation)) {
                destroyedETL.saveLocation(towerLocation);
                robotRole = 3;
            }
            else {
                smartMove(rc, towerLocation);
                MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
                if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
                    rc.attack(rc.getLocation());
                }
            }
        }
    }

    public static void scoutMap(RobotController rc) throws GameActionException {
        if (!mapDimensionFound) return;
        
        if (!goBackToTower) {
            MapLocation[] mapCorners = {
                new MapLocation(0, 0),
                new MapLocation(mapWidth, 0),
                new MapLocation(mapWidth, mapHeight),
                new MapLocation(0,mapHeight)
            };

            if(rc.canSenseLocation(mapCorners[cornerIdx])) {
                cornerIdx = (cornerIdx + 1) % 4;
            }
            smartMove(rc, mapCorners[cornerIdx]);
        }
        else {
            messageBackToTower(rc);
        }
        updateLocationMemory(rc);
    }

    public static void messageBackToTower(RobotController rc) throws GameActionException {
        smartMove(rc, teamTowerLocations.nearestLocationTo(rc.getLocation()));
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (int i = 0; i < nearbyRobots.length;i++) {
            if (nearbyRobots[i].team == rc.getTeam() && nearbyRobots[i].type.isTowerType()) {
                if (rc.canSendMessage(nearbyRobots[i].getLocation())) {
                    sendTowerLocationsTo(rc, nearbyRobots[i].getLocation(), 99);
                    sendTowerLocationsTo(rc, nearbyRobots[i].getLocation(), 96);
                    sendTowerLocationsTo(rc, nearbyRobots[i].getLocation(), 94);
                }
            }
        }
    }

    public static void manageTowers(RobotController rc) throws GameActionException {
        
    }

    public static void completeTowerMark(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation curLoc = null;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                curLoc = tile.getMapLocation();
            }
        }
        UnitType towerType = UnitType.LEVEL_ONE_MONEY_TOWER;
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType().isTowerType() && robot.getTeam() == rc.getTeam()) {
                curLoc = robot.getLocation();
                towerType = robot.getType();
            }
        }

        if (curLoc != null){
            MapLocation targetLoc = curLoc;
            Direction dir = targetLoc.directionTo(rc.getLocation());
            MapLocation shouldBeMarked = curLoc.add(dir);
            if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(towerType, targetLoc)){
                rc.markTowerPattern(towerType, targetLoc);
            }
            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                    boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(patternTile.getMapLocation()))
                        rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                }
            }
            if (rc.canCompleteTowerPattern(towerType, targetLoc)){
                rc.completeTowerPattern(towerType, targetLoc);
                System.out.println("Tower Mark Completed");
            }
            else {
                smartMove(rc, curLoc.add(dir.rotateLeft().rotateLeft()));
            }
        }
    }

    private static int encodeScoutInfo() {
        String numString = "1";
        numString += mapWidth;
        numString += mapHeight;
        numString += rng.nextInt(4);
        numString += "01";
        return Integer.valueOf(numString);
    }

    private static int encodeRusherInfo(MapLocation m) {
        String numString = "1";
        if (m.x < 10) numString += "0";
        numString += m.x;
        if (m.y < 10) numString += "0";
        numString += m.y;
        numString += "002";
        return Integer.valueOf(numString);
    }

    private static int encodeTowerLocations(MapLocation m, int mType) {
        String numString = "1";
        if (m.x < 10) numString += "0";
        numString += m.x;
        if (m.y < 10) numString += "0";
        numString += m.y;
        numString += "0" + mType;
        return Integer.valueOf(numString);
    }

    private static int decodeMessage(int message) {
        String numString = Integer.toString(message);
        int messageType = Integer.valueOf(numString.substring(numString.length() - 2));
        if (messageType == 1) {
            mapWidth = Integer.valueOf(numString.substring(1,3));
            mapHeight = Integer.valueOf(numString.substring(3,5));
            mapDimensionFound = true;
            cornerIdx = numString.charAt(5) - '0';
            robotRole = messageType;
        }
        else if (messageType == 2) {
            int enemyTowerX = Integer.valueOf(numString.substring(1,3));
            int enemyTowerY = Integer.valueOf(numString.substring(3,5));
            targetTower = new MapLocation(enemyTowerX, enemyTowerY);
            robotRole = messageType;
        }
        else if (messageType == 99 || messageType == 98 || messageType == 97) {
            int enemyTowerX = Integer.valueOf(numString.substring(1,3));
            int enemyTowerY = Integer.valueOf(numString.substring(3,5));
            knownETL.saveLocation(enemyTowerX, enemyTowerY);
        }
        else if (messageType == 94 || messageType == 93 || messageType == 92) {
            int teamTowerX = Integer.valueOf(numString.substring(1,3));
            int teamTowerY = Integer.valueOf(numString.substring(3,5));
            knownTTL.saveLocation(teamTowerX, teamTowerY);
        }
        else if (messageType == 96 || messageType == 95) {
            int enemyTowerX = Integer.valueOf(numString.substring(1,3));
            int enemyTowerY = Integer.valueOf(numString.substring(3,5));
            knownETL.removeLocation(enemyTowerX, enemyTowerY);
        }

        return messageType;
    }

    public static void updateLocationMemory(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (int i = 0; i < nearbyRobots.length; i++) {
            if (nearbyRobots[i].type.isTowerType()) {
                if (nearbyRobots[i].team == rc.getTeam()) {
                    if (!knownTTL.isLocationInMemory(nearbyRobots[i].getLocation()) &&
                        !teamTowerLocations.isLocationInMemory(nearbyRobots[i].getLocation())) {
                        teamTowerLocations.saveLocation(nearbyRobots[i].getLocation());
                    }
                }
                else {
                    if (!knownETL.isLocationInMemory(nearbyRobots[i].getLocation()) &&
                        !enemyTowerLocations.isLocationInMemory(nearbyRobots[i].getLocation())) {
                        goBackToTower = true;
                        enemyTowerLocations.saveLocation(nearbyRobots[i].getLocation());
                    }
                }
            }
        }
    }

    public static void sendTowerLocationsTo(RobotController rc, MapLocation m, int mType) throws GameActionException {
        if (mType == 99) {
            while (enemyTowerLocations.amountSaved > 0 && rc.canSendMessage(m)) {
                rc.sendMessage(m, encodeTowerLocations(enemyTowerLocations.savedLocations[0], mType));
                knownETL.saveLocation(enemyTowerLocations.savedLocations[0]);
                enemyTowerLocations.removeLocation(enemyTowerLocations.savedLocations[0]);
            }
            if (enemyTowerLocations.amountSaved <= 0) {
                goBackToTower = false;
            }
        }
        else if (mType == 97) {
            for (int i = 0; i < knownETL.amountSaved; i++) {
                if (rc.canSendMessage(m)) {
                    rc.sendMessage(m, encodeTowerLocations(knownETL.savedLocations[i], mType));
                }
            }
        }
        else if (mType == 96) {
            while (destroyedETL.amountSaved > 0 && rc.canSendMessage(m)) {
                rc.sendMessage(m, encodeTowerLocations(destroyedETL.savedLocations[0], mType));
                knownETL.removeLocation(destroyedETL.savedLocations[0]);
                destroyedETL.removeLocation(destroyedETL.savedLocations[0]);
            }
            if (destroyedETL.amountSaved <= 0) {
                goBackToTower = false;
            }
        }
        else if (mType == 94) {
            while (teamTowerLocations.amountSaved > 0 && rc.canSendMessage(m)) {
                rc.sendMessage(m, encodeTowerLocations(teamTowerLocations.savedLocations[0], mType));
                knownTTL.saveLocation(teamTowerLocations.savedLocations[0]);
                teamTowerLocations.removeLocation(teamTowerLocations.savedLocations[0]);
            }
            if (teamTowerLocations.amountSaved <= 0) {
                goBackToTower = false;
            }
        }
        else if (mType == 92) {
            for (int i = 0; i < knownTTL.amountSaved; i++) {
                if (rc.canSendMessage(m)) {
                    rc.sendMessage(m, encodeTowerLocations(knownTTL.savedLocations[i], mType));
                }
            }
        }
    }

    public static boolean isTower5x5Complete(RobotController rc) throws GameActionException{
        boolean complete = true;
        boolean[][] towerPattern = rc.getTowerPattern(rc.getType());
        for (int i = -2; i < 3; i++) {
            for (int j = -2; j < 3; j++) {
                MapInfo mInfo = rc.senseMapInfo(new MapLocation(rc.getLocation().x + j,rc.getLocation().y + i));
                if (mInfo.getPaint() == PaintType.ALLY_SECONDARY && towerPattern[i + 2][j + 2]);
                else if (mInfo.getPaint() == PaintType.ALLY_PRIMARY && !towerPattern[i + 2][j + 2]);
                else if (mInfo.getMapLocation().equals(rc.getLocation()));
                else {
                    complete = false;
                    break;
                }
            }
            if (!complete) break;
        }

        return complete;
    }
}
