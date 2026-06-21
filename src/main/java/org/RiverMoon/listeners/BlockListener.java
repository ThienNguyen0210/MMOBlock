package org.RiverMoon.listeners;

import org.RiverMoon.Main;
import org.RiverMoon.manager.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.UUID;

public class BlockListener implements Listener {
    private final Main plugin;
    private final LanguageManager lang; 

    public BlockListener(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        
        String mmoId = plugin.getDatabase().getMMOIdAt(loc);
        if (mmoId == null) return;

        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        
        if (player.isOp() && player.isSneaking()) {
            
            String asUuidStr = plugin.getDatabase().getArmorStandUUID(worldName, x, y, z);
            if (asUuidStr != null) {
                removeEntityByUUID(asUuidStr);
            }

            
            String hitboxUuidStr = plugin.getDatabase().getHitboxUUID(worldName, x, y, z);
            if (hitboxUuidStr != null) {
                removeEntityByUUID(hitboxUuidStr);
            }

            
            plugin.getHoloManager().removeHolo(loc);

            
            plugin.getDatabase().removePlacedBlock(worldName, x, y, z);

            
            player.sendMessage(lang.getMessage("block.removed"));

            
            return;
        }

        
        event.setCancelled(true);
    }



    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        
        if (plugin.getStructureManager().isEditing(uuid)) {
            if (event.getClickedBlock() == null) return;

            
            event.setCancelled(true);
            boolean isShift = player.isSneaking();

            
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (isShift) {
                    
                    plugin.getStructureManager().addSingleBlock(uuid, event.getClickedBlock());
                } else {
                    
                    plugin.getStructureManager().addBlocksInShape(uuid, event.getClickedBlock());
                }
            }

            
            else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (isShift) {
                    
                    plugin.getStructureManager().undoLastAction(uuid);
                } else {
                    
                    
                    plugin.getStructureManager().removeBlocksInShape(uuid, event.getClickedBlock());
                }
            }
        }
    }
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        
        String title = plugin.getLang().getMessage("list.title");
        if (!event.getView().getTitle().equals(title != null ? title : "§0MMOBlock Manager")) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        org.bukkit.inventory.meta.ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;

        
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "block_location");
        String locRaw = meta.getPersistentDataContainer().get(key, org.bukkit.persistence.PersistentDataType.STRING);

        if (locRaw == null) return;

        try {
            String[] parts = locRaw.split(":");
            String worldName = parts[0];
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);

            org.bukkit.World world = Bukkit.getWorld(worldName);
            if (world == null) return;
            org.bukkit.Location loc = new org.bukkit.Location(world, x, y, z);

            
            
            String asUuidStr = plugin.getDatabase().getArmorStandUUID(worldName, x, y, z);
            String hitboxUuidStr = plugin.getDatabase().getHitboxUUID(worldName, x, y, z);

            removeEntityByUUID(asUuidStr);
            removeEntityByUUID(hitboxUuidStr);

            
            plugin.getHoloManager().removeHolo(loc);
            loc.getBlock().setType(Material.AIR);

            
            plugin.getDatabase().removePlacedBlock(worldName, x, y, z);

            
            String successMsg = plugin.getLang().getMessage("list.delete-success");
            if (successMsg != null) {
                player.sendMessage(successMsg
                        .replace("%id%", meta.getDisplayName())
                        .replace("%coords%", x + ", " + y + ", " + z));
            } else {
                player.sendMessage("§c[MMOBlock] Đã xóa khối tại " + x + ", " + y + ", " + z);
            }

            player.closeInventory();

        } catch (Exception e) {
            player.sendMessage("§cLỗi khi trích xuất dữ liệu ẩn từ Item!");
            e.printStackTrace();
        }
    }

    private void removeEntityByUUID(String uuidStr) {
        if (uuidStr == null || uuidStr.isEmpty() || uuidStr.equals("null")) return;
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) entity.remove();
        } catch (Exception ignored) {}
    }
}