package org.RiverMoon.manager;

import org.RiverMoon.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class GroupManager {
    private final Main plugin;
    // Khai báo biến này để fix lỗi biên dịch [cannot find symbol groupsConfig]
    private FileConfiguration groupsConfig;
    private final Map<String, List<GroupEntry>> groups = new HashMap<>();

    public GroupManager(Main plugin) {
        this.plugin = plugin;
    }

    public void loadGroups() {
        groups.clear();
        File file = new File(plugin.getDataFolder(), "groups.yml");
        if (!file.exists()) {
            plugin.saveResource("groups.yml", false);
        }

        // Gán vào biến toàn cục groupsConfig
        this.groupsConfig = YamlConfiguration.loadConfiguration(file);

        for (String groupId : groupsConfig.getKeys(false)) {
            List<GroupEntry> entries = new ArrayList<>();
            ConfigurationSection section = groupsConfig.getConfigurationSection(groupId);
            if (section == null) continue;

            for (String mmoId : section.getKeys(false)) {
                // QUAN TRỌNG: Bỏ qua key "delay" để không nạp nó vào danh sách random quặng
                if (mmoId.equalsIgnoreCase("delay")) continue;

                double weight = section.getDouble(mmoId, 1.0);
                entries.add(new GroupEntry(mmoId, weight));
            }
            groups.put(groupId, entries);
        }
        plugin.getLogger().info("§e[MMOBlock] Đã nạp " + groups.size() + " nhóm quặng ngẫu nhiên.");
    }

    /**
     * Lấy Delay chung của một Group từ file groups.yml
     */
    public int getGroupDelay(String groupId) {
        if (groupsConfig == null) return 0;
        // Đọc giá trị delay trực tiếp từ config
        return groupsConfig.getInt(groupId + ".delay", 0);
    }

    /**
     * Random một ID quặng dựa trên trọng số (Weight)
     */
    public String getRandomId(String groupId) {
        List<GroupEntry> entries = groups.get(groupId);
        if (entries == null || entries.isEmpty()) return null;

        // 1. Tính tổng tất cả trọng số
        double totalWeight = 0;
        for (GroupEntry entry : entries) {
            totalWeight += entry.weight;
        }

        // 2. Lấy một con số ngẫu nhiên
        double randomValue = Math.random() * totalWeight;

        // 3. Duyệt để tìm điểm dừng theo trọng số
        double cursor = 0;
        for (GroupEntry entry : entries) {
            cursor += entry.weight;
            if (randomValue <= cursor) {
                return entry.id;
            }
        }

        return entries.get(0).id;
    }

    public Set<String> getGroupIds() {
        return groups.keySet();
    }

    // Record để lưu trữ cặp ID và Trọng số
    private record GroupEntry(String id, double weight) {}
}