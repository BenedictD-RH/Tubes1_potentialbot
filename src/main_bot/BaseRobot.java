package main_bot;

import battlecode.common.Direction;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class BaseRobot {
    // Referensi ke RobotController, digunakan oleh semua subclass
    public static RobotController rc;

    // Fungsi untuk menyimpan 8 arah pergerakan (N, NE, E, SE, S, SW, W, NW)
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

    // Fungsi untuk menyimpan 4 arah kardinal (N, E, S, W) untuk mop swing dan orbit ruin
    static public final Direction[] cardinalDirs = {
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST,
    };

    // Daftar semua tipe tower (Paint, Money, Defense) level 1-3
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

    // Daftar tipe unit mobile (Soldier, Mopper, Splasher)
    static public final UnitType[] robotTypes = {
        UnitType.SOLDIER,
        UnitType.MOPPER,
        UnitType.SPLASHER,
    };

    // Konstanta protokol pesan 32-bit: type(3 bit) | extra(11 bit) | x(6 bit) | y(6 bit)
    public static final int MSG_PREFIX_SHIFT = 29;
    public static final int MSG_PREFIX_MASK = 0x7;

    // Kode tipe pesan untuk komunikasi antar robot
    public static final int MSG_ENEMY_LOC = 1;      // Lokasi musuh terdeteksi
    public static final int MSG_TOWER_LOC = 2;       // Lokasi tower ally
    public static final int MSG_MOPPER_REQ = 3;      // Request mopper ke area tertentu
    public static final int MSG_RUIN_LOC = 4;        // Ruin kosong ditemukan
    public static final int MSG_ENEMY_TOWER = 5;     // Tower musuh terdeteksi
    public static final int MSG_RUIN_READY = 6;      // Pattern ruin penuh, butuh chip
    public static final int MSG_PAINT_TOWER = 7;     // Lokasi paint tower terdekat

    // Konstanta encoding koordinat (6 bit per koordinat, max 63)
    public static final int COORD_BITS = 6;
    public static final int COORD_MASK = 0x3F;

    // Konstanta encoding data tambahan (11 bit, untuk round number dll)
    public static final int EXTRA_SHIFT = 12;
    public static final int EXTRA_MASK = 0x7FF;

    // Konstanta fase permainan (early, mid, late game)
    public static final int EARLY_GAME_END = 150;
    public static final int MID_GAME_END = 600;

    // Konstanta gameplay untuk threshold paint dan chip
    public static final int LOW_PAINT_PERCENT = 20;    // Threshold paint rendah
    public static final int REFILL_PAINT_PERCENT = 90; // Target refill paint
    public static final int MIN_CHIPS_TOWER = 1000;    // Minimum chip untuk bangun tower
    public static final int MIN_CHIPS_SPAWN = 300;     // Minimum chip untuk spawn unit     

    // Fungsi untuk mengenkode pesan 32-bit dari tipe, koordinat, dan data tambahan
    public static int encodeMessage(int type, int x, int y, int extra) {
        return (type << MSG_PREFIX_SHIFT)
             | ((extra & EXTRA_MASK) << EXTRA_SHIFT)
             | ((x & COORD_MASK) << COORD_BITS)
             | (y & COORD_MASK);
    }

    // Fungsi untuk mendekode tipe pesan dari integer 32-bit
    public static int decodeType(int msg) {
        return (msg >> MSG_PREFIX_SHIFT) & MSG_PREFIX_MASK;
    }

    // Fungsi untuk mendekode koordinat X dari pesan
    public static int decodeX(int msg) {
        return (msg >> COORD_BITS) & COORD_MASK;
    }

    // Fungsi untuk mendekode koordinat Y dari pesan
    public static int decodeY(int msg) {
        return msg & COORD_MASK;
    }

    // Fungsi untuk mendekode data tambahan (extra) dari pesan
    public static int decodeExtra(int msg) {
        return (msg >> EXTRA_SHIFT) & EXTRA_MASK;
    }

    // Fungsi untuk mengecek apakah unit adalah tower (semua level dan tipe)
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

    // Fungsi untuk mengecek apakah unit adalah paint tower (level 1-3)
    public static boolean isPaintTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_PAINT_TOWER
            || type == UnitType.LEVEL_TWO_PAINT_TOWER
            || type == UnitType.LEVEL_THREE_PAINT_TOWER;
    }

    // Fungsi untuk mengecek apakah unit adalah money tower (level 1-3)
    public static boolean isMoneyTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_MONEY_TOWER
            || type == UnitType.LEVEL_TWO_MONEY_TOWER
            || type == UnitType.LEVEL_THREE_MONEY_TOWER;
    }

    // Fungsi untuk mengecek apakah unit adalah defense tower (level 1-3)
    public static boolean isDefenseTower(UnitType type) {
        return type == UnitType.LEVEL_ONE_DEFENSE_TOWER
            || type == UnitType.LEVEL_TWO_DEFENSE_TOWER
            || type == UnitType.LEVEL_THREE_DEFENSE_TOWER;
    }
}
