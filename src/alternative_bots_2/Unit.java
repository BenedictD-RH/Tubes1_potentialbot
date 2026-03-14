package alternative_bots_2;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

/**
 * Kelas dasar untuk semua unit robot (Soldier, Mopper, Splasher).
 * Menyediakan:
 *  - Greedy pathfinding dengan tile scoring
 *  - Komunikasi antar robot (5 tipe pesan)
 *  - Manajemen paint (refill, withdraw)
 *  - SRP building
 *  - Tower building dengan prioritas
 *  - Eksplorasi cerdas
 */
public class Unit extends BaseRobot {

    // === State yang disimpan antar giliran ===
    public static MapLocation spawnLocation;
    public static Direction currentExploreDirection = null;

    // Memori lokasi tower sekutu yang diketahui (paint tower untuk refill)
    public static MapLocation[] knownPaintTowers = new MapLocation[25];
    public static int knownPaintTowerCount = 0;

    // Memori lokasi tower sekutu (semua tipe)
    public static MapLocation[] knownAllyTowers = new MapLocation[25];
    public static int knownAllyTowerCount = 0;

    // Lokasi ruin yang diketahui belum ada tower
    public static MapLocation[] knownRuins = new MapLocation[30];
    public static int knownRuinCount = 0;

    // Target kembali setelah refill
    public static MapLocation returnLocation = null;

    // Lokasi terakhir musuh yang diketahui
    public static MapLocation lastKnownEnemyLoc = null;
    public static int lastKnownEnemyRound = -1;

    // === Inisialisasi ===
    public static void initUnit() {
        if (spawnLocation == null) {
            spawnLocation = rc.getLocation();
        }
    }

    // =========================================================================
    //  GREEDY PATHFINDING - Memilih tile terbaik berdasarkan heuristic scoring
    // =========================================================================

    /**
     * Greedy pathfinding: evaluasi semua arah yang mungkin dan pilih yang terbaik.
     * Scoring mempertimbangkan:
     *  - Jarak ke target (prioritas utama)
     *  - Apakah tile memiliki paint sekutu (mengurangi cooldown)
     *  - Apakah tile memiliki paint musuh (penalty)
     *  - Kedekatan dengan tower musuh (hindari)
     */
    public static void moveGreedy(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation nextLoc = myLoc.add(dir);
            double score = evaluateTile(nextLoc, target);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
        }
    }

    /**
     * Evaluasi score suatu tile untuk pathfinding.
     * Semakin tinggi score, semakin bagus tile tersebut.
     * Ini adalah fungsi heuristic greedy - selalu pilih lokal terbaik.
     */
    private static double evaluateTile(MapLocation loc, MapLocation target) throws GameActionException {
        double score = 0;

        // Komponen utama: jarak ke target (negatif, semakin dekat semakin baik)
        int distToTarget = loc.distanceSquaredTo(target);
        score -= distToTarget * 2.0;

        // Bonus jika tile punya paint sekutu (mengurangi cooldown pergerakan)
        if (rc.canSenseLocation(loc)) {
            MapInfo info = rc.senseMapInfo(loc);
            PaintType paint = info.getPaint();

            if (paint.isAlly()) {
                score += 8; // Bonus besar untuk tile sekutu
            } else if (paint == PaintType.EMPTY) {
                score += 2; // Netral lebih baik dari musuh
            } else {
                // Paint musuh - penalty karena kehilangan lebih banyak paint
                score -= 10;
            }

            // Hindari tile dekat tower musuh (cek robot di sekitar)
            if (isNearEnemyTower(loc)) {
                score -= 30; // Penalty besar - tower bisa menyerang
            }
        }

        return score;
    }

    /**
     * Cek apakah lokasi dekat dengan tower musuh.
     * Tower musuh punya attack range 3-4, jadi berbahaya.
     */
    private static boolean isNearEnemyTower(MapLocation loc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(loc, 9, rc.getTeam().opponent());
        for (RobotInfo enemy : nearbyEnemies) {
            if (isTowerType(enemy.getType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fallback movement sederhana - bergerak langsung ke target tanpa scoring.
     */
    public static void moveSimple(MapLocation target) throws GameActionException {
        if (!rc.isMovementReady()) return;

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int minDist = myLoc.distanceSquaredTo(target);

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;
            MapLocation next = myLoc.add(dir);
            int dist = next.distanceSquaredTo(target);
            if (dist < minDist) {
                minDist = dist;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
        }
    }

    // =========================================================================
    //  TOWER BUILDING - Prioritas membangun tower berdasarkan kondisi game
    // =========================================================================

    /**
     * Mencoba membangun tower di ruin terdekat.
     * Strategi greedy: pilih tower type yang paling menguntungkan saat ini.
     * Return true jika sedang dalam proses building (robot harus stay).
     */
    public static boolean tryBuildTower() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        // Cari ruin terdekat yang belum ada tower
        MapLocation targetRuin = null;
        int minDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();

            // Skip jika sudah ada tower di ruin
            if (rc.canSenseRobotAtLocation(ruinLoc)) {
                RobotInfo robot = rc.senseRobotAtLocation(ruinLoc);
                if (robot != null && isTowerType(robot.getType())) {
                    continue;
                }
            }

            int dist = myLoc.distanceSquaredTo(ruinLoc);
            if (dist < minDistance) {
                minDistance = dist;
                targetRuin = ruinLoc;
            }
        }

        if (targetRuin == null) return false;

        // Tentukan tipe tower berdasarkan kondisi ekonomi (greedy choice)
        UnitType towerToBuild = chooseTowerType();
        int requiredChips = (towerToBuild == UnitType.LEVEL_ONE_DEFENSE_TOWER) ? 2500 : 1000;

        // Cek apakah punya cukup chips
        if (rc.getMoney() < requiredChips) {
            // Tunggu di dekat ruin jika hampir cukup chips
            if (myLoc.distanceSquaredTo(targetRuin) <= 2 && rc.getMoney() >= requiredChips - 300) {
                return true; // Tetap di sini, tunggu chips
            }
            return false;
        }

        // Coba complete tower pattern
        if (rc.canCompleteTowerPattern(towerToBuild, targetRuin)) {
            rc.completeTowerPattern(towerToBuild, targetRuin);
            // Simpan di memori jika paint tower
            if (isPaintTower(towerToBuild)) {
                addKnownPaintTower(targetRuin);
            }
            addKnownAllyTower(targetRuin);
            return true;
        }

        // Mark tower pattern
        if (rc.canMarkTowerPattern(towerToBuild, targetRuin)) {
            rc.markTowerPattern(towerToBuild, targetRuin);
        }

        // Bergerak ke ruin jika belum dekat
        if (myLoc.distanceSquaredTo(targetRuin) > 2) {
            if (rc.isMovementReady()) {
                moveGreedy(targetRuin);
                return true;
            }
        }

        // Paint tiles sekitar ruin sesuai mark
        paintMarkedTilesAround(targetRuin);

        return true;
    }

    /**
     * Greedy choice: pilih tipe tower yang paling dibutuhkan saat ini.
     * Heuristic berdasarkan jumlah tower, resource, dan fase game.
     */
    private static UnitType chooseTowerType() throws GameActionException {
        int totalTowers = rc.getNumberTowers();
        int chips = rc.getMoney();
        int round = rc.getRoundNum();

        // Early game: prioritas money tower untuk ekonomi
        if (totalTowers < 2) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // Hitung rasio paint vs money tower dari yang diketahui
        int paintCount = 0;
        int moneyCount = 0;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) paintCount++;
            else if (isMoneyTower(ally.getType())) moneyCount++;
        }

        // Perlu paint tower jika belum punya atau rasio kurang
        if (totalTowers < 4) {
            if (paintCount == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        if (totalTowers < 7) {
            // Seimbangkan money dan paint
            if (paintCount <= moneyCount) return UnitType.LEVEL_ONE_PAINT_TOWER;
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // Mid-late game: defense tower jika sudah banyak
        if (totalTowers >= 10 && chips > 2000) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        // Default: seimbangkan berdasarkan resource
        if (chips > 2000) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    /**
     * Paint tiles yang sudah di-mark di sekitar lokasi tertentu (untuk tower pattern).
     */
    private static void paintMarkedTilesAround(MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(center, 8);
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            PaintType mark = tile.getMark();
            PaintType paint = tile.getPaint();

            // Jika ada mark dan paint belum sesuai
            if (mark != PaintType.EMPTY && mark != paint && tile.isPassable()) {
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                if (rc.canAttack(loc)) {
                    rc.attack(loc, useSecondary);
                    return;
                }
            }
        }
    }

    // =========================================================================
    //  SRP BUILDING - Special Resource Pattern untuk boost produksi
    // =========================================================================

    /**
     * Cek apakah kondisi cocok untuk membangun SRP.
     * Greedy: bangun SRP jika ekonomi stabil dan sudah punya cukup tower.
     */
    public static boolean shouldBuildSRP() throws GameActionException {
        int totalTowers = rc.getNumberTowers();
        int chips = rc.getMoney();
        int round = rc.getRoundNum();

        // Butuh minimal beberapa tower dan ekonomi stabil
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();
        int minTowers = Math.max(4, 6 - Math.min(mapWidth, mapHeight) / 10);

        return totalTowers >= minTowers && chips >= 500 && round > EARLY_GAME_END;
    }

    /**
     * Mencoba membangun SRP di lokasi saat ini.
     * Return true jika berhasil memulai atau menyelesaikan SRP.
     */
    public static boolean tryBuildSRP() throws GameActionException {
        if (!shouldBuildSRP()) return false;

        MapLocation myLoc = rc.getLocation();

        // Coba complete SRP pattern di sekitar
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                return true;
            }
        }

        // Coba mark SRP pattern
        if (rc.canMarkResourcePattern(myLoc)) {
            rc.markResourcePattern(myLoc);
            return true;
        }

        return false;
    }

    // =========================================================================
    //  PAINT MANAGEMENT - Refill dan manajemen sumber daya cat
    // =========================================================================

    /**
     * Refill paint dari tower terdekat.
     * Greedy: selalu cari tower paint terdekat, dengan fallback ke memori.
     * Return true jika sedang dalam proses refill.
     */
    public static boolean refillPaint() throws GameActionException {
        int paintCapacity = rc.getType().paintCapacity;
        int currentPaint = rc.getPaint();
        int paintPercent = (currentPaint * 100) / paintCapacity;

        if (paintPercent > REFILL_PAINT_PERCENT) return false;

        // Cari paint tower terdekat dari sensing
        MapLocation nearestPaintTower = findNearestPaintTower();

        // Fallback: cari dari memori
        if (nearestPaintTower == null) {
            nearestPaintTower = findNearestKnownPaintTower();
        }

        // Fallback: kembali ke spawn
        if (nearestPaintTower == null && spawnLocation != null) {
            nearestPaintTower = spawnLocation;
        }

        if (nearestPaintTower == null) return false;

        int dist = rc.getLocation().distanceSquaredTo(nearestPaintTower);

        if (dist <= 2) {
            // Sudah di samping tower - tarik paint
            withdrawPaint(nearestPaintTower);
        } else {
            // Bergerak ke tower
            if (rc.isMovementReady()) {
                moveGreedy(nearestPaintTower);
            }
        }

        return true;
    }

    /**
     * Cari paint tower terdekat dari sensing langsung.
     */
    private static MapLocation findNearestPaintTower() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) {
                int dist = rc.getLocation().distanceSquaredTo(ally.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = ally.getLocation();
                }
                // Update memori
                addKnownPaintTower(ally.getLocation());
            }
            // Track semua tower sekutu
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
            }
        }
        return nearest;
    }

    /**
     * Cari paint tower terdekat dari memori.
     */
    private static MapLocation findNearestKnownPaintTower() {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (int i = 0; i < knownPaintTowerCount; i++) {
            int dist = myLoc.distanceSquaredTo(knownPaintTowers[i]);
            if (dist < minDist) {
                minDist = dist;
                nearest = knownPaintTowers[i];
            }
        }
        return nearest;
    }

    /**
     * Tarik paint dari tower.
     */
    private static void withdrawPaint(MapLocation towerLoc) throws GameActionException {
        if (!rc.isActionReady()) return;

        int needed = rc.getType().paintCapacity - rc.getPaint();
        if (needed <= 0) return;

        if (rc.canSenseRobotAtLocation(towerLoc)) {
            RobotInfo tower = rc.senseRobotAtLocation(towerLoc);
            if (tower != null && tower.getTeam() == rc.getTeam()) {
                int available = tower.getPaintAmount();
                int transfer = Math.min(needed, available);
                if (transfer > 0 && rc.canTransferPaint(towerLoc, -transfer)) {
                    rc.transferPaint(towerLoc, -transfer);
                }
            }
        }
    }

    // =========================================================================
    //  KOMUNIKASI - Sistem pesan antar robot
    // =========================================================================

    /**
     * Broadcast lokasi musuh ke tower sekutu terdekat.
     */
    public static void broadcastEnemyLocation(MapLocation enemyLoc) throws GameActionException {
        int msg = encodeMessage(MSG_ENEMY_LOC, enemyLoc.x, enemyLoc.y, rc.getRoundNum() % EXTRA_MASK);

        // Kirim ke tower terdekat
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return; // Robot hanya bisa kirim 1 pesan per ronde
                }
            }
        }
    }

    /**
     * Broadcast lokasi tower musuh.
     */
    public static void broadcastEnemyTower(MapLocation towerLoc) throws GameActionException {
        int msg = encodeMessage(MSG_ENEMY_TOWER, towerLoc.x, towerLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    /**
     * Request mopper ke lokasi tertentu (ada paint musuh yang perlu dibersihkan).
     */
    public static void requestMopper(MapLocation loc) throws GameActionException {
        int msg = encodeMessage(MSG_MOPPER_REQ, loc.x, loc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    /**
     * Broadcast lokasi ruin kosong.
     */
    public static void broadcastRuin(MapLocation ruinLoc) throws GameActionException {
        int msg = encodeMessage(MSG_RUIN_LOC, ruinLoc.x, ruinLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return;
                }
            }
        }
    }

    /**
     * Membaca semua pesan dari buffer dan mengembalikan target paling relevan.
     * Greedy: pilih pesan terbaru yang paling dekat.
     */
    public static MapLocation readBroadcastTarget(int msgType) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        if (messages.length == 0) return null;

        MapLocation bestLoc = null;
        int bestDist = Integer.MAX_VALUE;
        int currentRound = rc.getRoundNum();

        for (Message m : messages) {
            int msg = m.getBytes();
            int type = decodeType(msg);

            if (type != msgType) continue;

            int sentRound = decodeExtra(msg);
            int age = (currentRound - sentRound) % EXTRA_MASK;

            // Hanya terima pesan yang masih segar (max 10 ronde)
            if (age <= 10 || age > 2000) {
                int x = decodeX(msg);
                int y = decodeY(msg);
                MapLocation loc = new MapLocation(x, y);
                int dist = rc.getLocation().distanceSquaredTo(loc);

                if (dist < bestDist) {
                    bestDist = dist;
                    bestLoc = loc;
                }
            }
        }

        return bestLoc;
    }

    /**
     * Scan semua pesan dan update informasi internal.
     */
    public static void processMessages() throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        int currentRound = rc.getRoundNum();

        for (Message m : messages) {
            int msg = m.getBytes();
            int type = decodeType(msg);
            int x = decodeX(msg);
            int y = decodeY(msg);
            MapLocation loc = new MapLocation(x, y);

            switch (type) {
                case MSG_TOWER_LOC:
                    addKnownAllyTower(loc);
                    break;
                case MSG_ENEMY_LOC:
                    lastKnownEnemyLoc = loc;
                    lastKnownEnemyRound = currentRound;
                    break;
                case MSG_RUIN_LOC:
                    addKnownRuin(loc);
                    break;
                default:
                    break;
            }
        }
    }

    // =========================================================================
    //  SENSING & AWARENESS - Memindai lingkungan sekitar
    // =========================================================================

    /**
     * Scan lingkungan dan update informasi internal.
     * Dipanggil setiap giliran untuk update awareness.
     */
    public static void scanEnvironment() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Update tower sekutu yang diketahui
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
                if (isPaintTower(ally.getType())) {
                    addKnownPaintTower(ally.getLocation());
                }
            }
        }

        // Update informasi musuh
        if (enemies.length > 0) {
            lastKnownEnemyLoc = enemies[0].getLocation();
            lastKnownEnemyRound = rc.getRoundNum();

            // Broadcast tower musuh jika ditemukan
            for (RobotInfo enemy : enemies) {
                if (isTowerType(enemy.getType())) {
                    broadcastEnemyTower(enemy.getLocation());
                    break;
                }
            }
        }

        // Scan ruin
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation ruinLoc = tile.getMapLocation();
                if (!rc.canSenseRobotAtLocation(ruinLoc) ||
                    (rc.canSenseRobotAtLocation(ruinLoc) &&
                     !isTowerType(rc.senseRobotAtLocation(ruinLoc).getType()))) {
                    addKnownRuin(ruinLoc);
                }
            }
        }
    }

    // =========================================================================
    //  EKSPLORASI - Pergerakan eksplorasi cerdas
    // =========================================================================

    /**
     * Eksplorasi cerdas: bergerak ke area yang belum dijelajahi.
     * Greedy: pilih arah yang menjauh dari spawn dan area yang sudah diwarnai.
     */
    public static void explore() throws GameActionException {
        if (!rc.isMovementReady()) return;

        // Coba bergerak ke area tanpa paint sekutu (area baru)
        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int bestScore = Integer.MIN_VALUE;

        for (Direction dir : directions) {
            if (!rc.canMove(dir)) continue;

            MapLocation next = myLoc.add(dir);
            int score = 0;

            // Jauhi spawn location
            score += next.distanceSquaredTo(spawnLocation);

            // Bonus jika tile belum di-paint sekutu
            if (rc.canSenseLocation(next)) {
                MapInfo info = rc.senseMapInfo(next);
                if (!info.getPaint().isAlly()) {
                    score += 15; // Prefer area baru
                }
                if (info.getPaint() == PaintType.EMPTY) {
                    score += 5;
                }
            }

            // Hindari kembali ke arah yang sama dengan explore direction terakhir
            if (currentExploreDirection != null && dir == currentExploreDirection.opposite()) {
                score -= 20;
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }
        }

        if (bestDir != null) {
            currentExploreDirection = bestDir;
            rc.move(bestDir);
        } else {
            // Fallback ke wander random
            wander();
        }
    }

    /**
     * Wander random dengan rotasi arah.
     */
    public static void wander() throws GameActionException {
        if (!rc.isMovementReady()) return;

        if (currentExploreDirection == null || !rc.canMove(currentExploreDirection)) {
            int randomIndex = (int) (Math.random() * directions.length);
            currentExploreDirection = directions[randomIndex];
        }

        if (rc.canMove(currentExploreDirection)) {
            rc.move(currentExploreDirection);
        } else {
            // Coba rotasi
            for (int i = 0; i < 8; i++) {
                currentExploreDirection = currentExploreDirection.rotateRight();
                if (rc.canMove(currentExploreDirection)) {
                    rc.move(currentExploreDirection);
                    return;
                }
            }
        }
    }

    // =========================================================================
    //  PAINTING - Mewarnai tile di sekitar
    // =========================================================================

    /**
     * Paint tile di bawah robot sendiri jika belum di-paint sekutu.
     * Mengurangi paint loss saat akhir giliran.
     */
    public static void paintUnderSelf() throws GameActionException {
        if (!rc.isActionReady()) return;

        MapLocation myLoc = rc.getLocation();
        if (!rc.canSenseLocation(myLoc)) return;
        MapInfo info = rc.senseMapInfo(myLoc);
        if (!info.getPaint().isAlly() && info.isPassable() && rc.canAttack(myLoc)) {
            PaintType mark = info.getMark();
            boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
            rc.attack(myLoc, useSecondary);
        }
    }

    // =========================================================================
    //  MEMORY MANAGEMENT - Menyimpan dan mencari informasi
    // =========================================================================

    public static void addKnownPaintTower(MapLocation loc) {
        for (int i = 0; i < knownPaintTowerCount; i++) {
            if (knownPaintTowers[i].equals(loc)) return;
        }
        if (knownPaintTowerCount < knownPaintTowers.length) {
            knownPaintTowers[knownPaintTowerCount++] = loc;
        }
    }

    public static void addKnownAllyTower(MapLocation loc) {
        for (int i = 0; i < knownAllyTowerCount; i++) {
            if (knownAllyTowers[i].equals(loc)) return;
        }
        if (knownAllyTowerCount < knownAllyTowers.length) {
            knownAllyTowers[knownAllyTowerCount++] = loc;
        }
    }

    public static void addKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) return;
        }
        if (knownRuinCount < knownRuins.length) {
            knownRuins[knownRuinCount++] = loc;
        }
    }

    /**
     * Cari ruin terdekat yang diketahui dari memori.
     */
    public static MapLocation findNearestKnownRuin() {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (int i = 0; i < knownRuinCount; i++) {
            int dist = myLoc.distanceSquaredTo(knownRuins[i]);
            if (dist < minDist) {
                minDist = dist;
                nearest = knownRuins[i];
            }
        }
        return nearest;
    }

    /**
     * Hapus ruin dari memori (sudah dibangun tower).
     */
    public static void removeKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) {
                knownRuins[i] = knownRuins[knownRuinCount - 1];
                knownRuinCount--;
                return;
            }
        }
    }

    // =========================================================================
    //  UTILITY - Fungsi bantuan umum
    // =========================================================================

    /**
     * Cek game phase berdasarkan round number.
     * 0 = early, 1 = mid, 2 = late
     */
    public static int getGamePhase() {
        int round = rc.getRoundNum();
        if (round < EARLY_GAME_END) return 0;
        if (round < MID_GAME_END) return 1;
        return 2;
    }

    /**
     * Cek apakah robot perlu refill paint (persentase rendah).
     */
    public static boolean isLowPaint() {
        int paintCapacity = rc.getType().paintCapacity;
        int currentPaint = rc.getPaint();
        return (currentPaint * 100 / paintCapacity) < LOW_PAINT_PERCENT;
    }

    /**
     * Hitung jumlah ally di sekitar.
     */
    public static int countNearbyAllies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam()).length;
    }

    /**
     * Hitung jumlah enemy di sekitar.
     */
    public static int countNearbyEnemies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;
    }
}
