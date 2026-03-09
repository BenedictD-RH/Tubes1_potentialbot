package algorithm.Units;

import algorithm.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;

public class Soldier extends Unit {
    
    public static void run() throws GameActionException {
        // 1. SENSING: Pindai semua informasi ubin/petak di dalam radius penglihatan
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1); 
        
        MapLocation bestTileToPaint = null;
        int minDistance = Integer.MAX_VALUE;

        // 2. GREEDY EVALUATION: Cari petak terdekat yang bukan warna tim kita
        for (MapInfo tile : nearbyTiles) {
                PaintType paint = tile.getPaint();
                if (!paint.isAlly() && tile.isPassable()) {
                int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                
                // Pilih yang jaraknya paling kecil (Greedy)
                if (dist < minDistance) {
                    minDistance = dist;
                    bestTileToPaint = tile.getMapLocation();
                }
            }
        }

        // 3. ACTION & MOVEMENT
        if (bestTileToPaint != null) {
            // Jika senjata sudah siap, kita punya cat, dan target ada di jangkauan
            if (rc.isActionReady() && rc.canAttack(bestTileToPaint)) {
                rc.attack(bestTileToPaint); // Tembakkan cat!
            } 
            // Jika di luar jangkauan, kejar petak tersebut
            else if (rc.isMovementReady()) {
                move(bestTileToPaint);
            }
        } 
        // 4. Jika area sekitar sudah dicat semua tim kita, eksplorasi cari area baru
        else if (rc.isMovementReady()) {
            wander();
        }
    }

    private static void wander() throws GameActionException {
        int randomIndex = (int) (Math.random() * directions.length);
        Direction randomDir = directions[randomIndex];
        
        if (rc.canMove(randomDir)) {
            rc.move(randomDir);
        }
    }
}