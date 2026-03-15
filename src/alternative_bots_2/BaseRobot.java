package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class BaseRobot {
    public static RobotController rc;

    
    static public final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    
    static public final Direction[] cardinalDirs = {
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST,
    };

    static public final UnitType[] towerTypes = {
        UnitType.LEVEL_ONE_PAINT_TOWER,
        UnitType.LEVEL_ONE_MONEY_TOWER,
        UnitType.LEVEL_ONE_DEFENSE_TOWER,
        UnitType.LEVEL_TWO_PAINT_TOWER,
        UnitType.LEVEL_TWO_MONEY_TOWER,
        UnitType.LEVEL_TWO_DEFENSE_TOWER,
        UnitType.LEVEL_THREE_PAINT_TOWER,
        UnitType.LEVEL_THREE_MONEY_TOWER,
        UnitType.LEVEL_THREE_DEFENSE_TOWER,
    };

    static public final UnitType[] robotTypes = {
        UnitType.SOLDIER,
        UnitType.MOPPER,
        UnitType.SPLASHER,
    };

    
    
    public static final int MSG_PREFIX_SHIFT = 29;
    public static final int MSG_PREFIX_MASK = 0x7; 

    
    public static final int MSG_ENEMY_LOC = 1;      
    public static final int MSG_TOWER_LOC = 2;       
    public static final int MSG_MOPPER_REQ = 3;      
    public static final int MSG_RUIN_LOC = 4;        
    public static final int MSG_ENEMY_TOWER = 5;     

    
    public static final int COORD_BITS = 6;
    public static final int COORD_MASK = 0x3F; 

    
    public static final int EXTRA_SHIFT = 12;
    public static final int EXTRA_MASK = 0x7FF; 

    
    public static final int EARLY_GAME_END = 150;    
    public static final int MID_GAME_END = 600;      

    
    public static final int LOW_PAINT_PERCENT = 20;   
    public static final int REFILL_PAINT_PERCENT = 90;
    public static final int MIN_CHIPS_TOWER = 1000;    
    public static final int MIN_CHIPS_SPAWN = 300;     

    
    public static int encodeMessage(int type, int x, int y, int extra) {
        return (type << MSG_PREFIX_SHIFT)
             | ((extra & EXTRA_MASK) << EXTRA_SHIFT)
             | ((x & COORD_MASK) << COORD_BITS)
             | (y & COORD_MASK);
    }

    
    public static int decodeType(int msg) {
        return (msg >> MSG_PREFIX_SHIFT) & MSG_PREFIX_MASK;
    }

    
    public static int decodeX(int msg) {
        return (msg >> COORD_BITS) & COORD_MASK;
    }

    
    public static int decodeY(int msg) {
        return msg & COORD_MASK;
    }

    
    public static int decodeExtra(int msg) {
        return (msg >> EXTRA_SHIFT) & EXTRA_MASK;
    }

    
    public static boolean isTowerType(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER
            || type == UnitType.LEVEL_ONE_MONEY_TOWER
            || type == UnitType.LEVEL_TWO_MONEY_TOWER
            || type == UnitType.LEVEL_THREE_MONEY_TOWER
            || type == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }

    
    public static boolean isPaintTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    
    public static boolean isMoneyTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_MONEY_TOWER
            || type == UnitType.LEVEL_TWO_MONEY_TOWER
            || type == UnitType.LEVEL_THREE_MONEY_TOWER;
    }

    
    public static boolean isDefenseTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }
}
