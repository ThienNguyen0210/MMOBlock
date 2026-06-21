package org.RiverMoon.manager;

import dev.lone.itemsadder.api.CustomStack;
import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.ItemStats;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.data.DoubleData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class ToolManager {

    public ConfigurationSection getMatchingTool(Player player, ConfigurationSection allowedTools, String actionType) {
        if (allowedTools == null) return null;
        ItemStack hand = player.getInventory().getItemInMainHand();
        String baseAction = actionType.replace("_click", "").toLowerCase();

        for (String key : allowedTools.getKeys(false)) {
            ConfigurationSection toolSec = allowedTools.getConfigurationSection(key);
            String reqMat = toolSec.getString("material");
            if (isMatching(hand, reqMat)) {
                if (toolSec.contains("both_click")) return toolSec;
                if (toolSec.contains(baseAction + "_click")) return toolSec;
            }
        }
        return null;
    }

    public void handleDurability(Player player, ConfigurationSection toolConfig, String actionType) {
        int damageAmount = 0;
        String baseAction = actionType.replace("_click", "").toLowerCase();

        if (toolConfig.contains("both_click")) {
            damageAmount = toolConfig.getInt("both_click.decreaseDurability", 0);
        } else if (toolConfig.contains(baseAction + "_click")) {
            damageAmount = toolConfig.getInt(baseAction + "_click.decreaseDurability", 0);
        }

        if (damageAmount <= 0) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        // Kiểm tra xem có phải item có NBT Type (thường là MMOItems) không
        NBTItem nbtItem = NBTItem.get(hand);
        if (nbtItem.hasType()) {
            try {
                // Gọi một hàm riêng để xử lý MMOItems
                if (applyMMOItemsDurability(player, hand, nbtItem, damageAmount)) return;
            } catch (NoClassDefFoundError | Exception e) {
                // Nếu lỗi class (thiếu plugin), code sẽ tự trôi xuống phần Vanilla bên dưới
            }
        }

        // Vanilla Logic - Sẽ chạy nếu không phải MMOItems HOẶC nếu MMOItems API bị lỗi
        if (hand.getItemMeta() instanceof Damageable meta) {
            meta.setDamage(meta.getDamage() + damageAmount);
            hand.setItemMeta(meta);
            if (meta.getDamage() >= hand.getType().getMaxDurability()) breakItem(player);
        }
    }

    // Hàm phụ trách riêng cho MMOItems để cô lập lỗi nạp class
    private boolean applyMMOItemsDurability(Player player, ItemStack hand, NBTItem nbtItem, int damageAmount) {
        LiveMMOItem mmoItem = new LiveMMOItem(nbtItem);
        double currentDurability = -1;

        if (nbtItem.hasTag("MMOITEMS_CUSTOM_DURABILITY")) {
            currentDurability = nbtItem.getDouble("MMOITEMS_CUSTOM_DURABILITY");
        }

        if (currentDurability <= 0) {
            if (mmoItem.hasData(ItemStats.CUSTOM_DURABILITY)) {
                currentDurability = ((DoubleData) mmoItem.getData(ItemStats.CUSTOM_DURABILITY)).getValue();
            } else if (mmoItem.hasData(ItemStats.MAX_DURABILITY)) {
                currentDurability = ((DoubleData) mmoItem.getData(ItemStats.MAX_DURABILITY)).getValue();
            } else {
                currentDurability = hand.getType().getMaxDurability();
            }
        }

        double newDurability = Math.max(0, currentDurability - damageAmount);
        mmoItem.setData(ItemStats.CUSTOM_DURABILITY, new DoubleData(newDurability));

        ItemStack newItem = mmoItem.newBuilder().build();
        NBTItem finalNbt = NBTItem.get(newItem);
        finalNbt.setDouble("MMOITEMS_CUSTOM_DURABILITY", newDurability);

        player.getInventory().setItemInMainHand(finalNbt.toItem());
        if (newDurability <= 0) breakItem(player);
        return true;
    }

    private void breakItem(Player player) {
        player.getInventory().setItemInMainHand(null);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
    }

    private boolean isMatching(ItemStack hand, String req) {
        if (req == null || hand == null || hand.getType() == Material.AIR) return false;
        if (req.contains(":")) {
            String[] parts = req.split(":");
            String prefix = parts[0];
            String id = parts[1];

            if (prefix.equalsIgnoreCase("_iainternal")) {
                CustomStack cs = CustomStack.byItemStack(hand);
                return cs != null && cs.getNamespacedID().equals(id);
            }

            NBTItem n = NBTItem.get(hand);
            if (n.hasType()) {
                return n.getType().equalsIgnoreCase(prefix) &&
                        n.getString("MMOITEMS_ITEM_ID").equalsIgnoreCase(id);
            }
        }
        return hand.getType().name().equalsIgnoreCase(req);
    }
}