package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


public class Unit extends BaseRobot {

    public static MapLocation spawnLocation;
    public static Direction currentExploreDirection = null;
    public static MapLocation[] knownPaintTowers = new MapLocation[25];
    public static int knownPaintTowerCount = 0;
    public static MapLocation[] knownAllyTowers = new MapLocation[25];
    public static int knownAllyTowerCount = 0;

    public static MapLocation[] knownRuins = new MapLocation[30];
    public static int knownRuinCount = 0;

    public static MapLocation returnLocation = null;

    public static MapLocation lastKnownEnemyLoc = null;
    public static int lastKnownEnemyRound = -1;
    public static MapLocation exploreTarget = null;
    public static int exploreTargetAge = 0;

    // Fungsi inisialisasi lokasi spawn
    public static void initUnit() {
        if (spawnLocation == null) {
            spawnLocation = rc.getLocation();
        }
    }

    // Fungsi bergerak greedy menuju target
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

    // Fungsi menilai skor tile untuk pergerakan greedy
    private static double evaluateTile(MapLocation loc, MapLocation target) throws GameActionException {
        double score = 0;

        // Prioritaskan tile yang lebih dekat ke target
        int distToTarget = loc.distanceSquaredTo(target);
        score -= distToTarget * 2.0;

        if (rc.canSenseLocation(loc)) {
            MapInfo info = rc.senseMapInfo(loc);
            PaintType paint = info.getPaint();

            if (paint.isAlly()) {
                score += 8; // lebih aman berjalan di area ally
            } else if (paint == PaintType.EMPTY) {
                score += 2;
            } else {
                score -= 10; // hindari cat musuh
            }

            if (isNearEnemyTower(loc)) {
                score -= 30; // jauhi jangkauan tower musuh
            }
        }

        return score;
    }

    // Fungsi cek apakah lokasi dekat tower musuh
    private static boolean isNearEnemyTower(MapLocation loc) throws GameActionException {
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(loc, 9, rc.getTeam().opponent());
        for (RobotInfo enemy : nearbyEnemies) {
            if (isTowerType(enemy.getType())) {
                return true;
            }
        }
        return false;
    }

    // Fungsi bergerak sederhana menuju target tanpa scoring
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

    // Fungsi mencoba membangun tower di ruin terdekat
    public static boolean tryBuildTower() throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);

        MapLocation targetRuin = null;
        int minDistance = Integer.MAX_VALUE;

        for (MapInfo tile : nearbyTiles) {
            if (!tile.hasRuin()) continue;
            MapLocation ruinLoc = tile.getMapLocation();

            // Skip ruin yang sudah ada tower
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

        UnitType towerToBuild = chooseTowerType();
        int requiredChips = (towerToBuild == UnitType.LEVEL_ONE_DEFENSE_TOWER) ? 2500 : 1000;

        if (rc.getMoney() < requiredChips) {
            // Tunggu dekat ruin jika hampir cukup chips
            if (myLoc.distanceSquaredTo(targetRuin) <= 2 && rc.getMoney() >= requiredChips - 300) {
                return true;
            }
            return false;
        }

        if (rc.canCompleteTowerPattern(towerToBuild, targetRuin)) {
            rc.completeTowerPattern(towerToBuild, targetRuin);
            if (isPaintTower(towerToBuild)) {
                addKnownPaintTower(targetRuin);
            }
            addKnownAllyTower(targetRuin);
            return true;
        }

        if (rc.canMarkTowerPattern(towerToBuild, targetRuin)) {
            rc.markTowerPattern(towerToBuild, targetRuin);
        }

        // Dekati ruin jika masih jauh
        if (myLoc.distanceSquaredTo(targetRuin) > 2) {
            if (rc.isMovementReady()) {
                moveGreedy(targetRuin);
                return true;
            }
        }

        paintMarkedTilesAround(targetRuin);

        return true;
    }

    // Fungsi memilih tipe tower berdasarkan kondisi saat ini
    private static UnitType chooseTowerType() throws GameActionException {
        int totalTowers = rc.getNumberTowers();
        int chips = rc.getMoney();
        @SuppressWarnings("unused") int round = rc.getRoundNum();

        if (totalTowers < 2) {
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // Hitung jumlah tower paint dan money yang terlihat
        int paintCount = 0;
        int moneyCount = 0;
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isPaintTower(ally.getType())) paintCount++;
            else if (isMoneyTower(ally.getType())) moneyCount++;
        }

        if (totalTowers < 4) {
            if (paintCount == 0) return UnitType.LEVEL_ONE_PAINT_TOWER;
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        if (totalTowers < 7) {
            // Seimbangkan paint dan money tower
            if (paintCount <= moneyCount) return UnitType.LEVEL_ONE_PAINT_TOWER;
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }

        // Tower defense jika sudah banyak tower dan chips cukup
        if (totalTowers >= 10 && chips > 2000) {
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }

        if (chips > 2000) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    // Fungsi mengecat tile yang sudah ditandai di sekitar ruin
    private static void paintMarkedTilesAround(MapLocation center) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(center, 8);
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            PaintType mark = tile.getMark();
            PaintType paint = tile.getPaint();

            // Cat tile yang belum sesuai dengan markingnya
            if (mark != PaintType.EMPTY && mark != paint && tile.isPassable()) {
                boolean useSecondary = (mark == PaintType.ALLY_SECONDARY);
                if (rc.canAttack(loc)) {
                    rc.attack(loc, useSecondary);
                    return;
                }
            }
        }
    }

    // Fungsi cek apakah kondisi memungkinkan membangun SRP
    public static boolean shouldBuildSRP() throws GameActionException {
        int totalTowers = rc.getNumberTowers();
        int chips = rc.getMoney();
        int round = rc.getRoundNum();

        // Minimal tower tergantung ukuran map
        int mapWidth = rc.getMapWidth();
        int mapHeight = rc.getMapHeight();
        int minTowers = Math.max(4, 6 - Math.min(mapWidth, mapHeight) / 10);

        return totalTowers >= minTowers && chips >= 500 && round > EARLY_GAME_END;
    }

    // Fungsi mencoba membangun SRP di lokasi saat ini
    public static boolean tryBuildSRP() throws GameActionException {
        if (!shouldBuildSRP()) return false;

        MapLocation myLoc = rc.getLocation();

        // Cek apakah bisa menyelesaikan SRP yang sudah ada
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(-1);
        for (MapInfo tile : nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            if (rc.canCompleteResourcePattern(loc)) {
                rc.completeResourcePattern(loc);
                return true;
            }
        }

        if (rc.canMarkResourcePattern(myLoc)) {
            rc.markResourcePattern(myLoc);
            return true;
        }

        return false;
    }

    // Fungsi mengisi ulang paint di tower terdekat
    public static boolean refillPaint() throws GameActionException {
        int paintCapacity = rc.getType().paintCapacity;
        int currentPaint = rc.getPaint();
        int paintPercent = (currentPaint * 100) / paintCapacity;

        if (paintPercent > REFILL_PAINT_PERCENT) return false;

        MapLocation nearestPaintTower = findNearestPaintTower();

        if (nearestPaintTower == null) {
            nearestPaintTower = findNearestKnownPaintTower();
        }

        // Fallback ke spawn jika tidak ada paint tower yang diketahui
        if (nearestPaintTower == null && spawnLocation != null) {
            nearestPaintTower = spawnLocation;
        }

        if (nearestPaintTower == null) return false;

        int dist = rc.getLocation().distanceSquaredTo(nearestPaintTower);

        if (dist <= 2) {
            withdrawPaint(nearestPaintTower);
        } else {
            if (rc.isMovementReady()) {
                moveGreedy(nearestPaintTower);
            }
        }

        return true;
    }

    // Fungsi mencari paint tower yang terlihat saat ini
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
                addKnownPaintTower(ally.getLocation()); // update cache
            }
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
            }
        }
        return nearest;
    }

    // Fungsi mencari paint tower dari cache yang diketahui
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

    // Fungsi mengambil paint dari tower ally
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

    // Fungsi broadcast lokasi musuh ke tower ally
    public static void broadcastEnemyLocation(MapLocation enemyLoc) throws GameActionException {
        int msg = encodeMessage(MSG_ENEMY_LOC, enemyLoc.x, enemyLoc.y, rc.getRoundNum() % EXTRA_MASK);

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                if (rc.canSendMessage(ally.getLocation(), msg)) {
                    rc.sendMessage(ally.getLocation(), msg);
                    return; // cukup satu tower
                }
            }
        }
    }

    // Fungsi broadcast lokasi tower musuh ke tower ally
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

    // Fungsi request mopper ke tower terdekat
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

    // Fungsi broadcast lokasi ruin ke tower ally
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

    // Fungsi membaca pesan broadcast dan memilih target terdekat
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

            // Hanya proses pesan yang masih segar
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

    // Fungsi memproses semua pesan masuk dan update state lokal
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

    // Fungsi memindai lingkungan sekitar dan update cache
    public static void scanEnvironment() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Update cache tower ally
        for (RobotInfo ally : allies) {
            if (isTowerType(ally.getType())) {
                addKnownAllyTower(ally.getLocation());
                if (isPaintTower(ally.getType())) {
                    addKnownPaintTower(ally.getLocation());
                }
            }
        }

        // Catat musuh dan broadcast tower musuh
        if (enemies.length > 0) {
            lastKnownEnemyLoc = enemies[0].getLocation();
            lastKnownEnemyRound = rc.getRoundNum();

            for (RobotInfo enemy : enemies) {
                if (isTowerType(enemy.getType())) {
                    broadcastEnemyTower(enemy.getLocation());
                    break;
                }
            }
        }

        // Update cache ruin yang terlihat
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

    // Fungsi eksplorasi berbasis zona untuk menyebar robot secara merata
    public static void explore() throws GameActionException {
        if (!rc.isMovementReady()) return;
        MapLocation myLoc = rc.getLocation();
        // Perbarui target jika belum ada, sudah dicapai, atau terlalu lama
        if (exploreTarget == null
                || myLoc.distanceSquaredTo(exploreTarget) <= 9
                || ++exploreTargetAge > 80) {
            exploreTarget = pickExploreTarget();
            exploreTargetAge = 0;
        }

        moveGreedy(exploreTarget);
    }

    // Fungsi memilih target zona berdasarkan ID robot
    public static MapLocation pickExploreTarget() {
        int W = rc.getMapWidth();
        int H = rc.getMapHeight();
        int id = rc.getID();

        int gx, gy;
        switch (id % 8) {
            case 0: gx = W / 4;     gy = H / 4;     break; // kiri-bawah
            case 1: gx = 3 * W / 4; gy = H / 4;     break; // kanan-bawah
            case 2: gx = W / 4;     gy = 3 * H / 4; break; // kiri-atas
            case 3: gx = 3 * W / 4; gy = 3 * H / 4; break; // kanan-atas
            case 4: gx = W / 2;     gy = H / 4;     break; // bawah-tengah
            case 5: gx = W / 2;     gy = 3 * H / 4; break; // atas-tengah
            case 6: gx = W / 4;     gy = H / 2;     break; // kiri-tengah
            default: gx = 3 * W / 4; gy = H / 2;    break; // kanan-tengah
        }

        // Offset kecil untuk cegah clustering di titik yang sama
        gx = Math.max(2, Math.min(W - 3, gx + (id * 3 % 7) - 3));
        gy = Math.max(2, Math.min(H - 3, gy + (id * 5 % 7) - 3));

        return new MapLocation(gx, gy);
    }

    // Fungsi jalan acak jika tidak ada target
    public static void wander() throws GameActionException {
        if (!rc.isMovementReady()) return;

        if (currentExploreDirection == null || !rc.canMove(currentExploreDirection)) {
            int randomIndex = (int) (Math.random() * directions.length);
            currentExploreDirection = directions[randomIndex];
        }

        if (rc.canMove(currentExploreDirection)) {
            rc.move(currentExploreDirection);
        } else {
            // Coba rotasi sampai menemukan arah yang bisa
            for (int i = 0; i < 8; i++) {
                currentExploreDirection = currentExploreDirection.rotateRight();
                if (rc.canMove(currentExploreDirection)) {
                    rc.move(currentExploreDirection);
                    return;
                }
            }
        }
    }

    // Fungsi mengecat tile di bawah robot jika belum di-paint
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

    // Fungsi menambah paint tower ke cache jika belum ada
    public static void addKnownPaintTower(MapLocation loc) {
        for (int i = 0; i < knownPaintTowerCount; i++) {
            if (knownPaintTowers[i].equals(loc)) return;
        }
        if (knownPaintTowerCount < knownPaintTowers.length) {
            knownPaintTowers[knownPaintTowerCount++] = loc;
        }
    }

    // Fungsi menambah ally tower ke cache jika belum ada
    public static void addKnownAllyTower(MapLocation loc) {
        for (int i = 0; i < knownAllyTowerCount; i++) {
            if (knownAllyTowers[i].equals(loc)) return;
        }
        if (knownAllyTowerCount < knownAllyTowers.length) {
            knownAllyTowers[knownAllyTowerCount++] = loc;
        }
    }

    // Fungsi menambah ruin ke cache jika belum ada
    public static void addKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) return;
        }
        if (knownRuinCount < knownRuins.length) {
            knownRuins[knownRuinCount++] = loc;
        }
    }

    // Fungsi mencari ruin terdekat dari cache
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

    // Fungsi menghapus ruin dari cache
    public static void removeKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) {
                knownRuins[i] = knownRuins[knownRuinCount - 1];
                knownRuinCount--;
                return;
            }
        }
    }

    // Fungsi mendapatkan fase permainan saat ini
    public static int getGamePhase() {
        int round = rc.getRoundNum();
        if (round < EARLY_GAME_END) return 0;
        if (round < MID_GAME_END) return 1;
        return 2;
    }

    // Fungsi cek apakah paint unit rendah
    public static boolean isLowPaint() {
        int paintCapacity = rc.getType().paintCapacity;
        int currentPaint = rc.getPaint();
        return (currentPaint * 100 / paintCapacity) < LOW_PAINT_PERCENT;
    }

    // Fungsi menghitung jumlah ally terdekat
    public static int countNearbyAllies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam()).length;
    }

    // Fungsi menghitung jumlah musuh terdekat
    public static int countNearbyEnemies() throws GameActionException {
        return rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length;
    }
}
