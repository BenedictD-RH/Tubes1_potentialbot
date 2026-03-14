package alternative_bots_2.Units;

import alternative_bots_2.Unit;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Splasher extends Unit {

    private static final int STATE_ATTACK_TOWER = 0;
    private static final int STATE_AREA_PAINT   = 1;
    private static final int STATE_OVERWRITE    = 2;
    private static final int STATE_REFILL       = 3;
    private static final int STATE_EXPLORE      = 4;

    private static int state = STATE_EXPLORE;
    private static MapLocation splashTarget = null;  
    private static MapLocation enemyTowerTarget = null;

    
    private static final int MIN_SPLASH_SCORE = 3;

    public static void run() throws GameActionException {
        initUnit();
        scanEnvironment();
        processMessages();

        
        updateState();

        
        executeState();
    }

    
    
    

    private static void updateState() throws GameActionException {
        int paintPercent = (rc.getPaint() * 100) / rc.getType().paintCapacity;

        
        if (paintPercent <= 25) {
            state = STATE_REFILL;
            return;
        }
        if (state == STATE_REFILL && paintPercent > 75) {
            state = STATE_EXPLORE;
        }

        
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo enemyTower = findEnemyTower(enemies);
        if (enemyTower != null) {
            enemyTowerTarget = enemyTower.getLocation();
            state = STATE_ATTACK_TOWER;
            return;
        }

        
        MapLocation bestOverwrite = findBestSplashTarget(true);
        if (bestOverwrite != null) {
            splashTarget = bestOverwrite;
            state = STATE_OVERWRITE;
            return;
        }

        
        MapLocation bestPaint = findBestSplashTarget(false);
        if (bestPaint != null) {
            splashTarget = bestPaint;
            state = STATE_AREA_PAINT;
            return;
        }

        
        state = STATE_EXPLORE;
    }

    
    
    

    private static void executeState() throws GameActionException {
        switch (state) {
            case STATE_ATTACK_TOWER:
                executeAttackTower();
                break;
            case STATE_OVERWRITE:
            case STATE_AREA_PAINT:
                executeAreaSplash();
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

    
    
    

    private static void executeAttackTower() throws GameActionException {
        if (enemyTowerTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        
        if (rc.canSenseLocation(enemyTowerTarget)) {
            if (!rc.canSenseRobotAtLocation(enemyTowerTarget)) {
                enemyTowerTarget = null;
                state = STATE_EXPLORE;
                return;
            }
        }

        MapLocation myLoc = rc.getLocation();

        
        
        if (rc.isActionReady() && rc.canAttack(enemyTowerTarget)) {
            rc.attack(enemyTowerTarget);
            broadcastEnemyTower(enemyTowerTarget);
            return;
        }

        
        if (rc.isMovementReady()) {
            
            
            int dist = myLoc.distanceSquaredTo(enemyTowerTarget);
            if (dist > 4) {
                moveGreedy(enemyTowerTarget);
            } else if (dist <= 2) {
                
                retreatFrom(enemyTowerTarget);
            }
        }
    }

    
    
    

    private static void executeAreaSplash() throws GameActionException {
        if (splashTarget == null) {
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();

        
        if (rc.isActionReady() && rc.canAttack(splashTarget)) {
            rc.attack(splashTarget);
            
            splashTarget = findBestSplashTarget(state == STATE_OVERWRITE);
            if (splashTarget == null) state = STATE_EXPLORE;
            return;
        }

        
        if (rc.isMovementReady()) {
            int dist = myLoc.distanceSquaredTo(splashTarget);
            if (dist <= 4) {
                
                moveTowardForSplash(splashTarget);
            } else {
                moveGreedy(splashTarget);
            }
        }
    }

    
    
    

    private static void executeExplore() throws GameActionException {
        
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            broadcastEnemyLocation(enemies[0].getLocation());
        }

        if (rc.isMovementReady()) {
            
            MapLocation towerLoc = readBroadcastTarget(MSG_ENEMY_TOWER);
            if (towerLoc != null) {
                enemyTowerTarget = towerLoc;
                state = STATE_ATTACK_TOWER;
                moveGreedy(towerLoc);
                return;
            }

            
            MapLocation enemyLoc = readBroadcastTarget(MSG_ENEMY_LOC);
            if (enemyLoc != null) {
                splashTarget = enemyLoc;
                state = STATE_OVERWRITE;
                moveGreedy(enemyLoc);
                return;
            }

            
            MapLocation unpaintedArea = findUnpaintedArea();
            if (unpaintedArea != null) {
                moveGreedy(unpaintedArea);
            } else {
                explore();
            }
        }
    }

    
    
    

    
    private static MapLocation findBestSplashTarget(boolean preferEnemy) throws GameActionException {
        
        if (Clock.getBytecodesLeft() < 2000) return null;

        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        MapLocation bestLoc = null;
        int bestScore = MIN_SPLASH_SCORE - 1; 

        for (MapInfo tile : nearbyTiles) {
            if (!tile.isPassable()) continue;
            MapLocation loc = tile.getMapLocation();

            
            if (myLoc.distanceSquaredTo(loc) > 4) continue;
            if (!rc.canAttack(loc)) continue;

            if (Clock.getBytecodesLeft() < 1000) break; 

            int score = evaluateSplashScore(loc, preferEnemy);

            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
            }
        }

        return bestLoc;
    }

    
    private static int evaluateSplashScore(MapLocation center, boolean preferEnemy)
            throws GameActionException {
        int score = 0;

        MapInfo[] splashArea = rc.senseNearbyMapInfos(center, 2);
        for (MapInfo t : splashArea) {
            if (!t.isPassable()) continue;

            PaintType paint = t.getPaint();

            
            if (rc.canSenseRobotAtLocation(t.getMapLocation())) {
                RobotInfo robot = rc.senseRobotAtLocation(t.getMapLocation());
                if (robot != null && robot.getTeam() != rc.getTeam()) {
                    if (isTowerType(robot.getType())) {
                        score += 10; 
                    } else {
                        score += 4;  
                    }
                    continue;
                }
            }

            if (!paint.isAlly()) {
                if (paint == PaintType.EMPTY) {
                    score += 1; 
                } else {
                    
                    score += preferEnemy ? 4 : 2; 
                }
            }
            
        }

        return score;
    }

    
    
    

    
    private static RobotInfo findEnemyTower(RobotInfo[] enemies) {
        RobotInfo best = null;
        int minHP = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (isTowerType(enemy.getType()) && enemy.health < minHP) {
                minHP = enemy.health;
                best = enemy;
            }
        }
        return best;
    }

    
    private static void retreatFrom(MapLocation target) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        
        Direction awayDir = target.directionTo(myLoc);
        if (awayDir != null && rc.canMove(awayDir)) {
            rc.move(awayDir);
        } else {
            
            for (Direction dir : directions) {
                MapLocation next = myLoc.add(dir);
                if (rc.canMove(dir) && next.distanceSquaredTo(target) > myLoc.distanceSquaredTo(target)) {
                    rc.move(dir);
                    return;
                }
            }
            wander();
        }
    }

    
    private static void moveTowardForSplash(MapLocation target) throws GameActionException {
        
        if (rc.canAttack(target)) return;

        
        moveGreedy(target);
    }

    
    private static MapLocation findUnpaintedArea() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation best = null;
        int maxDist = 0;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            if (tile.getPaint() == PaintType.EMPTY) {
                MapLocation loc = tile.getMapLocation();
                int dist = loc.distanceSquaredTo(spawnLocation != null ? spawnLocation : rc.getLocation());
                if (dist > maxDist) {
                    maxDist = dist;
                    best = loc;
                }
            }
        }
        return best;
    }
}
