package org.RiverMoon.manager;

import org.RiverMoon.Main;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class LanguageManager {
    private final Main plugin;
    private FileConfiguration langConfig;

    public LanguageManager(Main plugin) {
        this.plugin = plugin;
        // Tự động bung các file mặc định trước khi load
        saveDefaultLanguages();
        reloadLanguage();
    }

    private void saveDefaultLanguages() {
        File langFolder = new File(plugin.getDataFolder(), "languages");
        if (!langFolder.exists()) langFolder.mkdirs();

        String[] defaults = {"en.yml", "vi.yml"};

        for (String fileName : defaults) {
            File file = new File(langFolder, fileName);

            if (!file.exists()) {
                try {
                    InputStream is = plugin.getResource("languages/" + fileName);
                    if (is != null) {
                        plugin.saveResource("languages/" + fileName, false);
                        plugin.getLogger().info("Successfully exported default language: " + fileName);
                    } else {
                        plugin.getLogger().warning("Could not find 'languages/" + fileName + "' inside the JAR file!");
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error while exporting " + fileName + ": " + e.getMessage());
                }
            }

        }
    }

    public void reloadLanguage() {
        String langName = plugin.getConfig().getString("language", "en");

        File langFile = new File(plugin.getDataFolder(), "languages/" + langName + ".yml");

        if (!langFile.exists()) {
            plugin.getLogger().severe("Language file " + langName + ".yml not found! Falling back to en.yml");
            langFile = new File(plugin.getDataFolder(), "languages/en.yml");

            if (!langFile.exists()) {
                try {
                    langFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        InputStream defaultStream = plugin.getResource("languages/" + langName + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaultXml = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defaultXml);
        }
    }

    public String getMessage(String path) {
        String msg = langConfig.getString(path);
        if (msg == null) return "§cMissing lang key: " + path;
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}