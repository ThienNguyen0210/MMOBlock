package org.RiverMoon.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class HologramManager {
    private final Map<Location, List<UUID>> activeHolograms = new HashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();

    public Component parse(String input, String progress, String bar, String respawnTime) {
        if (input == null || input.isEmpty()) return Component.empty();

        if (progress != null) input = input.replace("%progress%", progress);
        if (bar != null) input = input.replace("%progress_bar%", bar);
        if (respawnTime != null) input = input.replace("%respawnTime%", respawnTime);

        String legacyText = input.replace("&", "§");

        Component legacyComp = LegacyComponentSerializer.legacySection().deserialize(legacyText);
        String miniMessageFormat = mm.serialize(legacyComp).replace("\\<", "<");

        return mm.deserialize(miniMessageFormat);
    }
    public String formatLegacy(String input, String progress, String bar, String respawnTime) {
        if (input == null || input.isEmpty()) return "";
        return LegacyComponentSerializer.legacySection().serialize(parse(input, progress, bar, respawnTime));
    }

    public void spawnHolo(Location loc, ConfigurationSection holoSection, String progress, String bar, String respawnTime) {
        removeHolo(loc);
        List<UUID> uuids = new ArrayList<>();

        double heightOffset = holoSection.getParent() != null ? holoSection.getParent().getDouble("height", 2.5) : 2.5;
        Location currentLoc = loc.clone().add(0.5, heightOffset, 0.5);

        List<String> keys = new ArrayList<>(holoSection.getKeys(false));
        keys.sort(Comparator.comparingInt(Integer::parseInt));

        for (String key : keys) {
            ConfigurationSection lineSection = holoSection.getConfigurationSection(key);
            if (lineSection == null) continue;

            String value = lineSection.getString("value", "");
            double lineSpacing = lineSection.getDouble("height", 0.3);

            currentLoc.add(0, lineSpacing, 0);

            if (value.equalsIgnoreCase("item")) {
                UUID itemUuid = spawnItemHolo(currentLoc, lineSection.getString("id", "PAPER"), lineSection.getInt("custom_model_data", 0));

                org.bukkit.entity.Entity itemEntity = Bukkit.getEntity(itemUuid);
                if (itemEntity != null) {
                    itemEntity.addScoreboardTag("MMOBlock_Holo");
                }
                uuids.add(itemUuid);
            } else {
                
                Component textComponent = parse(value, progress, bar, respawnTime);
                TextDisplay td = currentLoc.getWorld().spawn(currentLoc, TextDisplay.class, display -> {
                    display.setBillboard(Display.Billboard.CENTER);
                    display.text(textComponent);

                    
                    display.addScoreboardTag("MMOBlock_Holo");

                    if (holoSection.getParent() != null) {
                        display.setShadowed(holoSection.getParent().getBoolean("shadowed", true));
                        if (!holoSection.getParent().getBoolean("background", true)) {
                            display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        }
                    }
                });
                uuids.add(td.getUniqueId());
            }
        }
        activeHolograms.put(loc, uuids);
    }

    public void updateHoloText(Location loc, ConfigurationSection holoSection, String progress, String bar, String respawnTime) {
        List<UUID> uuids = activeHolograms.get(loc);

        int configLines = holoSection.getKeys(false).size();

        if (uuids == null || uuids.isEmpty() || uuids.size() != configLines) {
            spawnHolo(loc, holoSection, progress, bar, respawnTime);
            return;
        }

        List<String> keys = new ArrayList<>(holoSection.getKeys(false));
        keys.sort(Comparator.comparingInt(Integer::parseInt));

        int currentIndex = 0;
        for (String key : keys) {
            ConfigurationSection lineSection = holoSection.getConfigurationSection(key);
            if (lineSection == null) continue;

            String value = lineSection.getString("value", "");
            if (value.equalsIgnoreCase("item")) {
                currentIndex++;
                continue;
            }

            if (currentIndex < uuids.size()) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(uuids.get(currentIndex));
                if (entity instanceof TextDisplay td) {
                    td.text(parse(value, progress, bar, respawnTime));
                } else {
                    spawnHolo(loc, holoSection, progress, bar, respawnTime);
                    return;
                }
            }
            currentIndex++;
        }
    }
    private UUID spawnItemHolo(Location loc, String matName, int cmd) {
        ItemDisplay id = loc.getWorld().spawn(loc, ItemDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            Material mat = Material.matchMaterial(matName.toUpperCase());
            ItemStack item = new ItemStack(mat != null ? mat : Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(cmd);
                item.setItemMeta(meta);
            }
            display.setItemStack(item);
        });
        return id.getUniqueId();
    }

    public void removeHolo(Location loc) {
        List<UUID> uuids = activeHolograms.remove(loc);
        if (uuids != null) {
            uuids.forEach(uuid -> {
                org.bukkit.entity.Entity e = Bukkit.getEntity(uuid);
                if (e != null) e.remove();
            });
        }
    }

    public void removeAll() {
        new HashSet<>(activeHolograms.keySet()).forEach(this::removeHolo);
    }
}