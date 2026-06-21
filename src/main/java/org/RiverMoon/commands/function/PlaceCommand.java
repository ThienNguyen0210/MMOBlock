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
import java.util.stream.Collectors;

public class PlaceCommand implements SubCommand {
    private final Main plugin;
    private final LanguageManager lang;

    public PlaceCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @Override
    public String getName() { return "place"; }

    @Override
    public String getDescription() { return lang.getMessage("commands.place.desc"); }

    @Override
    public String getSyntax() { return "/mmoblock place <id>"; }

    public void perform(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(lang.getMessage("general.no-console"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(lang.getMessage("general.usage").replace("%syntax%", getSyntax()));
            return;
        }

        String id = args[1];
        FileConfiguration config = plugin.getConfigManager().getConfig(id);

        if (config == null) {
            player.sendMessage(lang.getMessage("place.not-found").replace("%id%", id));
            return;
        }

        Location loc = player.getLocation().getBlock().getLocation();

        
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
        } else {
            loc.getBlock().setType(Material.AIR);
        }

        
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
                armorStand.setCanPickupItems(false);
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

        
        final Interaction[] spawnedInteraction = {null};
        ConfigurationSection hitboxSection = config.getConfigurationSection(id + ".hitbox");

        
        double offX = 0, offY = 0, offZ = 0;

        if (hitboxSection != null) {
            float width = (float) hitboxSection.getDouble("width", 1.0) + 0.01f;
            float height = (float) hitboxSection.getDouble("height", 1.0) + 0.01f;

            
            offX = hitboxSection.getDouble("offset_x", 0.0);
            offY = hitboxSection.getDouble("offset_y", 0.0);
            offZ = hitboxSection.getDouble("offset_z", 0.0);

            
            Location spawnLoc = loc.clone().add(0.5 + offX, 0.0 + offY, 0.5 + offZ);

            spawnedInteraction[0] = loc.getWorld().spawn(spawnLoc, Interaction.class, inter -> {
                inter.setInteractionWidth(width);
                inter.setInteractionHeight(height);
                inter.setResponsive(true);
                inter.setSilent(true);
                inter.addScoreboardTag("MMOBlock_Hitbox");
                inter.addScoreboardTag("MMO_ID_" + id);
            });
        }

        
        UUID hitboxUUID = (spawnedInteraction[0] != null) ? spawnedInteraction[0].getUniqueId() : null;

        
        plugin.getDatabase().savePlacedBlock(
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                id,null, asUUID, hitboxUUID, offX, offY, offZ
        );

        
        ConfigurationSection holoSection = config.getConfigurationSection(id + ".hologram.customHolo");
        if (holoSection != null) {
            plugin.getHoloManager().spawnHolo(loc, holoSection, null, null, null);
        }

        player.sendMessage(lang.getMessage("place.success").replace("%id%", id));
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return plugin.getConfigManager().getLoadedBlockIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}