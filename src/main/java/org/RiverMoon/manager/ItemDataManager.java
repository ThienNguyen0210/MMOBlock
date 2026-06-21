package org.RiverMoon.manager;

import org.RiverMoon.Main;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class ItemDataManager {
    private final File file;
    private YamlConfiguration config;

    public ItemDataManager(Main plugin) {
        
        this.file = new File(plugin.getDataFolder(), "itemdata.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    
    public void saveItem(String id, ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeObject(item);
            dataOutput.close();

            String base64 = Base64Coder.encodeLines(outputStream.toByteArray());
            config.set(id, base64);
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    public ItemStack getItem(String id) {
        String base64 = config.getString(id);
        if (base64 == null) return null;

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(base64));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }
}