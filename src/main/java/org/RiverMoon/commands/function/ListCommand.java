package org.RiverMoon.commands.function;

import org.RiverMoon.Main;
import org.RiverMoon.database.PlacedBlockData;
import org.RiverMoon.manager.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ListCommand implements SubCommand {
    private final Main plugin;
    private final LanguageManager lang;

    public ListCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @Override
    public String getName() { return "list"; }

    @Override
    public String getDescription() { return "Mở danh sách các MMOBlock đã đặt"; }

    @Override
    public String getSyntax() { return "/mmoblock list"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;

        // Lấy tiêu đề từ LanguageManager cho đồng bộ
        String title = lang.getMessage("list.title");
        Inventory inv = Bukkit.createInventory(null, 54, title != null ? title : "§0MMOBlock Manager");

        List<PlacedBlockData> allBlocks = plugin.getDatabase().getAllPlacedBlocks();

        for (PlacedBlockData data : allBlocks) {
            String mmoId = data.getMmoId();
            FileConfiguration config = plugin.getConfigManager().getConfig(mmoId);

            Material iconMat = Material.STONE;
            boolean isItemsAdder = false;

            if (config != null) {
                if (config.getBoolean(mmoId + ".block-itemsadder.enabled", false)) {
                    iconMat = Material.matchMaterial(config.getString(mmoId + ".block-itemsadder.material", "PAPER"));
                    isItemsAdder = true;
                } else {
                    iconMat = Material.matchMaterial(config.getString(mmoId + ".block-settings.material", "STONE"));
                }
            }

            ItemStack item = new ItemStack(iconMat != null ? iconMat : Material.BARRIER);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.setDisplayName("§eID: §f" + mmoId);

                NamespacedKey key = new NamespacedKey(plugin, "block_location");
                String locationData = data.getWorldName() + ":" + data.getX() + ":" + data.getY() + ":" + data.getZ();
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, locationData);

                // Tạo Lore dựa trên LanguageManager
                List<String> lore = new ArrayList<>();
                lore.add(lang.getMessage("list.gui-world").replace("%world%", data.getWorldName()));
                lore.add(lang.getMessage("list.gui-coords")
                        .replace("%x%", String.valueOf(data.getX()))
                        .replace("%y%", String.valueOf(data.getY()))
                        .replace("%z%", String.valueOf(data.getZ())));

                String typeStr = isItemsAdder ? "ItemsAdder (Model)" : "Vanilla Block";
                lore.add(lang.getMessage("list.gui-type").replace("%type%", typeStr));

                lore.add("");
                lore.add(lang.getMessage("list.gui-delete-hint"));

                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.addItem(item);
        }
        player.openInventory(inv);
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}