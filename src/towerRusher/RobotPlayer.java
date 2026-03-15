package towerRusher;

import battlecode.common.*;

import java.util.Random;




public class RobotPlayer {
    
    static int turnCount = 0;

   
    static final Random rng = new Random(6147);

    static int mapWidth;
    static int mapHeight;
    static boolean mapDimensionFound = false;

    static private int cornerIdx = -1;

    static private LocationMemory knownTTL = new LocationMemory();

    static private LocationMemory knownETL = new LocationMemory();

    static private LocationMemory enemyTowerLocations = new LocationMemory();

    static private LocationMemory destroyedETL = new LocationMemory();

    static private boolean goBackToTower = false;

    static private MapLocation targetTower;

    static private boolean towerPatternComplete = false;

    static private int robotRole = 0;

    static private int lastReport = -1;

    static private boolean sendMoppers = false;

    static private boolean buildManager = false;

    static private boolean builtEmergencySplasher = false;

    static private boolean goClockwise;

    static private int mopperCD = 0;

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
            if (turnCount <= 2) {
                if (rc.getType().isTowerType()) {
                    if (rc.canBroadcastMessage()) {
                        rc.broadcastMessage(encodeOwnTowerLocation(rc));
                        System.out.println("Broadcasted Self");
                    }
                }
            }
            try {
                switch (rc.getType()){
                    case SOLDIER:
                        readAndDecodeMessages(rc);
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
                        else if (robotRole == 4) {
                            manageTowers(rc);
                        }
                        else {
                            System.out.println("how bro : "  + robotRole);
                        }
                        break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runEmergencySplasher(rc); break; 
                    default: 
                            runTower(rc); 
                        break;
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
        if (turnCount % 50 == 0) {
            knownTTL.printLocations("Known Team Towers");
        }
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
            if (mType == 96) {
                lastReport = turnCount;
            }
        }

        if ((turnCount - lastReport > 300) && lastReport != -1) {
            sendMoppers = true;
            //System.out.println("SEND MOPPERS NOW!");
        }

        
        if (!isTowerMaxLevel(rc)) {
            int addCost = 0;
            if (isPaintTower(rc)) {
                addCost = 500;
            }
            if (rc.getMoney() >= (rc.getType().getNextLevel().moneyCost + UnitType.SOLDIER.moneyCost) + addCost) {
                buildManager = true;
                System.out.println("SEND MANAGER");
            }
        }
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (isAreaCoveredInEnemyPaint(rc) && !builtEmergencySplasher) {
            if (rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                rc.buildRobot(UnitType.SPLASHER, nextLoc);
                builtEmergencySplasher = true;
                return;
            }
        }

        if (isAreaHasTeamPaint(rc)) {
            while(!rc.senseMapInfo(nextLoc).getPaint().isAlly()) {
                dir = dir.rotateRight();
                nextLoc = rc.getLocation().add(dir);
            }
        }

        if (isPaintTower(rc)) {
            if (sendMoppers && rc.canBuildRobot(UnitType.MOPPER, nextLoc) && (mopperCD < 4)){
                mopperCD++;
                rc.buildRobot(UnitType.MOPPER, nextLoc);
                if (rc.canSendMessage(nextLoc)) {
                    rc.sendMessage(nextLoc, encodeMopperInfo());
                }
            }
            else if ((!sendMoppers || mopperCD >= 4) && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)){
                mopperCD = 0;
                rc.buildRobot(UnitType.SOLDIER, nextLoc);
                System.out.println("BUILT A SOLDIER");

                if (rc.canSendMessage(nextLoc)) {
                    if (buildManager) {
                        rc.sendMessage(nextLoc, 10000004);
                        buildManager = false;
                        sendTowerLocationsTo(rc, nextLoc, 92);
                    }
                    else if (knownETL.amountSaved == 0) {
                        rc.sendMessage(nextLoc, encodeScoutInfo());
                    }
                    else {
                        rc.sendMessage(nextLoc, encodeRusherInfo(knownETL.savedLocations[0]));
                        System.out.println("Rush now");
                    }
                    sendTowerLocationsTo(rc, nextLoc, 97);
                }
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
        }
        if (isMoneyTower(rc)) {
            rc.setIndicatorString(Integer.toString(rc.getMoney()));
            if (rc.canBuildRobot(UnitType.SOLDIER, nextLoc) && (buildManager || turnCount == 1)) {
                rc.buildRobot(UnitType.SOLDIER, nextLoc); 
                if (rc.canSendMessage(nextLoc) && turnCount > 1) {
                    rc.sendMessage(nextLoc, 10000004);
                }
                buildManager = false;
                sendTowerLocationsTo(rc, nextLoc, 92);
            }
        }
        
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        
    }

   
    public static void runSoldier(RobotController rc) throws GameActionException{
        scoutMap(rc);
    }

    public static void runEmergencySplasher(RobotController rc) throws GameActionException {
        MapLocation[] mapCorners = mainMapSearchPoints();
        if (rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
        if(rc.canSenseLocation(mapCorners[cornerIdx])) {
            int d = 1;
            if (goClockwise) {
                d = 4;
            }
            cornerIdx = (cornerIdx + d) % 5;
        }
        if(rc.getActionCooldownTurns() <= 0) {
            RobotMovement.smartMove(rc, mapCorners[cornerIdx]);
        }
    }
    

    public static void runMopper(RobotController rc) throws GameActionException{
        readAndDecodeMessages(rc);
        MapLocation[] mapCorners = mainMapSearchPoints();

        if(rc.canSenseLocation(mapCorners[cornerIdx])) {
            int d = 1;
            if (goClockwise) d = 4;
            cornerIdx = (cornerIdx + d) % 5;
        }
        RobotMovement.mopperMove(rc, mapCorners[cornerIdx]);
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMopSwing(dir)){
            rc.mopSwing(dir);
            System.out.println("Mop Swing! Booyah!");
        }
        else {
            MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
            if (currentTile.getPaint().isEnemy() && rc.canAttack(rc.getLocation())){
                rc.attack(rc.getLocation());
            }
        }
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
                RobotMovement.smartMove(rc, towerLocation);
                MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
                if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
                    rc.attack(rc.getLocation());
                }
            }
        }
    }

    public static MapLocation[] mainMapSearchPoints() {
        MapLocation[] searchPoints = new MapLocation[5];
        MapLocation[] mapCorners = {
            new MapLocation(0, 0),
            new MapLocation(mapWidth - 3, 0),
            new MapLocation(mapWidth - 3, mapHeight - 3),
            new MapLocation(0,mapHeight - 3)
        };
        MapLocation spawnTowerLoc = knownTTL.savedLocations[0];
        MapLocation possibleEnemyTowerLoc = new MapLocation(mapWidth - spawnTowerLoc.x, mapHeight - spawnTowerLoc.y);



        int nearestPointIdx = 0;
        for (int i = 0; i < 4; i++) {
            if (mapCorners[i].distanceSquaredTo(possibleEnemyTowerLoc) < mapCorners[nearestPointIdx].distanceSquaredTo(possibleEnemyTowerLoc)) {
                nearestPointIdx = i;
            }
        }
        for (int i = 0; i < 5; i++) {
            if ((nearestPointIdx + 1) > i) {
                searchPoints[i] = mapCorners[i];
            }
            else if (((nearestPointIdx + 1) % 5) == i) {
                searchPoints[i] = possibleEnemyTowerLoc;
            }
            else {
                searchPoints[i] = mapCorners[i - 1];
            }
        }

        return searchPoints;
    }

    public static void scoutMap(RobotController rc) throws GameActionException {
        if (!mapDimensionFound) return;
        if (!goBackToTower) {
            MapLocation[] mapCorners = mainMapSearchPoints();

            if(rc.canSenseLocation(mapCorners[cornerIdx])) {
                int d = 1;
                if (goClockwise) {
                    d = 4;
                }
                cornerIdx = (cornerIdx + d) % 5;
            }
            RobotMovement.smartMove(rc, mapCorners[cornerIdx]);
        }
        else {
            messageBackToTower(rc);
        }
        //sendPaintToMoneyTower(rc);
        updateLocationMemory(rc);
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation()) && rc.getPaint() > rc.getType().paintCapacity*0.5){
            rc.attack(rc.getLocation());
        }
    }

    public static void messageBackToTower(RobotController rc) throws GameActionException {
        RobotMovement.smartMove(rc, knownTTL.nearestLocationTo(rc.getLocation()));
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (int i = 0; i < nearbyRobots.length;i++) {
            if (nearbyRobots[i].team == rc.getTeam() && nearbyRobots[i].type.isTowerType()) {
                if (rc.canSendMessage(nearbyRobots[i].getLocation())) {
                    sendTowerLocationsTo(rc, nearbyRobots[i].getLocation(), 99);
                    sendTowerLocationsTo(rc, nearbyRobots[i].getLocation(), 96);
                }
            }
        }
    }

    public static boolean isTowerMaxLevel(RobotInfo robot) {
        if (robot.getType() == UnitType.LEVEL_THREE_DEFENSE_TOWER || 
            robot.getType() == UnitType.LEVEL_THREE_MONEY_TOWER ||
            robot.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
            return true;
        }
        else {
            return false;
        }
    }

    public static boolean isTowerMaxLevel(RobotController robot) {
        if (robot.getType() == UnitType.LEVEL_THREE_DEFENSE_TOWER || 
            robot.getType() == UnitType.LEVEL_THREE_MONEY_TOWER ||
            robot.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {
            return true;
        }
        else {
            return false;
        }
    }

    public static boolean isPaintTower(UnitType type) {
        if (type == UnitType.LEVEL_ONE_PAINT_TOWER || 
            type == UnitType.LEVEL_TWO_PAINT_TOWER ||
            type == UnitType.LEVEL_THREE_PAINT_TOWER) {
            return true;
        }
        else {
            return false;
        }
    }

    public static boolean isPaintTower(RobotController rc) {
        return isPaintTower(rc.getType());
    }

    public static boolean isPaintTower(RobotInfo robot) {
        return isPaintTower(robot.getType());
    }

    public static boolean isMoneyTower(UnitType type) {
        if (type == UnitType.LEVEL_ONE_MONEY_TOWER || 
            type == UnitType.LEVEL_TWO_MONEY_TOWER ||
            type == UnitType.LEVEL_THREE_MONEY_TOWER) {
            return true;
        }
        else {
            return false;
        }
    }

    public static boolean isMoneyTower(RobotController rc) {
        return isMoneyTower(rc.getType());
    }

    public static boolean isMoneyTower(RobotInfo robot) {
        return isMoneyTower(robot.getType());
    }

    public static void manageTowers(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (RobotInfo robot : nearbyRobots) {
            if (robot.getType().isTowerType()) {
                if (rc.canUpgradeTower(robot.getLocation())) {
                    rc.upgradeTower(robot.getLocation());
                }
                if (rc.canTransferPaint(robot.getLocation(), -rc.getPaint())) {
                    rc.transferPaint(robot.getLocation(), -rc.getPaint());
                }
                break;
            }
        }
        rc.disintegrate();
    }

    public static void completeTowerMark(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation curLoc = null;
        for (MapInfo tile : nearbyTiles){
            if (rc.onTheMap(tile.getMapLocation())) {
                if (tile.hasRuin()){
                    curLoc = tile.getMapLocation();
                }
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
                if (rc.onTheMap(patternTile.getMapLocation())) {
                    if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                        boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                        if (rc.canAttack(patternTile.getMapLocation()))
                            rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                    }
                }
            }
            if (rc.canCompleteTowerPattern(towerType, targetLoc)){
                rc.completeTowerPattern(towerType, targetLoc);
                System.out.println("Tower Mark Completed");
            }
            else {
                RobotMovement.smartMove(rc, curLoc.add(dir.rotateLeft().rotateLeft()));
            }
        }
    }

    public static void readAndDecodeMessages(RobotController rc) {
        Message[] m = rc.readMessages(-1);
        for (int i = 0; i < m.length; i++) {
            int message = m[i].getBytes();
            decodeMessage(message);
        }
}

    private static int encodeScoutInfo() {
        String numString = "1";
        numString += mapWidth;
        numString += mapHeight;
        numString += rng.nextInt(10);
        numString += "01";
        System.out.println(numString);
        return Integer.valueOf(numString);
    }

        private static int encodeMopperInfo() {
        String numString = "1";
        numString += mapWidth;
        numString += mapHeight;
        numString += rng.nextInt(10);
        numString += "05";
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

    private static int encodeOwnTowerLocation(RobotController rc) {
        String numString = "1";
        MapLocation selfLoc = rc.getLocation();
        if (selfLoc.x < 10) numString += "0";
        numString += selfLoc.x;
        if (selfLoc.y < 10) numString += "0";
        numString += selfLoc.y;
        int z;
        switch(rc.getType()) {
            case LEVEL_ONE_DEFENSE_TOWER : z = 1; break;
            case LEVEL_ONE_PAINT_TOWER : z = 2; break;
            case LEVEL_ONE_MONEY_TOWER : z = 3; break;
            case LEVEL_TWO_DEFENSE_TOWER : z = 4; break;
            case LEVEL_TWO_PAINT_TOWER : z = 5; break;
            case LEVEL_TWO_MONEY_TOWER : z = 6; break;
            case LEVEL_THREE_DEFENSE_TOWER : z = 7; break;
            case LEVEL_THREE_PAINT_TOWER : z = 8; break;
            case LEVEL_THREE_MONEY_TOWER : z = 9; break;
            default : z = 0; break;
        }
        numString += z; 
        numString += "93";
        return Integer.valueOf(numString);
    }

    private static int decodeMessage(int message) {
        String numString = Integer.toString(message);
        int messageType = Integer.valueOf(numString.substring(numString.length() - 2));
        if ((messageType == 1 || messageType == 5) && robotRole != 4) {
            mapWidth = Integer.valueOf(numString.substring(1,3));
            mapHeight = Integer.valueOf(numString.substring(3,5));
            mapDimensionFound = true;
            if (cornerIdx == -1) {
                cornerIdx = (numString.charAt(5) - '0') % 5;
                goClockwise = (numString.charAt(5) - '0') >= 5;
            }
            robotRole = messageType;
        }
        else if (messageType == 2 && robotRole != 4) {
            int enemyTowerX = Integer.valueOf(numString.substring(1,3));
            int enemyTowerY = Integer.valueOf(numString.substring(3,5));
            targetTower = new MapLocation(enemyTowerX, enemyTowerY);
            robotRole = messageType;
        }
        else if (messageType == 4) {
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
        if (rc.getType().isTowerType() && !knownTTL.isLocationInMemory(rc.getLocation())) {
            knownTTL.saveLocation(rc.getLocation());
        }
        for (int i = 0; i < nearbyRobots.length; i++) {
            if (nearbyRobots[i].type.isTowerType()) {
                if (nearbyRobots[i].team == rc.getTeam()) {
                    if (!knownTTL.isLocationInMemory(nearbyRobots[i].getLocation())) {
                        knownTTL.saveLocation(nearbyRobots[i].getLocation());
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

    public static void sendPaintToMoneyTower(RobotController rc) throws GameActionException{
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        for (RobotInfo robot : nearbyRobots) {
            if (isMoneyTower(robot) && !isTowerMaxLevel(robot)) {
                int moneyTowerPaint = robot.getPaintAmount();
                if (moneyTowerPaint < 200) {
                    int currPaint = rc.getPaint();
                    if (currPaint > (200 - moneyTowerPaint)) {
                        currPaint = 200 - moneyTowerPaint;
                    }
                    rc.transferPaint(robot.location, -currPaint);
                }
            }
        }
    }

    public static boolean isAreaCoveredInEnemyPaint(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(3);
        int enemyPaintAmount = 0;
        int totalArea = 0;
        for (MapInfo tile : nearbyMapInfos) {
            if (tile.getPaint().isEnemy()) {
                enemyPaintAmount++;
            }
            totalArea++;
        }
        return enemyPaintAmount > 0.5*totalArea;
    }

    public static boolean isAreaHasTeamPaint(RobotController rc) throws GameActionException {
        MapInfo[] nearbyMapInfos = rc.senseNearbyMapInfos(1);
        for (MapInfo tile : nearbyMapInfos) {
            if (tile.getPaint().isAlly()) {
                return true;
            }
            
        }
        return false;
    }
}
