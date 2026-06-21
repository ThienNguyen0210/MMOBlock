package org.RiverMoon.manager;

import org.RiverMoon.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public class StructureManager {
    private final Main plugin;
    private final Map<UUID, Boolean> isEditing = new HashMap<>();
    private final Map<UUID, LinkedList<Map<Location, Material>>> undoHistory = new HashMap<>();
    private final Map<UUID, Map<Location, Material>> allSelectedBlocks = new HashMap<>();
    private final Map<UUID, String> playerShape = new HashMap<>();
    private final Map<UUID, Integer> playerSize = new HashMap<>();

    public StructureManager(Main plugin) {
        this.plugin = plugin;
    }

    public void setPlayerEditTool(UUID uuid, String shape, int size) {
        playerShape.put(uuid, shape.toLowerCase());
        playerSize.put(uuid, size);
    }

    
    public void addSingleBlock(UUID uuid, Block block) {
        if (!isEditing.getOrDefault(uuid, false)) return;

        Map<Location, Material> changes = new HashMap<>();
        processBlockSelection(uuid, block, changes);

        if (!changes.isEmpty()) {
            saveToUndoHistory(uuid, changes);
            sendMessage(uuid, "§a+ Đã chọn 1 block.");
        }
    }

    
    public void addBlocksInShape(UUID uuid, Block centerBlock) {
        if (!isEditing.getOrDefault(uuid, false)) return;

        String shape = playerShape.getOrDefault(uuid, "single");
        int size = playerSize.getOrDefault(uuid, 1);
        Location center = centerBlock.getLocation();
        int radius = size / 2;

        Map<Location, Material> currentClickChanges = new HashMap<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    boolean inside = (shape.equals("sphere"))
                            ? (x * x + y * y + z * z <= radius * radius)
                            : true;

                    if (inside) {
                        processBlockSelection(uuid, center.clone().add(x, y, z).getBlock(), currentClickChanges);
                    }
                }
            }
        }

        if (!currentClickChanges.isEmpty()) {
            saveToUndoHistory(uuid, currentClickChanges);
            sendMessage(uuid, "§a+ Đã chọn " + currentClickChanges.size() + " blocks.");
        }
    }

    
    public void removeBlocksInShape(UUID uuid, Block centerBlock) {
        if (!isEditing.getOrDefault(uuid, false)) return;

        int size = playerSize.getOrDefault(uuid, 1);
        int radius = size / 2;
        Map<Location, Material> allBlocks = allSelectedBlocks.get(uuid);

        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = centerBlock.getLocation().add(x, y, z).getBlock().getLocation();
                    if (allBlocks.containsKey(loc)) {
                        loc.getBlock().setType(allBlocks.get(loc));
                        allBlocks.remove(loc);
                        count++;
                    }
                }
            }
        }
        if (count > 0) sendMessage(uuid, "§c- Đã bỏ chọn " + count + " blocks.");
    }

    
    public void undoLastAction(UUID uuid) {
        LinkedList<Map<Location, Material>> history = undoHistory.get(uuid);
        if (history == null || history.isEmpty()) {
            sendMessage(uuid, "§cKhông còn gì để Undo!");
            return;
        }

        Map<Location, Material> lastAction = history.removeFirst();
        lastAction.forEach((loc, mat) -> {
            loc.getBlock().setType(mat);
            allSelectedBlocks.get(uuid).remove(loc);
        });

        sendMessage(uuid, "§e[MMOBlock] §fĐã Undo (" + lastAction.size() + " blocks). Còn: " + history.size() + "/10");
    }

    

    private void processBlockSelection(UUID uuid, Block target, Map<Location, Material> currentChanges) {
        if (target.getType() == Material.AIR || target.getType() == Material.CAVE_AIR) return;

        Location loc = target.getLocation().getBlock().getLocation();
        if (!allSelectedBlocks.get(uuid).containsKey(loc)) {
            Material original = target.getType();
            currentChanges.put(loc, original);
            allSelectedBlocks.get(uuid).put(loc, original);
            target.setType(Material.AIR);
        }
    }

    private void saveToUndoHistory(UUID uuid, Map<Location, Material> changes) {
        LinkedList<Map<Location, Material>> history = undoHistory.get(uuid);
        history.addFirst(changes);
        if (history.size() > 10) history.removeLast();
    }

    public void toggleEditMode(UUID uuid) {
        if (isEditing.getOrDefault(uuid, false)) {
            
            Map<Location, Material> blocks = allSelectedBlocks.get(uuid);
            if (blocks != null) {
                blocks.forEach((loc, mat) -> {
                    
                    if (loc.getBlock().getType() == Material.AIR || loc.getBlock().getType() == Material.CAVE_AIR) {
                        loc.getBlock().setType(mat);
                    }
                });
            }
            

            
            isEditing.put(uuid, false);
            undoHistory.remove(uuid);
            allSelectedBlocks.remove(uuid);
            playerShape.remove(uuid);
            playerSize.remove(uuid);

            sendMessage(uuid, "§e[MMOBlock] §cĐã tắt Edit Mode. Toàn bộ block đã được khôi phục.");
        } else {
            isEditing.put(uuid, true);
            undoHistory.put(uuid, new LinkedList<>());
            allSelectedBlocks.put(uuid, new HashMap<>());
            sendMessage(uuid, "§e[MMOBlock] §aĐã bật Edit Mode. (Dùng Shift + Chuột phải để Undo)");
        }
    }

    public Map<Location, Material> getSavedBlocks(UUID uuid) { return allSelectedBlocks.get(uuid); }
    public boolean isEditing(UUID uuid) { return isEditing.getOrDefault(uuid, false); }
    private void sendMessage(UUID uuid, String msg) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) p.sendMessage(msg);
    }
}