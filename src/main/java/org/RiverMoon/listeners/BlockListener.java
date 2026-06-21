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
    private final LanguageManager lang; // Khai báo LanguageManager

    public BlockListener(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        // Lấy ID từ database trước để biết đây có phải block của plugin không
        String mmoId = plugin.getDatabase().getMMOIdAt(loc);
        if (mmoId == null) return;

        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // LOGIC XÓA VĨNH VIỄN (Admin + Sneak + Break)
        if (player.isOp() && player.isSneaking()) {
            // 1. Xóa ArmorStand (Model)
            String asUuidStr = plugin.getDatabase().getArmorStandUUID(worldName, x, y, z);
            if (asUuidStr != null) {
                removeEntityByUUID(asUuidStr);
            }

            // 2. Xóa Interaction (Hitbox)
            String hitboxUuidStr = plugin.getDatabase().getHitboxUUID(worldName, x, y, z);
            if (hitboxUuidStr != null) {
                removeEntityByUUID(hitboxUuidStr);
            }

            // 3. Xóa Hologram
            plugin.getHoloManager().removeHolo(loc);

            // 4. Xóa dữ liệu trong Database
            plugin.getDatabase().removePlacedBlock(worldName, x, y, z);

            // 5. THỰC HIỆN XÓA BLOCK (Không cancel event)
            player.sendMessage(lang.getMessage("block.removed"));

            // Quan trọng: Return ở đây để không chạy xuống dòng setCancelled phía dưới
            return;
        }

        // Nếu không phải Admin xóa, hoặc không Sneak -> Chặn việc phá block
        event.setCancelled(true);
    }



    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Kiểm tra xem người chơi có đang trong chế độ Edit Structure không
        if (plugin.getStructureManager().isEditing(uuid)) {
            if (event.getClickedBlock() == null) return;

            // Cancel event để không phá block thật hoặc mở menu block (như Chest/Furnace)
            event.setCancelled(true);
            boolean isShift = player.isSneaking();

            // --- CHUỘT TRÁI: CHỌN BLOCK (Biến thành AIR) ---
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (isShift) {
                    // Shift + Left: Chỉ chọn đúng 1 block duy nhất
                    plugin.getStructureManager().addSingleBlock(uuid, event.getClickedBlock());
                } else {
                    // Left Click: Chọn theo Shape (Sphere/Solid) và lưu vào 1 tầng Undo
                    plugin.getStructureManager().addBlocksInShape(uuid, event.getClickedBlock());
                }
            }

            // --- CHUỘT PHẢI: HOÀN TÁC (UNDO) ---
            else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                if (isShift) {
                    // Shift + Right: Undo lại lần click gần nhất (Tối đa 10 lần)
                    plugin.getStructureManager().undoLastAction(uuid);
                } else {
                    // Right Click thường: Bạn có thể để trống hoặc dùng để "xóa" vùng chọn
                    // (trả block về trạng thái cũ mà không theo tầng Undo)
                    plugin.getStructureManager().removeBlocksInShape(uuid, event.getClickedBlock());
                }
            }
        }
    }
    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        // 1. Kiểm tra tiêu đề GUI (Nên lấy từ LanguageManager nếu ông có đổi title trong config)
        String title = plugin.getLang().getMessage("list.title");
        if (!event.getView().getTitle().equals(title != null ? title : "§0MMOBlock Manager")) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR) return;

        Player player = (Player) event.getWhoClicked();
        org.bukkit.inventory.meta.ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) return;

        // 2. Trích xuất tọa độ từ PDC thay vì Lore
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

            // 3. THỰC HIỆN XÓA
            // Lấy UUID trước khi xóa bản ghi trong DB
            String asUuidStr = plugin.getDatabase().getArmorStandUUID(worldName, x, y, z);
            String hitboxUuidStr = plugin.getDatabase().getHitboxUUID(worldName, x, y, z);

            removeEntityByUUID(asUuidStr);
            removeEntityByUUID(hitboxUuidStr);

            // Xóa Hologram & Block vật lý
            plugin.getHoloManager().removeHolo(loc);
            loc.getBlock().setType(Material.AIR);

            // Xóa khỏi Database
            plugin.getDatabase().removePlacedBlock(worldName, x, y, z);

            // 4. Thông báo thành công (Dùng message từ config cho chuyên nghiệp)
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