package algorithm;

import battlecode.common.Direction;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class BaseRobot{
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

    public int widht;
    public int height;
    public int spawnTurn;

}