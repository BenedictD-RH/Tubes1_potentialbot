package algorithm.Units;

import algorithm.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;



public class Splasher extends Unit {
    
    public static void run() throws GameActionException{
         // Pindai semua informasi ubin/petak di dalam radius penglihatan
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        MapLocation bestSplashTarget = null;
        int maxPaintableTiles = 0;
        MapLocation closestUnpainted = null;
        int minDistance = Integer.MAX_VALUE;

        // mencari petak yang kosong
        for (MapInfo tile : nearbyTiles){
            MapLocation loc = tile.getMapLocation();

            // cek pergerakan atau lokasi terdekat
            if (!tile.getPaint().isAlly() && tile.isPassable()) {
                int dist = rc.getLocation().distanceSquaredTo(loc);
                if(dist < minDistance){
                    minDistance = dist;
                    closestUnpainted = loc;
                }
            }

            if (rc.canAttack((loc))){
                int score = evaluateSplashTarget(loc);
                if(score > maxPaintableTiles) {
                    maxPaintableTiles = score;
                    bestSplashTarget = loc;
                }
            }
        }

        //  mewarnai minimal 2 petak 
        if (bestSplashTarget != null && maxPaintableTiles >= 2 && rc.isActionReady()) {
            rc.attack(bestSplashTarget);
        } 
        // mendektati petak kosong terdekat
        else if (closestUnpainted != null && rc.isMovementReady()) {
            move(closestUnpainted);
        } 
        // eksplorasi
        else if (rc.isMovementReady()) {
            wander();
        }
    }

    private static int evaluateSplashTarget (MapLocation center) throws GameActionException {
        int score = 0;
        MapInfo[] splashArea = rc.senseNearbyMapInfos(center, 2);
        for(MapInfo tile : splashArea) {
            if (!tile.getPaint().isAlly() && tile.isPassable()){
                score++;
            }
        }
        return score;
    }

    private static void wander() throws GameActionException {
        int randomIndex = (int) (Math.random() * directions.length);
        Direction randomDir = directions[randomIndex];
        if( rc.canMove(randomDir)) {
            rc.move(randomDir);
        }
    }
}
