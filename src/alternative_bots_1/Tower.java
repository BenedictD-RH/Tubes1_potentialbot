package alternative_bots_1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower extends BaseRobot {
    private static int lastMoney = 0; // Untuk mengetahui uang terakhir
    private static int stagnantRounds = 0;

    public static void run() throws GameActionException {
        if (rc.getType() == UnitType.LEVEL_ONE_DEFENSE_TOWER ||
            rc.getType() == UnitType.LEVEL_TWO_DEFENSE_TOWER ||
            rc.getType() == UnitType.LEVEL_THREE_DEFENSE_TOWER) {
            
            RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemies.length > 0 && rc.isActionReady()) {
                MapLocation targetEnemy = enemies[0].getLocation();
                if (rc.canAttack(targetEnemy)) {
                    rc.attack(targetEnemy);
                    return;
                }
            }
        }

        // Untuk Upgrade Tower
        if (rc.isActionReady() && rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
            return;
        }

        // Produksi Pasukan
        if (rc.isActionReady()) {
            int currentMoney = rc.getMoney();
            
            // cek apakah uang cukup namun tidak digunakan
            if (currentMoney >= lastMoney && currentMoney > 1000) {
                stagnantRounds++;
            } else {
                stagnantRounds = 0; 
            }
            lastMoney = currentMoney;

            // Batasan kapasitas tabungan
            int MAX_BANK_CAPACITY = 3500;

            // KONDISI SPAWN ROBOT:
            // 1. Awal permainan (Ronde < 150) 
            // 2. Stagnasi jalan buntu (> 100 ronde tanpa pengeluaran)
            // 3. UANG MELEBIHI kapasitas maksimal total
            if ((rc.getRoundNum() < 150 && currentMoney >= 500) || 
                stagnantRounds > 100 || 
                currentMoney > MAX_BANK_CAPACITY) {
                
                buildStrategicArmy();
                stagnantRounds = 0; 
            }
        }
    }

    // Membuat Pasukan
    private static void buildStrategicArmy() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        
        int allyPaint = 0;
        int enemyPaint = 0;
        
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isAlly()) allyPaint++;
            else if (tile.getPaint().isEnemy()) enemyPaint++;
        }

        boolean isUnderThreat = enemies.length > 0 || enemyPaint > 15;
        boolean isAreaLarge = rc.getNumberTowers() >= 7 || allyPaint > 45;

        double rand = Math.random();
        UnitType typeToBuild;

        if (isUnderThreat) {
            if (rand < 0.55) {
                typeToBuild = UnitType.SOLDIER;
            } else if (rand < 0.95) {
                typeToBuild = UnitType.MOPPER;
            } else {
                typeToBuild = UnitType.SPLASHER;
            }
        } 
        else if (isAreaLarge) {
            if (rand < 0.50) {
                typeToBuild = UnitType.SOLDIER;
            } else if (rand < 0.70) {
                typeToBuild = UnitType.MOPPER;
            } else {
                typeToBuild = UnitType.SPLASHER;
            }
        } 
        else {
            if (rand < 0.75) {
                typeToBuild = UnitType.SOLDIER;
            } else if (rand < 0.90) {
                typeToBuild = UnitType.MOPPER;
            } else {
                typeToBuild = UnitType.SPLASHER;
            }
        }

        for (Direction dir : directions) {
            MapLocation spawnLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(typeToBuild, spawnLoc)) {
                rc.buildRobot(typeToBuild, spawnLoc);
                break;
            }
        }
    }
}