package algorithm.Units;

import algorithm.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;

public class Soldier extends Unit {
    
    public static void run() throws GameActionException {
        // Pindai semua informasi ubin/petak di dalam radius penglihatan
        initUnit();
        buildTower();
        if(refillPaint()){
            return;
        }
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1); 
        
        MapLocation bestTileToPaint = null;
        int minDistance = Integer.MAX_VALUE;

        // Cari petak terdekat yang bukan warna tim
        for (MapInfo tile : nearbyTiles) {
                PaintType paint = tile.getPaint();
                if (!paint.isAlly() && tile.isPassable()) {
                int dist = rc.getLocation().distanceSquaredTo(tile.getMapLocation());
                
                // Pilih yang jaraknya paling kecil 
                if (dist < minDistance) {
                    minDistance = dist;
                    bestTileToPaint = tile.getMapLocation();
                }
            }
        }

        if (bestTileToPaint != null) {
            // target ada di jangkauan
            if (rc.isActionReady() && rc.canAttack(bestTileToPaint)) {
                rc.attack(bestTileToPaint);
            } 
            // Jika di luar jangkauan
            else if (rc.isMovementReady()) {
                move(bestTileToPaint);
            }
        } 
        // eksplorasi cari area baru
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