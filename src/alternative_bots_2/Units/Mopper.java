package alternative_bots_2.Units;

import alternative_bots_2.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Mopper extends Unit {

    private static final int STATE_MOP_RUIN  = 0;
    private static final int STATE_COMBAT    = 1;
    private static final int STATE_TRANSFER  = 2;
    private static final int STATE_MOP_AREA  = 3;
    private static final int STATE_REFILL    = 4;
    private static final int STATE_EXPLORE   = 5;

    private static int state = STATE_EXPLORE;
    private static MapLocation mopTarget = null;   
    private static MapLocation combatTarget = null; 

    public static void run() throws GameActionException {
        initUnit();
        scanEnvironment();
        processMessages();

        
        updateState();

        
        executeState();
    }

    
    
    

    private static void updateState() throws GameActionException {
        int paintPercent = (rc.getPaint() * 100) / rc.getType().paintCapacity;

        
        if (paintPercent <= LOW_PAINT_PERCENT) {
            state = STATE_REFILL;
            return;
        }
        if (state == STATE_REFILL && paintPercent > 80) {
            state = STATE_EXPLORE;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] allies  = rc.senseNearbyRobots(-1, rc.getTeam());

        
        RobotInfo dyingAlly = findDyingAlly(allies);
        if (dyingAlly != null && paintPercent > 40) {
            state = STATE_TRANSFER;
            return;
        }

        
        MapLocation ruinWithEnemyPaint = findRuinWithEnemyPaint();
        if (ruinWithEnemyPaint != null) {
            mopTarget = ruinWithEnemyPaint;
            state = STATE_MOP_RUIN;
            
            broadcastRuin(ruinWithEnemyPaint);
            return;
        }

        
        if (enemies.length > 0) {
            combatTarget = selectCombatTarget(enemies);
            if (combatTarget != null) {
                state = STATE_COMBAT;
                return;
            }
        }

        
        MapLocation enemyPaint = findNearestEnemyPaint();
        if (enemyPaint != null) {
            mopTarget = enemyPaint;
            state = STATE_MOP_AREA;
            return;
        }

        
        if (state != STATE_TRANSFER && state != STATE_MOP_RUIN) {
            state = STATE_EXPLORE;
        }
    }

    
    
    

    private static void executeState() throws GameActionException {
        switch (state) {
            case STATE_MOP_RUIN:
                executeMopRuin();
                break;
            case STATE_COMBAT:
                executeCombat();
                break;
            case STATE_TRANSFER:
                executeTransfer();
                break;
            case STATE_MOP_AREA:
                executeMopArea();
                break;
            case STATE_REFILL:
                refillPaint();
                break;
            case STATE_EXPLORE:
            default:
                executeExplore();
                break;
        }
    }

    
    
    

    private static void executeMopRuin() throws GameActionException {
        if (mopTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(mopTarget);

        
        if (dist <= 2) {
            if (rc.isActionReady() && rc.canAttack(mopTarget)) {
                rc.attack(mopTarget);

                
                mopTarget = findRuinWithEnemyPaint();
                if (mopTarget == null) {
                    state = STATE_EXPLORE;
                }
            }
        } else {
            
            if (rc.isMovementReady()) {
                moveGreedy(mopTarget);
            }
        }
    }

    
    
    

    private static void executeCombat() throws GameActionException {
        if (combatTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(combatTarget);

        
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        boolean targetStillExists = false;
        for (RobotInfo e : enemies) {
            if (e.getLocation().equals(combatTarget)) {
                targetStillExists = true;
                break;
            }
        }

        if (!targetStillExists) {
            
            if (enemies.length > 0) {
                combatTarget = selectCombatTarget(enemies);
            } else {
                combatTarget = null;
                state = STATE_EXPLORE;
                return;
            }
        }

        
        if (rc.isActionReady() && enemies.length >= 2) {
            Direction swingDir = findBestSwingDirection(enemies);
            if (swingDir != null && rc.canMopSwing(swingDir)) {
                rc.mopSwing(swingDir);
                return;
            }
        }

        
        if (dist <= 2) {
            if (rc.isActionReady() && rc.canAttack(combatTarget)) {
                rc.attack(combatTarget);
                return;
            }
        }

        
        if (rc.isMovementReady()) {
            moveGreedy(combatTarget);
        }
    }

    
    
    

    private static void executeTransfer() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo dyingAlly = findDyingAlly(allies);

        if (dyingAlly == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation allyLoc = dyingAlly.getLocation();
        int dist = rc.getLocation().distanceSquaredTo(allyLoc);

        if (dist <= 2) {
            
            if (rc.isActionReady()) {
                int allyNeed = dyingAlly.getType().paintCapacity / 2 - dyingAlly.getPaintAmount();
                int canGive  = rc.getPaint() - rc.getType().paintCapacity / 4; 
                int amount   = Math.min(allyNeed, canGive);

                if (amount > 0 && rc.canTransferPaint(allyLoc, amount)) {
                    rc.transferPaint(allyLoc, amount);
                }
            }
            state = STATE_EXPLORE;
        } else {
            if (rc.isMovementReady()) {
                moveGreedy(allyLoc);
            }
        }
    }

    
    
    

    private static void executeMopArea() throws GameActionException {
        if (mopTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(mopTarget);

        if (dist <= 2) {
            if (rc.isActionReady() && rc.canAttack(mopTarget)) {
                rc.attack(mopTarget);
                
                mopTarget = findNearestEnemyPaint();
                if (mopTarget == null) state = STATE_EXPLORE;
            }
        } else {
            if (rc.isMovementReady()) {
                moveGreedy(mopTarget);
            }
        }
    }

    
    
    

    private static void executeExplore() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            broadcastEnemyLocation(enemies[0].getLocation());
        }

        if (rc.isMovementReady()) {
            
            MapLocation mopReq = readBroadcastTarget(MSG_MOPPER_REQ);
            if (mopReq != null) {
                mopTarget = mopReq;
                state = STATE_MOP_AREA;
                moveGreedy(mopReq);
                return;
            }

            
            MapLocation enemyLoc = readBroadcastTarget(MSG_ENEMY_LOC);
            if (enemyLoc != null) {
                moveGreedy(enemyLoc);
                return;
            }

            explore();
        }
    }

    
    
    

    
    private static RobotInfo findDyingAlly(RobotInfo[] allies) {
        RobotInfo worst = null;
        int minPaint = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            
            if (isTowerType(ally.getType())) continue;

            int paintPercent = (ally.getPaintAmount() * 100);
            if (ally.getType().paintCapacity > 0) {
                paintPercent /= ally.getType().paintCapacity;
            }

            
            if (paintPercent < 20 && ally.getPaintAmount() < minPaint) {
                minPaint = ally.getPaintAmount();
                worst = ally;
            }
        }
        return worst;
    }

    
    private static MapLocation findRuinWithEnemyPaint() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();

            
            MapInfo[] ruinArea = rc.senseNearbyMapInfos(ruinLoc, 8);
            for (MapInfo ruinTile : ruinArea) {
                if (!ruinTile.getPaint().isAlly() &&
                    ruinTile.getPaint() != PaintType.EMPTY &&
                    ruinTile.isPassable()) {
                    
                    int dist = myLoc.distanceSquaredTo(ruinTile.getMapLocation());
                    if (dist < minDist) {
                        minDist = dist;
                        best = ruinTile.getMapLocation();
                    }
                    break;
                }
            }
        }
        return best;
    }

    
    private static MapLocation findNearestEnemyPaint() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        int minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (!paint.isAlly() && paint != PaintType.EMPTY) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < minDist) {
                    minDist = dist;
                    best = tile.getMapLocation();
                }
            }
        }
        return best;
    }

    
    private static MapLocation selectCombatTarget(RobotInfo[] enemies) {
        RobotInfo best = null;
        int minPaint = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (isTowerType(enemy.getType())) continue; 
            if (enemy.getPaintAmount() < minPaint) {
                minPaint = enemy.getPaintAmount();
                best = enemy;
            }
        }

        
        if (best == null && enemies.length > 0) {
            best = enemies[0];
        }
        return best != null ? best.getLocation() : null;
    }

    
    private static Direction findBestSwingDirection(RobotInfo[] enemies) throws GameActionException {
        Direction bestDir = null;
        int maxHits = 0;

        for (Direction dir : cardinalDirs) {
            if (!rc.canMopSwing(dir)) continue;

            
            
            MapLocation myLoc = rc.getLocation();
            int hits = 0;

            MapLocation step1 = myLoc.add(dir);
            MapLocation step2 = step1.add(dir);

            for (RobotInfo enemy : enemies) {
                MapLocation eLoc = enemy.getLocation();
                
                if (eLoc.distanceSquaredTo(step1) <= 1 ||
                    eLoc.distanceSquaredTo(step2) <= 1) {
                    hits++;
                }
            }

            if (hits > maxHits) {
                maxHits = hits;
                bestDir = dir;
            }
        }

        
        return (maxHits >= 2) ? bestDir : null;
    }
}
