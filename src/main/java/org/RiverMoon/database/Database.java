package org.RiverMoon.database;

import org.RiverMoon.Main;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Database {
    private final Main plugin;
    private Connection connection;

    public Database(Main plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            File dataFolder = new File(plugin.getDataFolder(), "cache.db");
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);

            // Tối ưu SQLite
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }

            connection.setAutoCommit(true);

            // Bước 1: Tạo bảng nếu chưa có
            createTables();

            // Bước 2: Kiểm tra và nâng cấp cấu trúc bảng (Migration)
            updateDatabaseStructure();

        } catch (Exception e) {
            plugin.getLogger().severe("Could not connect to SQLite: " + e.getMessage());
        }
    }
    private void updateDatabaseStructure() {
        // Lệnh thêm cột respawn_at nếu chưa có
        // Lưu ý: INTEGER trong SQLite có thể chứa giá trị Long (timestamp)
        String sql = "ALTER TABLE placed_blocks ADD COLUMN respawn_at INTEGER DEFAULT 0";

        try (Statement s = connection.createStatement()) {
            s.execute(sql);
            plugin.getLogger().info("§a[Database] Đã nâng cấp cấu trúc bảng: Thêm cột respawn_at thành công.");
        } catch (SQLException e) {
            // Nếu lỗi chứa từ "duplicate column", nghĩa là cột đã tồn tại rồi, không cần làm gì cả.
            if (e.getMessage().contains("duplicate column name")) {
                // Cột đã tồn tại, bỏ qua
            } else {
                // Lỗi khác thì in ra để kiểm tra
                // plugin.getLogger().warning("Thông báo: " + e.getMessage());
            }
        }
    }
    // 2. Hàm cập nhật thời gian hồi sinh
    public void updateRespawnTime(String world, int x, int y, int z, long respawnAt) {
        // Phải có đủ 4 điều kiện WHERE: world, x, y, z
        String sql = "UPDATE placed_blocks SET respawn_at = ? WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, respawnAt);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z); // Thêm dòng này
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    // 3. Hàm lấy các block đang chờ hồi sinh (Global Task sẽ dùng hàm này)
    public List<PlacedBlockData> getBlocksReadyToRespawn(long currentTime) {
        List<PlacedBlockData> list = new ArrayList<>();
        // Lấy những block có respawn_at > 0 (đang đợi) và <= thời gian hiện tại (đã đến lúc)
        String sql = "SELECT * FROM placed_blocks WHERE respawn_at > 0 AND respawn_at <= ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, currentTime);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new PlacedBlockData(
                        rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                        rs.getString("mmo_id"), rs.getString("group_id"),
                        null, null, // UUID sẽ được tạo mới khi spawn
                        rs.getDouble("hb_off_x"), rs.getDouble("hb_off_y"), rs.getDouble("hb_off_z")
                ));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }
    public boolean isWaitingForRespawn(String world, int x, int y, int z, long currentTime) {
        String sql = "SELECT respawn_at FROM placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long respawnAt = rs.getLong("respawn_at");
                // Trả về true nếu respawn_at > 0 và chưa tới giờ hiện tại (đang đợi)
                return respawnAt > 0 && respawnAt > currentTime;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    // Trong Database.java
    private void createTables() {
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS placed_blocks (" +
                    "world TEXT, x INTEGER, y INTEGER, z INTEGER, " +
                    "mmo_id TEXT, group_id TEXT, " +
                    "as_uuid TEXT, hitbox_uuid TEXT, " +
                    "hb_off_x REAL, hb_off_y REAL, hb_off_z REAL, " +
                    "respawn_at INTEGER DEFAULT 0, " + // THÊM CỘT NÀY (Lưu timestamp milis)
                    "PRIMARY KEY (world, x, y, z))");
        } catch (SQLException e) { e.printStackTrace(); }
    }


    // Thêm hàm lấy Group ID
    public String getGroupIdAt(String world, int x, int y, int z) {
        String sql = "SELECT group_id FROM placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("group_id");
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public void savePlacedBlock(String world, int x, int y, int z,
                                String mmoId, String groupId,
                                UUID asUUID, UUID hbUUID,
                                double ox, double oy, double oz) {
        // Câu lệnh SQL với 12 dấu hỏi tương ứng 12 cột
        String sql = "REPLACE INTO placed_blocks (world, x, y, z, mmo_id, group_id, as_uuid, hitbox_uuid, hb_off_x, hb_off_y, hb_off_z, respawn_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.setString(5, mmoId);
            ps.setString(6, groupId);
            ps.setString(7, asUUID != null ? asUUID.toString() : null);
            ps.setString(8, hbUUID != null ? hbUUID.toString() : null);
            ps.setDouble(9, ox);
            ps.setDouble(10, oy);
            ps.setDouble(11, oz);
            ps.setLong(12, 0); // Khi lưu quặng sống, đặt respawn_at mặc định là 0
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getArmorStandUUID(String world, int x, int y, int z) {
        String sql = "SELECT as_uuid FROM placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("as_uuid");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getHitboxUUID(String world, int x, int y, int z) {
        String sql = "SELECT hitbox_uuid FROM placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("hitbox_uuid");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void removePlacedBlock(String world, int x, int y, int z) {
        String sql = "DELETE FROM placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<PlacedBlockData> getAllPlacedBlocks() {
        List<PlacedBlockData> blocks = new ArrayList<>();
        String sql = "SELECT * FROM placed_blocks"; // Lấy tất cả các cột
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String asUuidStr = rs.getString("as_uuid");
                String hbUuidStr = rs.getString("hitbox_uuid");

                blocks.add(new PlacedBlockData(
                        rs.getString("world"),
                        rs.getInt("x"),
                        rs.getInt("y"),
                        rs.getInt("z"),
                        rs.getString("mmo_id"),
                        rs.getString("group_id"),
                        asUuidStr != null ? UUID.fromString(asUuidStr) : null,
                        hbUuidStr != null ? UUID.fromString(hbUuidStr) : null,
                        rs.getDouble("hb_off_x"), // Đọc offset X mới từ DB
                        rs.getDouble("hb_off_y"), // Đọc offset Y mới từ DB
                        rs.getDouble("hb_off_z")  // Đọc offset Z mới từ DB
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Lỗi nạp dữ liệu blocks: " + e.getMessage());
        }
        return blocks;
    }

    public String getMMOIdAt(org.bukkit.Location loc) {
        String sql = "SELECT mmo_id FROM placed_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, loc.getWorld().getName());
            ps.setInt(2, loc.getBlockX());
            ps.setInt(3, loc.getBlockY());
            ps.setInt(4, loc.getBlockZ());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("mmo_id");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}