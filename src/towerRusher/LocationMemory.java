package towerRusher;

import battlecode.common.*;

public class LocationMemory {
    public int amountSaved;
    public MapLocation[] savedLocations = new MapLocation[20];

    public LocationMemory() {
        amountSaved = 0;
    }

    public boolean isLocationInMemory(MapLocation m) {
        boolean found = false;
        for (int i = 0; i < amountSaved; i++) {
            found = savedLocations[i].equals(m);
            if (found) break;
        }
        return found;
    }

    public boolean isLocationInMemory(int x, int y) {
        return isLocationInMemory(new MapLocation(x, y));
    }

    public void saveLocation(MapLocation m) {
        if (!isLocationInMemory(m)) {
            savedLocations[amountSaved] = m;
            amountSaved++;
        }
    }

    public void saveLocation(int x, int y) {
        saveLocation(new MapLocation(x, y));
    }

    public void removeLocation(MapLocation m) {
        if (isLocationInMemory(m)) {
            int i = 0;
            while (!savedLocations[i].equals(m) && i < amountSaved) {
                i++;
            }
            if (i >= amountSaved) {
                return;
            }
            amountSaved--;
            while (i < amountSaved) {
                savedLocations[i] = savedLocations[i + 1];
                i++;
            }
        }
    }

    public void removeLocation(int x, int y) {
        removeLocation(new MapLocation(x, y));
    }

    public MapLocation nearestLocationTo(MapLocation m) {
        MapLocation currNearest = savedLocations[0];
        for(int i = 0; i < amountSaved; i++) { 
            if(currNearest.distanceSquaredTo(m) > savedLocations[i].distanceSquaredTo(m)) {
                currNearest = savedLocations[i];
            }
        }
        return currNearest;
    }

    public void printLocations(String s) {
        System.out.print(s + "\n");
        for (int i = 0; i < amountSaved;i ++) {
            System.out.println("[" + savedLocations[i].x + ", " + savedLocations[i].y + "]");
        }
    }

    public boolean doMemoryIntersect(LocationMemory lm) {
        boolean found = false;
        for (int i = 0; i < this.amountSaved; i++) {
            found = lm.isLocationInMemory(savedLocations[i]);
            if (found) break;
        }
        return found;
    }

    public void printLocations() {
        printLocations("");
    }
}
