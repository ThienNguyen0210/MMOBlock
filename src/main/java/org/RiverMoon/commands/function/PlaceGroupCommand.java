package org.RiverMoon.commands.function;

import org.RiverMoon.Main;
import org.RiverMoon.manager.LanguageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlaceGroupCommand implements SubCommand {
    private final Main plugin;
    private final LanguageManager lang;

    public PlaceGroupCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @Override
    public String getName() { return "placegroup"; }

    @Override
    public String getDescription() { return "Đặt một mỏ quặng ngẫu nhiên theo nhóm"; }

    @Override
    public String getSyntax() { return "/mmoblock placegroup <groupId>"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.getMessage("general.no-console"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(lang.getMessage("general.usage").replace("%syntax%", getSyntax()));
            return;
        }

        String groupId = args[1];

        // 1. Lấy ID ngẫu nhiên đầu tiên từ GroupManager (Bạn cần tạo Manager này)
        // Nếu chưa có Manager, tạm thời lấy từ một List trong config groups.yml
        String id = plugin.getGroupManager().getRandomId(groupId);

        if (id == null) {
            player.sendMessage("§cKhông tìm thấy Group hoặc Group không có ID nào: " + groupId);
            return;
        }

        FileConfiguration config = plugin.getConfigManager().getConfig(id);
        if (config == null) return;

        Location loc = player.getLocation().getBlock().getLocation();

        // --- 2. XỬ LÝ BLOCK VẬT LÝ ---
        ConfigurationSection blockSettings = config.getConfigurationSection(id + ".block-settings");
        ConfigurationSection modelSettings = config.getConfigurationSection(id + ".block-itemsadder");

        boolean blockEnabled = blockSettings != null && blockSettings.getBoolean("enabled", true);
        boolean modelEnabled = modelSettings != null && modelSettings.getBoolean("enabled", false);

        if (modelEnabled && !blockEnabled) {
            loc.getBlock().setType(Material.BARRIER);
        } else if (blockEnabled) {
            String matStr = blockSettings.getString("material", "DRAGON_EGG");
            Material mat = Material.matchMaterial(matStr.toUpperCase());
            loc.getBlock().setType(mat != null ? mat : Material.BARRIER);
        }

        // --- 3. XỬ LÝ MODEL HIỂN THỊ (ARMORSTAND) ---
        UUID asUUID = null;
        if (modelEnabled) {
            String modelMatStr = modelSettings.getString("material", "PAPER");
            int modelId = modelSettings.getInt("model-id", 0);

            Location asLoc = loc.clone().add(0.5, -0.2, 0.5);
            ArmorStand as = asLoc.getWorld().spawn(asLoc, ArmorStand.class, armorStand -> {
                armorStand.setVisible(false);
                armorStand.setGravity(false);
                armorStand.setMarker(true);
                armorStand.setBasePlate(false);
                armorStand.addScoreboardTag("MMOBlock_Model");

                Material mat = Material.matchMaterial(modelMatStr.toUpperCase());
                ItemStack helmet = new ItemStack(mat != null ? mat : Material.PAPER);
                ItemMeta meta = helmet.getItemMeta();
                if (meta != null) {
                    meta.setCustomModelData(modelId);
                    helmet.setItemMeta(meta);
                }
                if (armorStand.getEquipment() != null) armorStand.getEquipment().setHelmet(helmet);
            });
            asUUID = as.getUniqueId();
        }

        // --- 4. TRIỆU HỒI HITBOX (INTERACTION) ---
        UUID hitboxUUID = null;
        double offX = 0, offY = 0, offZ = 0;
        ConfigurationSection hitboxSection = config.getConfigurationSection(id + ".hitbox");

        if (hitboxSection != null) {
            float width = (float) hitboxSection.getDouble("width", 1.0) + 0.01f;
            float height = (float) hitboxSection.getDouble("height", 1.0) + 0.01f;
            offX = hitboxSection.getDouble("offset_x", 0.0);
            offY = hitboxSection.getDouble("offset_y", 0.0);
            offZ = hitboxSection.getDouble("offset_z", 0.0);

            Location spawnLoc = loc.clone().add(0.5 + offX, 0.0 + offY, 0.5 + offZ);
            Interaction inter = loc.getWorld().spawn(spawnLoc, Interaction.class, i -> {
                i.setInteractionWidth(width);
                i.setInteractionHeight(height);
                i.setResponsive(true);
                i.addScoreboardTag("MMOBlock_Hitbox");
                i.addScoreboardTag("MMO_ID_" + id);
            });
            hitboxUUID = inter.getUniqueId();
        }

        // --- 5. LƯU VÀO DATABASE (QUAN TRỌNG: LƯU CẢ GROUPID) ---
        // Đảm bảo bạn đã sửa hàm savePlacedBlock trong Database.java để nhận thêm groupId
        plugin.getDatabase().savePlacedBlock(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                id, groupId, asUUID, hitboxUUID, offX, offY, offZ
        );

        // --- 6. HIỂN THỊ HOLOGRAM ---
        ConfigurationSection holoSection = config.getConfigurationSection(id + ".hologram.customHolo");
        if (holoSection != null) {
            plugin.getHoloManager().spawnHolo(loc, holoSection, null, null, null);
        }

        player.sendMessage("§a[MMOBlock] Đã đặt Node ngẫu nhiên thuộc nhóm: §f" + groupId);
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 2) {
            // Trả về danh sách Group ID từ Manager của bạn để Tab-Complete
            return new ArrayList<>(plugin.getGroupManager().getGroupIds());
        }
        return new ArrayList<>();
    }
}