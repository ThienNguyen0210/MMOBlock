package org.RiverMoon.manager;

import dev.lone.itemsadder.api.CustomStack;
import me.clip.placeholderapi.PlaceholderAPI;
import net.Indyuce.mmoitems.MMOItems;
import org.RiverMoon.Main;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class DropManager {
    private final Main plugin; 
    private final Random random = new Random();

    public DropManager(Main plugin) {
        this.plugin = plugin;
    }

    public void handleDrops(Player player, ConfigurationSection dropsSection, List<String> allowedDrops, Location loc, String actionType) {
        if (dropsSection == null) return;

        for (String dropKey : dropsSection.getKeys(false)) {
            if (allowedDrops != null && !allowedDrops.isEmpty() && !allowedDrops.contains(dropKey)) continue;

            ConfigurationSection dropSec = dropsSection.getConfigurationSection(dropKey);
            if (dropSec == null) continue;

            String targetAction = dropSec.getString("target", "both_click").toLowerCase();
            if (!targetAction.equals("both_click") && !targetAction.contains(actionType.toLowerCase())) {
                continue;
            }

            if (dropSec.contains("conditions")) {
                if (!checkConditions(player, dropSec.getStringList("conditions"))) {
                    continue;
                }
            }

            double chance = dropSec.getDouble("item.chances", dropSec.getDouble("command.chances", 1.0));
            if (random.nextDouble() > chance) continue;

            if (dropSec.contains("item")) {
                giveItemDrop(player, dropSec.getConfigurationSection("item"), loc);
            }

            if (dropSec.contains("command")) {
                executeCommandDrop(player, dropSec.getConfigurationSection("command"));
            }
        }
    }

    private boolean checkConditions(Player player, List<String> conditions) {
        if (conditions == null || conditions.isEmpty()) return true;
        boolean hasPAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        for (String condition : conditions) {
            String parsed = hasPAPI ? PlaceholderAPI.setPlaceholders(player, condition) : condition;
            if (!evaluateExpression(parsed)) return false;
        }
        return true;
    }

    private boolean evaluateExpression(String expression) {
        try {
            String[] operators = {">=", "<=", "==", "!=", ">", "<"};
            String selectedOp = null;
            for (String op : operators) {
                if (expression.contains(op)) {
                    selectedOp = op;
                    break;
                }
            }
            if (selectedOp == null) return false;
            String[] parts = expression.split(selectedOp);
            String left = parts[0].trim();
            String right = parts[1].trim();

            if (isDouble(left) && isDouble(right)) {
                double v1 = Double.parseDouble(left);
                double v2 = Double.parseDouble(right);
                return switch (selectedOp) {
                    case ">=" -> v1 >= v2;
                    case "<=" -> v1 <= v2;
                    case ">" -> v1 > v2;
                    case "<" -> v1 < v2;
                    case "==" -> v1 == v2;
                    case "!=" -> v1 != v2;
                    default -> false;
                };
            }
            return switch (selectedOp) {
                case "==" -> left.equalsIgnoreCase(right);
                case "!=" -> !left.equalsIgnoreCase(right);
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private void giveItemDrop(Player player, ConfigurationSection sec, Location loc) {
        String matStr = sec.getString("material", "STONE");
        int amount = getRandomAmount(sec.getString("total", "1"));
        String dropType = sec.getString("drop_type", "inventory");

        ItemStack item = null;

        
        if (matStr.toLowerCase().startsWith("data_")) {
            String dataId = matStr.substring(5);
            item = plugin.getItemDataManager().getItem(dataId);
        }

        
        if (item == null && matStr.contains(":")) {
            String[] parts = matStr.split(":");

            
            if (Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
                try {
                    
                    item = net.Indyuce.mmoitems.MMOItems.plugin.getItem(
                            parts[0].toUpperCase(),
                            parts[1].toUpperCase()
                    );
                } catch (Throwable ignored) {
                    
                }
            }

            
            if (item == null && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
                try {
                    String iaId = matStr.startsWith("_iainternal:") ? matStr.replace("_iainternal:", "") : matStr;
                    dev.lone.itemsadder.api.CustomStack cs = dev.lone.itemsadder.api.CustomStack.getInstance(iaId);
                    if (cs != null) item = cs.getItemStack();
                } catch (Throwable ignored) {}
            }
        }

        
        if (item == null) {
            Material mat = Material.matchMaterial(matStr.toUpperCase());
            if (mat == null) mat = Material.STONE;
            item = new ItemStack(mat);

            
            if (mat == Material.STONE && !matStr.equalsIgnoreCase("STONE")) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§c§lLỖI VẬT PHẨM");
                    meta.setLore(java.util.Arrays.asList("§cError: " + matStr, "§cLỗi cấu hình hoặc thiếu Plugin hỗ trợ"));
                    item.setItemMeta(meta);
                }
            }
        }

        
        item = item.clone();
        item.setAmount(amount);

        
        if (dropType.equalsIgnoreCase("inventory")) {
            player.getInventory().addItem(item).values().forEach(over ->
                    loc.getWorld().dropItemNaturally(loc, over));
        } else if (dropType.equalsIgnoreCase("center_ground")) {
            spawnSpecialDrop(item, loc, sec);
        } else {
            loc.getWorld().dropItemNaturally(loc, item);
        }
    }

    private void spawnSpecialDrop(ItemStack itemStack, Location loc, ConfigurationSection sec) {
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        Item droppedItem = loc.getWorld().dropItem(center, itemStack);
        double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
        double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.3;
        double y = 0.45;
        droppedItem.setVelocity(new Vector(x, y, z));
        droppedItem.setGlowing(sec.getBoolean("glow", false));
    }

    private void executeCommandDrop(Player player, ConfigurationSection sec) {
        String cmd = sec.getString("value");
        int amount = getRandomAmount(sec.getString("amount", "1"));
        if (cmd != null) {
            String finalCmd = cmd.replace("%player%", player.getName())
                    .replace("%amount%", String.valueOf(amount));

            Bukkit.getScheduler().runTask(plugin, () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
        }
    }

    private int getRandomAmount(String raw) {
        try {
            String clean = raw.replace("[", "").replace("]", "").trim();
            if (clean.contains("-")) {
                String[] parts = clean.split("-");
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            }
            return Integer.parseInt(clean);
        } catch (Exception e) {
            return 1;
        }
    }

    private boolean isDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}