package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Tower extends BaseRobot {

    
    private static int turnCount       = 0;
    private static int soldierSpawned  = 0;
    private static int mopperSpawned   = 0;
    private static int splasherSpawned = 0;

    
    private static MapLocation lastEnemyLoc   = null;
    private static int         lastEnemyRound = -1;
    private static MapLocation pendingRuin    = null; 

    public static void run() throws GameActionException {
        turnCount++;

        int round  = rc.getRoundNum();
        int chips  = rc.getMoney();
        int paint  = rc.getPaint();
        int towers = rc.getNumberTowers();

        
        attackEnemies();

        
        processIncomingMessages();

        
        if (rc.isActionReady() && shouldUpgrade(chips, towers)) {
            if (rc.canUpgradeTower(rc.getLocation())) {
                rc.upgradeTower(rc.getLocation());
                return;
            }
        }

        
        if (rc.isActionReady()) {
            spawnUnit(round, towers, chips, paint);
        }

        
        if (round <= 10 || round % 15 == 0) {
            broadcastMyLocation();
        }
    }

    
    
    

    private static void attackEnemies() throws GameActionException {
        if (!rc.isActionReady()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        
        RobotInfo target = null;
        int minHP = Integer.MAX_VALUE;

        
        for (RobotInfo e : enemies) {
            if (!isTowerType(e.getType()) && e.health < minHP) {
                minHP = e.health;
                target = e;
            }
        }
        
        if (target == null) {
            for (RobotInfo e : enemies) {
                if (e.health < minHP) { minHP = e.health; target = e; }
            }
        }

        if (target != null && rc.canAttack(target.getLocation())) {
            rc.attack(target.getLocation());
        }
    }

    
    
    

    private static void processIncomingMessages() throws GameActionException {
        Message[] msgs = rc.readMessages(-1);
        int curRound   = rc.getRoundNum();

        for (Message m : msgs) {
            int raw  = m.getBytes();
            int type = decodeType(raw);
            int x    = decodeX(raw);
            int y    = decodeY(raw);

            switch (type) {
                case MSG_ENEMY_LOC:
                case MSG_ENEMY_TOWER:
                    lastEnemyLoc   = new MapLocation(x, y);
                    lastEnemyRound = curRound;
                    relayToNearbyTowers(raw);
                    break;
                case MSG_MOPPER_REQ:
                case MSG_RUIN_LOC:
                    pendingRuin = new MapLocation(x, y);
                    break;
                default:
                    break;
            }
        }
    }

    
    private static void relayToNearbyTowers(int msg) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo a : allies) {
            if (isTowerType(a.getType()) && !a.getLocation().equals(rc.getLocation())) {
                if (rc.canSendMessage(a.getLocation(), msg)) {
                    rc.sendMessage(a.getLocation(), msg);
                    return;
                }
            }
        }
    }

    
    private static void broadcastMyLocation() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int msgType = MSG_TOWER_LOC;
        int msg = encodeMessage(msgType, myLoc.x, myLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] nearby = rc.senseNearbyRobots(-1, rc.getTeam());
        int sent = 0;
        for (RobotInfo u : nearby) {
            if (!isTowerType(u.getType()) && rc.canSendMessage(u.getLocation(), msg)) {
                rc.sendMessage(u.getLocation(), msg);
                if (++sent >= 3) break;
            }
        }
    }

    
    
    

    private static void spawnUnit(int round, int towers, int chips, int paint)
            throws GameActionException {

        UnitType toSpawn = decideUnitType(round, towers, chips, paint);
        if (toSpawn == null) return;

        
        MapLocation spawnLoc = chooseBestSpawnLoc(toSpawn);
        if (spawnLoc == null) return;

        if (rc.canBuildRobot(toSpawn, spawnLoc)) {
            rc.buildRobot(toSpawn, spawnLoc);
            countSpawn(toSpawn);
        }
    }

    
    private static UnitType decideUnitType(int round, int towers, int chips, int paint) {

        
        if (round < EARLY_GAME_END) {
            
            if (chips < 250 || paint < 200) return null;

            int totalUnit = soldierSpawned + mopperSpawned + splasherSpawned;

            
            if (soldierSpawned < 5) {
                return UnitType.SOLDIER;
            }

            
            if (mopperSpawned < soldierSpawned / 3 && paint >= 100 && chips >= 300) {
                return UnitType.MOPPER;
            }

            
            if (chips >= 250 && paint >= 200) return UnitType.SOLDIER;
            return null;
        }

        
        if (round < MID_GAME_END) {
            int total = soldierSpawned + mopperSpawned + splasherSpawned;
            if (total == 0) {
                return (chips >= 250 && paint >= 200) ? UnitType.SOLDIER : null;
            }

            double soldierR  = (double) soldierSpawned  / total;
            double mopperR   = (double) mopperSpawned   / total;
            double splasherR = (double) splasherSpawned / total;

            
            if (pendingRuin != null && mopperR < 0.3 && paint >= 100 && chips >= 300) {
                pendingRuin = null;
                return UnitType.MOPPER;
            }

            
            if (soldierR < 0.40 && chips >= 250 && paint >= 200) return UnitType.SOLDIER;
            if (splasherR < 0.35 && chips >= 400 && paint >= 300) return UnitType.SPLASHER;
            if (mopperR  < 0.25 && chips >= 300 && paint >= 100) return UnitType.MOPPER;

            
            if (chips >= 250 && paint >= 200) return UnitType.SOLDIER;
            if (chips >= 300 && paint >= 100) return UnitType.MOPPER;
            return null;
        }

        
        int total = soldierSpawned + mopperSpawned + splasherSpawned;
        if (total == 0) {
            return (chips >= 250 && paint >= 200) ? UnitType.SOLDIER : null;
        }

        double splasherR = (double) splasherSpawned / total;
        double soldierR  = (double) soldierSpawned  / total;

        
        if (splasherR < 0.50 && chips >= 400 && paint >= 300) return UnitType.SPLASHER;
        if (soldierR  < 0.30 && chips >= 250 && paint >= 200) return UnitType.SOLDIER;
        if (chips >= 300 && paint >= 100) return UnitType.MOPPER;
        return null;
    }

    
    private static MapLocation chooseBestSpawnLoc(UnitType type) throws GameActionException {
        MapLocation myLoc  = rc.getLocation();
        MapLocation best   = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (!rc.canBuildRobot(type, spawnLoc)) continue;

            int score = 0;

            if (rc.canSenseLocation(spawnLoc)) {
                
                if (rc.senseMapInfo(spawnLoc).getPaint().isAlly()) {
                    score += 20;
                }
            }

            
            if (lastEnemyLoc != null && rc.getRoundNum() - lastEnemyRound < 50) {
                
                score -= spawnLoc.distanceSquaredTo(lastEnemyLoc) / 10;
            }

            
            if (pendingRuin != null) {
                score -= spawnLoc.distanceSquaredTo(pendingRuin) / 10;
            }

            if (score > bestScore) {
                bestScore = score;
                best = spawnLoc;
            }
        }
        return best;
    }

    
    
    

    private static boolean shouldUpgrade(int chips, int towers) {
        int round = rc.getRoundNum();
        
        if (isPaintTower(rc.getType())) return chips >= 2500 && round > 100;
        if (isMoneyTower(rc.getType()))  return chips >= 3500 && round > 200;
        if (isDefenseTower(rc.getType())) return chips >= 2500;
        return false;
    }

    
    
    

    private static void countSpawn(UnitType type) {
        if (type == UnitType.SOLDIER)  soldierSpawned++;
        else if (type == UnitType.MOPPER)   mopperSpawned++;
        else if (type == UnitType.SPLASHER) splasherSpawned++;
    }
}
