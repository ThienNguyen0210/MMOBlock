package org.RiverMoon.manager;

import org.RiverMoon.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class GroupManager {
    private final Main plugin;
    
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

        
        this.groupsConfig = YamlConfiguration.loadConfiguration(file);

        for (String groupId : groupsConfig.getKeys(false)) {
            List<GroupEntry> entries = new ArrayList<>();
            ConfigurationSection section = groupsConfig.getConfigurationSection(groupId);
            if (section == null) continue;

            for (String mmoId : section.getKeys(false)) {
                
                if (mmoId.equalsIgnoreCase("delay")) continue;

                double weight = section.getDouble(mmoId, 1.0);
                entries.add(new GroupEntry(mmoId, weight));
            }
            groups.put(groupId, entries);
        }
        plugin.getLogger().info("§e[MMOBlock] Đã nạp " + groups.size() + " nhóm quặng ngẫu nhiên.");
    }

    
    public int getGroupDelay(String groupId) {
        if (groupsConfig == null) return 0;
        
        return groupsConfig.getInt(groupId + ".delay", 0);
    }

    
    public String getRandomId(String groupId) {
        List<GroupEntry> entries = groups.get(groupId);
        if (entries == null || entries.isEmpty()) return null;

        
        double totalWeight = 0;
        for (GroupEntry entry : entries) {
            totalWeight += entry.weight;
        }

        
        double randomValue = Math.random() * totalWeight;

        
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

    
    private record GroupEntry(String id, double weight) {}
}