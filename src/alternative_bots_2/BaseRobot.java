package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class BaseRobot {
    public static RobotController rc;

    // 8 arah gerakan
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

    // 4 arah kardinal (untuk mopper swing)
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

    // === Konstanta Komunikasi ===
    // Prefix 3-bit untuk tipe pesan (bit 29-31)
    public static final int MSG_PREFIX_SHIFT = 29;
    public static final int MSG_PREFIX_MASK = 0x7; // 3 bit

    // Tipe pesan
    public static final int MSG_ENEMY_LOC = 1;      // Lokasi musuh
    public static final int MSG_TOWER_LOC = 2;       // Lokasi tower sekutu
    public static final int MSG_MOPPER_REQ = 3;      // Request mopper (ada paint musuh)
    public static final int MSG_RUIN_LOC = 4;        // Lokasi ruin kosong
    public static final int MSG_ENEMY_TOWER = 5;     // Lokasi tower musuh

    // Koordinat: 6 bit x, 6 bit y (cukup untuk map 60x60)
    public static final int COORD_BITS = 6;
    public static final int COORD_MASK = 0x3F; // 6 bit = 0-63

    // Extra data: 11 bit (bit 12-22) untuk info tambahan
    public static final int EXTRA_SHIFT = 12;
    public static final int EXTRA_MASK = 0x7FF; // 11 bit

    // === Konstanta Game Phase ===
    public static final int EARLY_GAME_END = 150;    // Ronde akhir early game
    public static final int MID_GAME_END = 600;      // Ronde akhir mid game

    // === Resource Thresholds ===
    public static final int LOW_PAINT_PERCENT = 20;   // Persen paint dianggap rendah
    public static final int REFILL_PAINT_PERCENT = 30; // Persen paint mulai refill
    public static final int MIN_CHIPS_TOWER = 1000;    // Minimum chips untuk build tower
    public static final int MIN_CHIPS_SPAWN = 300;     // Minimum chips untuk spawn unit

    // === Helper: Encode pesan ===
    public static int encodeMessage(int type, int x, int y, int extra) {
        return (type << MSG_PREFIX_SHIFT)
             | ((extra & EXTRA_MASK) << EXTRA_SHIFT)
             | ((x & COORD_MASK) << COORD_BITS)
             | (y & COORD_MASK);
    }

    // === Helper: Decode tipe pesan ===
    public static int decodeType(int msg) {
        return (msg >> MSG_PREFIX_SHIFT) & MSG_PREFIX_MASK;
    }

    // === Helper: Decode koordinat X ===
    public static int decodeX(int msg) {
        return (msg >> COORD_BITS) & COORD_MASK;
    }

    // === Helper: Decode koordinat Y ===
    public static int decodeY(int msg) {
        return msg & COORD_MASK;
    }

    // === Helper: Decode extra data ===
    public static int decodeExtra(int msg) {
        return (msg >> EXTRA_SHIFT) & EXTRA_MASK;
    }

    // === Helper: Cek apakah UnitType adalah tower ===
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

    // === Helper: Cek apakah tower tipe paint ===
    public static boolean isPaintTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    // === Helper: Cek apakah tower tipe money ===
    public static boolean isMoneyTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_MONEY_TOWER
            || type == UnitType.LEVEL_TWO_MONEY_TOWER
            || type == UnitType.LEVEL_THREE_MONEY_TOWER;
    }

    // === Helper: Cek apakah tower tipe defense ===
    public static boolean isDefenseTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }
}
