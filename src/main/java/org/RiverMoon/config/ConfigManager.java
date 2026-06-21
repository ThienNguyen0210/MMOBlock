package org.RiverMoon.config;

import org.RiverMoon.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final Main plugin;
    private final Map<String, FileConfiguration> blockConfigs = new HashMap<>();

    public ConfigManager(Main plugin) {
        this.plugin = plugin;
    }

    public void reloadAll() {
        plugin.reloadConfig();

        blockConfigs.clear();

        File blocksFolder = new File(plugin.getDataFolder(), "blocks");
        if (!blocksFolder.exists()) {
            blocksFolder.mkdirs();
        }

        loadFilesRecursively(blocksFolder);

        plugin.getLogger().info("Đã reload thành công " + blockConfigs.size() + " tệp cấu hình block!");
    }

    private void loadFilesRecursively(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                loadFilesRecursively(file);
            } else if (file.getName().endsWith(".yml")) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                String id = file.getName().replace(".yml", "");
                blockConfigs.put(id, config);
            }
        }
    }
    public java.util.Set<String> getLoadedBlockIds() {
        return blockConfigs.keySet();
    }
    public FileConfiguration getConfig(String id) {
        return blockConfigs.get(id);
    }
}