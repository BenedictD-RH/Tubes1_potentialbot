package alternative_bots_2.Units;

import alternative_bots_2.Unit;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Soldier - Unit utama untuk tower building, lalu ekspansi area.
 *
 * ========== FILOSOFI UTAMA ==========
 * FASE 1 (Tower Rush): Bangun semua tower yang bisa dibangun DULU.
 *   - Cari ruin → mark pattern → paint tile pattern → complete tower
 *   - TIDAK ada painting acak, TIDAK expand selama ada ruin
 *   - Hemat paint: hanya paint tile yang diperlukan untuk pattern
 *
 * FASE 2 (Expansion): Baru expand setelah tidak ada ruin yang bisa dibangun.
 *   - Paint area kosong/musuh secara sistematis
 *
 * ========== ALUR BUILD TOWER (kritis) ==========
 * Masalah utama sebelumnya: soldier bergerak ke ruin, tapi tile yang perlu
 * di-paint ada di seluruh 5x5 area. Attack radius soldier = sqrt(3) ≈ 1.73,
 * jadi dari satu posisi hanya bisa menjangkau beberapa tile.
 *
 * FIX: Bergerak ke TILE YANG PERLU DICAT, bukan ke ruin.
 * Dengan begitu soldier bisa cat semua tile dalam pattern secara efisien.
 *
 * ========== MANAJEMEN PAINT ==========
 * - Refill awal: sebelum mulai build tower, pastikan paint >= 50%
 * - Tidak paint tile acak saat berjalan ke ruin
 * - Hanya paint tile yang merupakan bagian tower pattern
 */
public class Soldier extends Unit {

    // === State machine ===
    private static final int STATE_EXPLORE     = 0;  // Jelajah cari ruin baru
    private static final int STATE_GOTO_RUIN   = 1;  // Jalan ke ruin target
    private static final int STATE_BUILD_TOWER = 2;  // Marking + painting pattern
    private static final int STATE_REFILL      = 3;  // Isi paint
    private static final int STATE_EXPAND      = 4;  // Warnai area (fase 2)
    private static final int STATE_COMBAT      = 5;  // Serang tower musuh

    private static int state = STATE_EXPLORE;

    // Target aktif
    private static MapLocation ruinTarget   = null;  // Ruin yang sedang dituju
    private static MapLocation paintTarget  = null;  // Tile pattern yang akan dicat
    private static MapLocation expandTarget = null;
    private static MapLocation combatTarget = null;

    // Anti-stuck
    private static MapLocation lastPos  = null;
    private static int         posCount = 0;

    // Flag: sudah mark pattern di ruin target?
    private static boolean patternMarked = false;

    // Threshold refill: lebih tinggi saat building agar tidak terputus
    private static final int REFILL_WHILE_BUILDING = 40;  // 40% = 80 paint
    private static final int REFILL_WHILE_EXPLORE  = 25;  // 25% = 50 paint

    public static void run() throws GameActionException {
        initUnit();
        scanEnvironment();
        processMessages();

        detectStuck();
        selectState();
        executeState();
    }

    // =========================================================================
    //  STATE SELECTION - Greedy: pilih state paling menguntungkan
    // =========================================================================

    private static void selectState() throws GameActionException {
        int paint    = rc.getPaint();
        int capacity = rc.getType().paintCapacity;
        int pct      = (paint * 100) / capacity;
        int chips    = rc.getMoney();
        int towers   = rc.getNumberTowers();

        // ----------------------------------------------------------------
        // P0: Jika baru spawn atau reset, cari ruin segera
        // ----------------------------------------------------------------

        // ----------------------------------------------------------------
        // P1: REFILL - Kritis
        // Threshold lebih tinggi saat building (jangan sampai terputus di tengah)
        // ----------------------------------------------------------------
        int refillThreshold = (state == STATE_BUILD_TOWER || state == STATE_GOTO_RUIN)
                            ? REFILL_WHILE_BUILDING : REFILL_WHILE_EXPLORE;

        if (pct <= refillThreshold) {
            if (state != STATE_REFILL) {
                returnLocation = ruinTarget;  // kembali ke ruin setelah refill
            }
            state = STATE_REFILL;
            return;
        }

        // Selesai refill?
        if (state == STATE_REFILL && pct > 70) {
            if (returnLocation != null) {
                ruinTarget    = returnLocation;
                patternMarked = false;  // perlu re-mark karena mungkin ada perubahan
                returnLocation = null;
                state = STATE_GOTO_RUIN;
            } else {
                state = STATE_EXPLORE;
            }
        }

        // ----------------------------------------------------------------
        // P2: BUILD_TOWER - Lanjutkan build jika sudah punya ruin target
        // ----------------------------------------------------------------
        if (state == STATE_BUILD_TOWER || state == STATE_GOTO_RUIN) {
            // Validasi ruin target masih aktif
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
            // Tetap di state saat ini
            return;
        }

        // ----------------------------------------------------------------
        // P3: Cari ruin - ada ruin dalam sensor? Langsung BUILD
        // ----------------------------------------------------------------
        MapLocation nearRuin = findNearbyEmptyRuin();
        if (nearRuin != null && chips >= chipsRequired(towers)) {
            ruinTarget    = nearRuin;
            patternMarked = false;
            paintTarget   = null;
            state = STATE_BUILD_TOWER;
            return;
        }

        // ----------------------------------------------------------------
        // P4: Ada ruin dalam memori? GOTO_RUIN
        // ----------------------------------------------------------------
        MapLocation memRuin = findNearestKnownRuin();
        if (memRuin != null) {
            ruinTarget    = memRuin;
            patternMarked = false;
            paintTarget   = null;
            state = STATE_GOTO_RUIN;
            return;
        }

        // ----------------------------------------------------------------
        // P5: COMBAT - Serang tower musuh jika sudah stabil
        // ----------------------------------------------------------------
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

        // ----------------------------------------------------------------
        // P6: EXPAND - Warnai area (hanya jika tidak ada ruin yang diketahui)
        // ----------------------------------------------------------------
        if (towers >= 3 && pct > 50) {
            state = STATE_EXPAND;
            return;
        }

        // Default
        state = STATE_EXPLORE;
    }

    // =========================================================================
    //  STATE EXECUTION
    // =========================================================================

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

    // =========================================================================
    //  BUILD TOWER
    //
    //  Urutan eksekusi per ronde:
    //   1. canCompleteTowerPattern → complete SEGERA
    //   2. canMarkTowerPattern → mark (perlu dekat ruin, dist <= sqrt(2))
    //   3. Ada tile mark yang belum dicat DAN canAttack → cat sekarang
    //   4. Ada tile mark yang belum dicat tapi jauh → BERGERAK KE TILE ITU
    //   5. Sudah dekat ruin, semua tercat → coba complete lagi
    //   6. Belum dekat ruin → bergerak ke ruin
    // =========================================================================

    private static void executeBuildTower() throws GameActionException {
        if (ruinTarget == null) { state = STATE_EXPLORE; return; }

        int towers    = rc.getNumberTowers();
        UnitType type = chooseTowerType(towers);

        // -- STEP 1: Complete segera jika pattern sudah selesai --
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

        // -- STEP 2: Mark pattern (perlu dist <= 2 dari ruin) --
        if (!patternMarked && distToRuin <= 2) {
            if (rc.canMarkTowerPattern(type, ruinTarget)) {
                rc.markTowerPattern(type, ruinTarget);
                patternMarked = true;
            }
        }

        // -- STEP 3 & 4: Paint tile pattern yang belum selesai --
        if (rc.isActionReady()) {
            // Cari tile pattern terbaik untuk dicat
            MapLocation tileToGo = findBestPatternTile(ruinTarget);

            if (tileToGo != null) {
                if (rc.canAttack(tileToGo)) {
                    // Bisa langsung cat
                    PaintType mark = rc.senseMapInfo(tileToGo).getMark();
                    boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                    rc.attack(tileToGo, useSecondary);
                    paintTarget = null;
                } else {
                    // Tidak bisa attack dari sini → bergerak ke tile itu
                    paintTarget = tileToGo;
                }
            }
        }

        // -- STEP 5 & 6: Movement --
        if (rc.isMovementReady()) {
            if (paintTarget != null) {
                // Bergerak ke tile yang perlu dicat (bukan ke ruin!)
                moveSimple(paintTarget);
                checkStuck();
            } else if (distToRuin > 2) {
                // Belum dekat ruin → dekat dulu untuk mark
                moveSimple(ruinTarget);
                checkStuck();
            }
            // Jika distToRuin <= 2 dan tidak ada paintTarget → diam sambil tunggu
        }
    }

    // =========================================================================
    //  GOTO RUIN - Pergi ke ruin, hemat paint (tidak paint sambil jalan)
    // =========================================================================

    private static void executeGotoRuin() throws GameActionException {
        if (ruinTarget == null) { state = STATE_EXPLORE; return; }

        // Begitu sudah bisa sense ruin, switch ke BUILD
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

        // Bergerak ke ruin - TIDAK paint sambil jalan (hemat paint)
        if (rc.isMovementReady()) {
            moveSimple(ruinTarget);
            checkStuck();
        }
    }

    // =========================================================================
    //  REFILL
    // =========================================================================

    private static void executeRefill() throws GameActionException {
        if (!refillPaint()) {
            state = STATE_EXPLORE;
        }
    }

    // =========================================================================
    //  EXPAND - Warnai area kosong/musuh (Fase 2, setelah tower cukup)
    // =========================================================================

    private static void executeExpand() throws GameActionException {
        // Setiap ronde cek ulang: jika ada ruin baru ditemukan → prioritas build
        MapLocation nearRuin = findNearbyEmptyRuin();
        if (nearRuin != null && rc.getMoney() >= chipsRequired(rc.getNumberTowers())) {
            ruinTarget    = nearRuin;
            patternMarked = false;
            paintTarget   = null;
            state = STATE_BUILD_TOWER;
            return;
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

        if (rc.isMovementReady()) {
            if (expandTarget != null) {
                moveSimple(expandTarget);
                if (rc.getLocation().distanceSquaredTo(expandTarget) <= 1) expandTarget = null;
            } else {
                explore();
            }
        }
    }

    // =========================================================================
    //  COMBAT
    // =========================================================================

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

    // =========================================================================
    //  EXPLORE
    // =========================================================================

    private static void executeExplore() throws GameActionException {
        // Broadcast musuh jika ada
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) broadcastEnemyLocation(enemies[0].getLocation());

        if (rc.isMovementReady()) {
            // Ikuti broadcast ruin
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

    // =========================================================================
    //  HELPER METHODS
    // =========================================================================

    /**
     * Cari tile terbaik dalam pattern tower untuk dicat.
     * Greedy: pilih yang PALING DEKAT dari posisi robot saat ini.
     *
     * Hanya mencari dalam radius 8 (sqrt(8)≈2.83) dari ruin,
     * karena tower pattern 5x5 punya radius max 2 dari center.
     *
     * Penting: bergerak ke tile ini, bukan ke ruin!
     */
    private static MapLocation findBestPatternTile(MapLocation ruinCenter)
            throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // Scan area 5x5 di sekitar ruin (radius distanceSquared = 8 mencakup 5x5)
        MapInfo[] tiles = rc.senseNearbyMapInfos(ruinCenter, 8);
        MapLocation best    = null;
        int         minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.isPassable()) continue;

            PaintType mark  = tile.getMark();
            PaintType paint = tile.getPaint();

            // Tile ini bagian dari pattern (ada mark) tapi belum dicat sesuai mark
            if (mark == PaintType.EMPTY) continue;
            if (mark == paint) continue; // sudah benar, skip

            MapLocation loc  = tile.getMapLocation();
            int         dist = myLoc.distanceSquaredTo(loc);

            if (dist < minDist) {
                minDist = dist;
                best    = loc;
            }
        }
        return best;
    }

    /**
     * Cari ruin kosong (belum ada tower) dalam radius sensor.
     */
    private static MapLocation findNearbyEmptyRuin() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(-1);
        MapLocation myLoc = rc.getLocation();
        MapLocation best  = null;
        int minDist = Integer.MAX_VALUE;

        for (MapInfo tile : tiles) {
            if (!tile.hasRuin()) continue;
            MapLocation loc = tile.getMapLocation();
            if (isTowerBuiltAt(loc)) {
                removeKnownRuin(loc);  // hapus ruin yang sudah jadi tower
                continue;
            }
            int dist = myLoc.distanceSquaredTo(loc);
            if (dist < minDist) { minDist = dist; best = loc; }
        }
        return best;
    }

    /**
     * Pilih tipe tower berdasarkan jumlah tower saat ini.
     *
     * Greedy: alternasi money-paint untuk keseimbangan ekonomi.
     *   towers 0, 2, 4, 6, 8, 10 (genap) → MONEY TOWER
     *   towers 1, 3, 5, 7, 9    (ganjil) → PAINT TOWER
     *   towers >= 12 dan chips > 3000     → DEFENSE TOWER
     *
     * Logika: Kita selalu mulai dengan 1 money + 1 paint tower bawaan.
     * Jadi tower ke-2 (index 2) sebaiknya money lagi, ke-3 paint, dst.
     */
    private static UnitType chooseTowerType(int totalTowers) throws GameActionException {
        if (totalTowers >= 12 && rc.getMoney() > 3000) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        // Genap → Money, Ganjil → Paint
        return (totalTowers % 2 == 0)
            ? UnitType.LEVEL_ONE_MONEY_TOWER
            : UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    /**
     * Chips minimum untuk mulai build tower.
     * Agresif di awal agar tower cepat jadi.
     */
    private static int chipsRequired(int totalTowers) {
        if (totalTowers < 4)  return 700;
        if (totalTowers < 10) return 800;
        return 1000;
    }

    /**
     * Cari tile terbaik untuk expand (di luar tower building).
     * Greedy: musuh > kosong, terdekat.
     */
    private static MapLocation findBestExpandPaintTarget() throws GameActionException {
        MapInfo[] tiles = rc.senseNearbyMapInfos(3); // attack radius soldier
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

    /** Cek apakah tower sekutu sudah ada di lokasi */
    private static boolean isTowerBuiltAt(MapLocation loc) throws GameActionException {
        if (!rc.canSenseLocation(loc)) return false;
        if (!rc.canSenseRobotAtLocation(loc)) return false;
        RobotInfo r = rc.senseRobotAtLocation(loc);
        return r != null && isTowerType(r.getType()) && r.getTeam() == rc.getTeam();
    }

    /** Deteksi stuck: reset state jika tidak bergerak */
    private static void detectStuck() {
        MapLocation cur = rc.getLocation();
        if (lastPos != null && cur.equals(lastPos)) {
            posCount++;
            if (posCount >= 6) {
                // Stuck 6 ronde - wander keluar dari sini
                currentExploreDirection = null;
                posCount = 0;
                if (state == STATE_GOTO_RUIN || state == STATE_BUILD_TOWER) {
                    // Coba reset dan cari ruin lain
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
        // Called after movement attempt - handled by detectStuck() at start of run()
    }
}
