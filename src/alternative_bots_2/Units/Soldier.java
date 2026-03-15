package alternative_bots_2.Units;

import alternative_bots_2.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Soldier extends Unit {
   
    private static final int STATE_EXPLORE     = 0;  
    private static final int STATE_GOTO_RUIN   = 1;  
    private static final int STATE_BUILD_TOWER = 2;  
    private static final int STATE_REFILL      = 3;  
    private static final int STATE_EXPAND      = 4;  
    private static final int STATE_COMBAT      = 5;  

    private static int state = STATE_EXPLORE;
 
    private static MapLocation ruinTarget   = null;  
    private static MapLocation paintTarget  = null;  
    private static MapLocation expandTarget = null;
    private static MapLocation combatTarget = null;

    
    private static MapLocation lastPos  = null;
    private static int posCount = 0;

    
    private static boolean patternMarked = false;

    
    private static final int REFILL_WHILE_BUILDING = 40;  
    private static final int REFILL_WHILE_EXPLORE  = 25;  

    // Fungsi menjalankan
    public static void run() throws GameActionException {
        initUnit();
        scanEnvironment();
        processMessages();

        detectStuck();
        selectState();
        executeState();
    }

    // Fungsi memilih state
    private static void selectState() throws GameActionException {
        int paint    = rc.getPaint();
        int capacity = rc.getType().paintCapacity;
        int pct      = (paint * 100) / capacity;
        int chips    = rc.getMoney();
        int towers   = rc.getNumberTowers();
        int refillThreshold = (state == STATE_BUILD_TOWER || state == STATE_GOTO_RUIN)
                            ? REFILL_WHILE_BUILDING : REFILL_WHILE_EXPLORE;

        if (pct <= refillThreshold) {
            if (state != STATE_REFILL) {
                returnLocation = ruinTarget;  
            }
            state = STATE_REFILL;
            return;
        }
        
        if (state == STATE_REFILL && pct > 70) {
            if (returnLocation != null) {
                ruinTarget    = returnLocation;
                patternMarked = false;  
                returnLocation = null;
                state = STATE_GOTO_RUIN;
            } else {
                state = STATE_EXPLORE;
            }
        }
        
        if (state == STATE_BUILD_TOWER || state == STATE_GOTO_RUIN) {
            
            if (ruinTarget != null && rc.canSenseLocation(ruinTarget)) {
                if (isTowerBuiltAt(ruinTarget)) {
                    removeKnownRuin(ruinTarget);
                    ruinTarget    = null;
                    patternMarked = false;
                    paintTarget   = null;
                    state = STATE_EXPLORE;
                    return;
                }
            }
            
            return;
        }
        
        MapLocation nearRuin = findNearbyEmptyRuin();
        if (nearRuin != null && chips >= chipsRequired(towers)) {
            ruinTarget    = nearRuin;
            patternMarked = false;
            paintTarget   = null;
            state = STATE_BUILD_TOWER;
            // Broadcast ruin 
            broadcastRuin(nearRuin);
            return;
        }
        MapLocation memRuin = findAssignedRuin();
        if (memRuin != null && chips >= chipsRequired(towers)) {
            ruinTarget    = memRuin;
            patternMarked = false;
            paintTarget   = null;
            state = STATE_GOTO_RUIN;
            return;
        }
        
        if (towers >= 4 && pct > 50) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo e : enemies) {
                if (isTowerType(e.getType())) {
                    combatTarget = e.getLocation();
                    state = STATE_COMBAT;
                    return;
                }
            }
        }
        
        if (towers >= 3 && pct > 50) {
            state = STATE_EXPAND;
            return;
        }
        
        state = STATE_EXPLORE;
    }

    // Fungsi menjalankan state
    private static void executeState() throws GameActionException {
        switch (state) {
            case STATE_BUILD_TOWER: executeBuildTower(); break;
            case STATE_GOTO_RUIN:   executeGotoRuin();   break;
            case STATE_REFILL:      executeRefill();     break;
            case STATE_EXPAND:      executeExpand();     break;
            case STATE_COMBAT:      executeCombat();     break;
            default:                executeExplore();    break;
        }
    }

    // Fungsi menjalankan state BUILD_TOWER
    private static void executeBuildTower() throws GameActionException {
        if (ruinTarget == null) { state = STATE_EXPLORE; return; }
        int towers    = rc.getNumberTowers();
        UnitType type = chooseTowerType(towers);
        
        if (rc.canCompleteTowerPattern(type, ruinTarget)) {
            rc.completeTowerPattern(type, ruinTarget);
            if (isPaintTower(type)) addKnownPaintTower(ruinTarget);
            addKnownAllyTower(ruinTarget);
            removeKnownRuin(ruinTarget);
            ruinTarget    = null;
            patternMarked = false;
            paintTarget   = null;
            state = STATE_EXPLORE;
            return;
        }
        MapLocation myLoc = rc.getLocation();
        int distToRuin = myLoc.distanceSquaredTo(ruinTarget);
        
        if (!patternMarked && distToRuin <= 2) {
            if (rc.canMarkTowerPattern(type, ruinTarget)) {
                rc.markTowerPattern(type, ruinTarget);
                patternMarked = true;
            }
        }
        
        if (rc.isActionReady()) {
            
            MapLocation tileToGo = findBestPatternTile(ruinTarget);

            if (tileToGo != null) {
                if (rc.canAttack(tileToGo)) {
                    
                    PaintType mark = rc.senseMapInfo(tileToGo).getMark();
                    boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                    rc.attack(tileToGo, useSecondary);
                    paintTarget = null;
                } else {
                    
                    paintTarget = tileToGo;
                }
            }
        }
        
        if (rc.isMovementReady()) {
            if (paintTarget != null) {
                
                moveSimple(paintTarget);
                checkStuck();
            } else if (distToRuin > 2) {
                
                moveSimple(ruinTarget);
                checkStuck();
            }
            
        }
    }

    // Fungsi menjalankan state GOTO_RUIN
    private static void executeGotoRuin() throws GameActionException {
        if (ruinTarget == null) { state = STATE_EXPLORE; return; }
        
        if (rc.canSenseLocation(ruinTarget)) {
            if (isTowerBuiltAt(ruinTarget)) {
                removeKnownRuin(ruinTarget);
                ruinTarget = null;
                state = STATE_EXPLORE;
                return;
            }
            state = STATE_BUILD_TOWER;
            return;
        }
        
        if (rc.isMovementReady()) {
            moveSimple(ruinTarget);
            checkStuck();
        }
    }
    
    // Fungsi menjalankan state REFILL
    private static void executeRefill() throws GameActionException {
        if (!refillPaint()) {
            state = STATE_EXPLORE;
        }
    }
    
    // Fungsi menjalankan state EXPAND
    private static void executeExpand() throws GameActionException {
        MapLocation nearRuin = findNearbyEmptyRuin();
        if (nearRuin != null && rc.getMoney() >= chipsRequired(rc.getNumberTowers())) {
            ruinTarget    = nearRuin;
            patternMarked = false;
            paintTarget   = null;
            state = STATE_BUILD_TOWER;
            return;
        }
        // Untuk 20% bots, membuat SRP baru
        if (rc.getID() % 5 == 0 && rc.getPaint() > 100) {
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length == 0) {
                MapInfo[] nearTiles = rc.senseNearbyMapInfos(-1);
                for (MapInfo t : nearTiles) {
                    if (rc.canCompleteResourcePattern(t.getMapLocation())) {
                        rc.completeResourcePattern(t.getMapLocation());
                        return;
                    }
                }
                MapLocation myLoc = rc.getLocation();
                if (rc.canMarkResourcePattern(myLoc)) {
                    rc.markResourcePattern(myLoc);
                }
                if (rc.isActionReady()) {
                    for (MapInfo t : nearTiles) {
                        PaintType mark  = t.getMark();
                        PaintType paint = t.getPaint();
                        if (mark != PaintType.EMPTY && mark != paint && t.isPassable()) {
                            boolean sec = (mark == PaintType.ALLY_SECONDARY);
                            if (rc.canAttack(t.getMapLocation())) {
                                rc.attack(t.getMapLocation(), sec);
                                return;
                            }
                        }
                    }
                }
            }
        }
        // Paint tile terbaik dalam jangkauan attack 
        if (rc.isActionReady()) {
            MapLocation best = findBestExpandPaintTarget();
            if (best != null) {
                if (rc.canAttack(best)) {
                    PaintType mark = rc.senseMapInfo(best).getMark();
                    boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                    rc.attack(best, useSecondary);
                    return;
                } else {
                    expandTarget = best;
                }
            }
        }
        // bots bergerak
        if (rc.isMovementReady()) {
            if (expandTarget != null) {
                moveSimple(expandTarget);
                if (rc.getLocation().distanceSquaredTo(expandTarget) <= 1) expandTarget = null;
            } else {
                explore();
            }
        }
    }

    // Fungsi menjalankan state COMBAT
    private static void executeCombat() throws GameActionException {
        if (combatTarget == null) { state = STATE_EXPLORE; return; }

        if (rc.canSenseLocation(combatTarget) && !rc.canSenseRobotAtLocation(combatTarget)) {
            combatTarget = null;
            state = STATE_EXPLORE;
            return;
        }

        MapLocation myLoc = rc.getLocation();
        int dist = myLoc.distanceSquaredTo(combatTarget);

        if (dist <= 3 && rc.isActionReady() && rc.canAttack(combatTarget)) {
            rc.attack(combatTarget);
            if (rc.isMovementReady()) {
                Direction away = combatTarget.directionTo(myLoc);
                if (away != null && rc.canMove(away)) rc.move(away);
            }
            return;
        }
        if (rc.isMovementReady()) moveSimple(combatTarget);
    }

    // Fungsi menjalankan state EXPLORE
    private static void executeExplore() throws GameActionException {
        
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) broadcastEnemyLocation(enemies[0].getLocation());

        if (rc.isMovementReady()) {
            
            MapLocation ruinMsg = readBroadcastTarget(MSG_RUIN_LOC);
            if (ruinMsg != null) {
                addKnownRuin(ruinMsg);
                ruinTarget    = ruinMsg;
                patternMarked = false;
                state = STATE_GOTO_RUIN;
                moveSimple(ruinMsg);
                return;
            }
            explore();
        }
    }

    // Fungsi mencari rute terbaik
    private static MapLocation findBestPatternTile(MapLocation ruinCenter)
            throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        
        MapInfo[] tiles = rc.senseNearbyMapInfos(ruinCenter, 8);
        MapLocation best    = null;
        int         minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;

            PaintType mark  = tile.getMark();
            PaintType paint = tile.getPaint();

            
            if (mark == PaintType.EMPTY) continue;
            if (mark == paint) continue; 

            MapLocation loc  = tile.getMapLocation();
            int         dist = myLoc.distanceSquaredTo(loc);

            if (dist < minDist) {
                minDist = dist;
                best    = loc;
            }
        }
        return best;
    }

    // score setiap ruin berdasarkan kondisi yang ada
    private static MapLocation findAssignedRuin() {
        if (knownRuinCount == 0) return null;

        MapLocation myLoc = rc.getLocation();

        // Score setiap ruin
        MapLocation[] top   = new MapLocation[3];
        int[]         score = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};

        for (int i = 0; i < knownRuinCount; i++) {
            MapLocation ruin = knownRuins[i];
            int dist = myLoc.distanceSquaredTo(ruin);
            int s = -dist;

            // nilai dikurangi jika musuh diketahui dekat ruin ini 
            if (lastKnownEnemyLoc != null && rc.getRoundNum() - lastKnownEnemyRound < 60) {
                int enemyToRuin = ruin.distanceSquaredTo(lastKnownEnemyLoc);
                if (enemyToRuin < 100) {
                    s -= (100 - enemyToRuin) * 4;
                }
            }

            // Bonus kecil jika ruin lebih dekat ke ally territory
            if (spawnLocation != null) {
                int ruinDistFromSpawn = ruin.distanceSquaredTo(spawnLocation);
                int myDistFromSpawn   = myLoc.distanceSquaredTo(spawnLocation);
                if (ruinDistFromSpawn < myDistFromSpawn) {
                    s += 15;
                }
            }
            if (s > score[0]) {
                score[2] = score[1]; top[2] = top[1];
                score[1] = score[0]; top[1] = top[0];
                score[0] = s;        top[0] = ruin;
            } else if (s > score[1]) {
                score[2] = score[1]; top[2] = top[1];
                score[1] = s;        top[1] = ruin;
            } else if (s > score[2]) {
                score[2] = s;        top[2] = ruin;
            }
        }

        // Hitung slot terisi
        int filled = 0;
        for (int k = 0; k < 3; k++) if (top[k] != null) filled++;
        if (filled == 0) return null;
        int idx = rc.getID() % filled;
        return top[idx] != null ? top[idx] : top[0];
    }

    // Fungsi untuk mencari terdekat ruin
    private static MapLocation findNearbyEmptyRuin() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best  = null;
        int minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.hasRuin()) continue;
            MapLocation loc = tile.getMapLocation();
            if (isTowerBuiltAt(loc)) {
                removeKnownRuin(loc);  
                continue;
            }
            int dist = myLoc.distanceSquaredTo(loc);
            if (dist < minDist) { minDist = dist; best = loc; }
        }
        return best;
    }

    
    private static UnitType chooseTowerType(int totalTowers) throws GameActionException {
        if (totalTowers >= 12 && rc.getMoney() > 3000) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        
        return (totalTowers % 2 == 0)
            ? UnitType.LEVEL_ONE_MONEY_TOWER
            : UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    
    private static int chipsRequired(int totalTowers) {
        if (totalTowers < 4)  return 700;
        if (totalTowers < 10) return 800;
        return 1000;
    }

    
    private static MapLocation findBestExpandPaintTarget() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(3); 
        MapLocation myLoc = rc.getLocation();
        MapLocation bestEnemy = null, bestEmpty = null;
        int minED = Integer.MAX_VALUE, minEMD = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;
            MapLocation loc   = tile.getMapLocation();
            PaintType   paint = tile.getPaint();
            int         dist  = myLoc.distanceSquaredTo(loc);

            if (!paint.isAlly() && paint != PaintType.EMPTY && dist < minED) {
                minED = dist; bestEnemy = loc;
            }
            if (paint == PaintType.EMPTY && dist < minEMD) {
                minEMD = dist; bestEmpty = loc;
            }
        }
        return (bestEnemy != null) ? bestEnemy : bestEmpty;
    }

    
    private static boolean isTowerBuiltAt(MapLocation loc) throws GameActionException {
        if (!rc.canSenseLocation(loc)) return false;
        if (!rc.canSenseRobotAtLocation(loc)) return false;
        RobotInfo r = rc.senseRobotAtLocation(loc);
        return r != null && isTowerType(r.getType()) && r.getTeam() == rc.getTeam();
    }

    
    private static void detectStuck() {
        MapLocation cur = rc.getLocation();
        if (lastPos != null && cur.equals(lastPos)) {
            posCount++;
            if (posCount >= 6) {
                currentExploreDirection = null;
                exploreTarget           = null;
                posCount = 0;
                if (state == STATE_GOTO_RUIN || state == STATE_BUILD_TOWER) {
                    ruinTarget    = null;
                    patternMarked = false;
                    paintTarget   = null;
                    state = STATE_EXPLORE;
                }
            }
        } else {
            posCount = 0;
        }
        lastPos = cur;
    }

    private static void checkStuck() {
        
    }
}
