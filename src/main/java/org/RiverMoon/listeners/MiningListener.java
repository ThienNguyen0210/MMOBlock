package org.RiverMoon.listeners;

import org.RiverMoon.Main;
import org.RiverMoon.manager.DropManager;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.api.item.mmoitem.MMOItem;
import net.Indyuce.mmoitems.api.Type;
import org.bukkit.inventory.ItemStack;
import net.Indyuce.mmoitems.MMOItems;
import io.lumine.mythic.lib.api.item.NBTItem;
import org.RiverMoon.manager.ToolManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import java.util.List;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MiningListener implements Listener {
    private final Main plugin;
    private final Map<Location, Integer> blockProgress = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<Location, BukkitTask> refundTasks = new HashMap<>();
    private ToolManager toolManager; // Đừng để final nếu muốn gán trong try-catch
    private final DropManager dropManager;
    public MiningListener(Main plugin) {
        this.plugin = plugin;
        this.dropManager = new DropManager(plugin);
        try {
            // Chỉ khởi tạo nếu class tồn tại
            this.toolManager = new ToolManager();
            plugin.getLogger().info("§a[MMOBlock] Đã kết nối với ToolManager.");
        } catch (NoClassDefFoundError | Exception e) {
            // Nếu thiếu MMOItems, toolManager sẽ là null
            this.toolManager = null;
            plugin.getLogger().warning("§c[MMOBlock] Không tìm thấy MMOItems. Chế độ Vanilla đã được kích hoạt.");
        }
    }

    // --- 1. CHẶN TƯƠNG TÁC BLOCK VẬT LÝ ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPhysicalInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation();

        if (plugin.getDatabase().getMMOIdAt(loc) != null) {
            if (event.getPlayer().isOp() && event.getPlayer().isSneaking()) return; // Cho phép Admin shift-break
            event.setCancelled(true);
        }
    }

    // --- 2. XỬ LÝ CHUỘT PHẢI VÀO HITBOX ---
    @EventHandler
    public void onHitboxInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        // Đổi "right" thành "right_click" để khớp với config
        handleMining(event.getPlayer(), interaction, "right_click");
    }

    // --- 3. XỬ LÝ CHUỘT TRÁI (ĐÀO) VÀO HITBOX ---
    @EventHandler
    public void onHitboxDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof Interaction interaction) {
            event.setDamage(0);
            handleMining(player, interaction, "left_click");
        }
    }
    private void handleMining(Player player, Interaction interaction, String actionType) {
        // 1. Kiểm tra trạng thái Hitbox và Quặng
        if (!interaction.getScoreboardTags().contains("MMOBlock_Hitbox") ||
                interaction.getScoreboardTags().contains("MMOBlock_Respawning") ||
                !interaction.isResponsive()) {
            return;
        }

        Location loc = new Location(
                interaction.getWorld(),
                interaction.getLocation().getBlockX(),
                interaction.getLocation().getBlockY(),
                interaction.getLocation().getBlockZ()
        );

        // 2. Admin shift-break
        if (player.isOp() && player.isSneaking()) {
            removeBlockPermanently(player, loc, interaction);
            return;
        }

        // 3. Lấy thông tin cấu hình block
        String mmoId = getMMOIdFromInteraction(interaction);
        if (mmoId == null) return;

        FileConfiguration config = plugin.getConfigManager().getConfig(mmoId);
        if (config == null) return;

        ConfigurationSection blockSec = config.getConfigurationSection(mmoId);
        if (blockSec == null) return;

        // 4. Kiểm tra điều kiện (Conditions)
        if (!checkConditions(player, config, mmoId)) return;

        // 5. Kiểm tra Cooldown - SỬA LỖI: Tránh chặn click quá gắt
        double cooldownTime = blockSec.getDouble("click_cooldown", 0.35);
        if (isInCooldown(player.getUniqueId(), cooldownTime)) return;

        // 6. Kiểm tra công cụ (Tool check)
        ConfigurationSection toolConfig = null;
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (this.toolManager != null) {
            toolConfig = toolManager.getMatchingTool(player, blockSec.getConfigurationSection("allowed_tools"), actionType);
        } else {
            ConfigurationSection allowedTools = blockSec.getConfigurationSection("allowed_tools");
            if (allowedTools != null && hand.getType() != Material.AIR) {
                String handMatName = hand.getType().name();
                for (String key : allowedTools.getKeys(false)) {
                    String requiredMat = allowedTools.getString(key + ".material");
                    if (requiredMat != null && handMatName.equalsIgnoreCase(requiredMat)) {
                        toolConfig = allowedTools.getConfigurationSection(key);
                        break;
                    }
                }
            }
        }

        // Nếu không cầm đúng tool, gửi feedback rồi thoát
        if (toolConfig == null) {
            String failTitle = blockSec.getString("send-title");
            String failSub = blockSec.getString("send-subtitle");
            sendFeedback(player, failTitle, failSub, null, null, blockSec.getBoolean("action_bar", true));
            return;
        }

        // 7. Xử lý độ bền
        if (this.toolManager != null) {
            toolManager.handleDurability(player, toolConfig, actionType);
        } else {
            // Cập nhật lại item cho player để Client không bị "đơ" sau khi trừ độ bền
            handleVanillaDurabilityFallback(player, toolConfig, actionType);
        }

        // 8. Tính toán sát thương
        int damage = toolConfig.contains("both_click")
                ? toolConfig.getInt("both_click.clickNeeded", 1)
                : toolConfig.getInt(actionType + ".clickNeeded", 1);

        // Phát âm thanh khi click
        String clickSound = blockSec.getString("sounds.onClick");
        if (clickSound != null && !clickSound.isEmpty()) {
            player.playSound(loc, clickSound, 1.0f, 1.0f);
        }

        // 9. LOGIC TIẾN TRÌNH (PROGRESS)
        int maxHealth = blockSec.getInt("health-block", 3);
        int currentProgress = blockProgress.getOrDefault(loc, 0);
        int newProgress = currentProgress + damage;

        // Hủy bỏ task refund cũ ngay khi nhận click mới để tránh reset progress
        cancelRefundTask(loc);

        if (newProgress >= maxHealth) {
            // Xử lý khi block vỡ
            blockProgress.remove(loc);
            plugin.getHoloManager().removeHolo(loc);

            List<String> allowedDrops = toolConfig.getStringList("allowedDrops");
            dropManager.handleDrops(player, blockSec.getConfigurationSection("drops"), allowedDrops, loc, actionType);

            String deathSound = blockSec.getString("sounds.onDeath");
            if (deathSound != null && !deathSound.isEmpty()) {
                player.playSound(loc, deathSound, 1.0f, 1.0f);
            }

            startRespawnLogic(loc, mmoId, config, interaction);
        } else {
            // Cập nhật progress mới
            blockProgress.put(loc, newProgress);
            String progressStr = newProgress + "/" + maxHealth;
            String barStr = plugin.getHoloProvider().createBar(newProgress, maxHealth);

            sendFeedback(player, null, null, progressStr, barStr, blockSec.getBoolean("action_bar", true));
            updateProgressHolo(loc, mmoId, config, progressStr, barStr);

            // Chạy lại timer refund (reset máu nếu người chơi ngừng đào)[cite: 2]
            startRefundTimer(loc, mmoId, config);
        }
    }

    private void handleVanillaDurabilityFallback(Player player, ConfigurationSection toolConfig, String actionType) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) return;

        ConfigurationSection actionSec = toolConfig.getConfigurationSection(actionType);
        int amount = (actionSec != null) ? actionSec.getInt("decreaseDurability", 1) : 1;

        if (hand.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta) {
            meta.setDamage(meta.getDamage() + amount);
            hand.setItemMeta(meta);

            // QUAN TRỌNG: Ghi đè lại item để Client thấy độ bền thay đổi và không bị kẹt animation[cite: 2]
            player.getInventory().setItemInMainHand(hand);

            if (meta.getDamage() >= hand.getType().getMaxDurability()) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            }
        }
    }
    // Hàm phụ để lấy ID từ Tag của Interaction
    private String getMMOIdFromInteraction(Interaction interaction) {
        return interaction.getScoreboardTags().stream()
                .filter(tag -> tag.startsWith("MMO_ID_"))
                .findFirst()
                .map(tag -> tag.replace("MMO_ID_", ""))
                .orElse(null);
    }

    // Hàm phụ để hủy task refund khi người chơi click liên tục
    private void cancelRefundTask(Location loc) {
        if (refundTasks.containsKey(loc)) {
            refundTasks.get(loc).cancel();
            refundTasks.remove(loc);
        }
    }

    // Hàm phụ xử lý việc Admin xóa block vĩnh viễn (Shift + Click)
    private void removeBlockPermanently(Player player, Location loc, Interaction interaction) {
        String world = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        String asUuid = plugin.getDatabase().getArmorStandUUID(world, x, y, z);
        if (asUuid != null) {
            Entity as = Bukkit.getEntity(UUID.fromString(asUuid));
            if (as != null) as.remove();
        }
        interaction.remove();
        plugin.getHoloManager().removeHolo(loc);
        loc.getBlock().setType(Material.AIR);
        plugin.getDatabase().removePlacedBlock(world, x, y, z);
        player.sendMessage("§c[MMOBlock] Đã xóa vĩnh viễn block và dữ liệu.");
    }
    /**
     * Hàm kiểm tra tất cả các điều kiện dựa trên PlaceholderAPI
     */
    private boolean checkConditions(Player player, FileConfiguration config, String mmoId) {
        ConfigurationSection conditions = config.getConfigurationSection(mmoId + ".conditions");
        if (conditions == null) return true;

        for (String key : conditions.getKeys(false)) {
            ConfigurationSection section = conditions.getConfigurationSection(key);
            if (section == null) continue;

            // Hook PAPI để lấy giá trị thực tế
            String first = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, section.getString("first", ""));
            String second = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, section.getString("second", ""));
            String operator = section.getString("operator", "==");

            if (!evaluate(first, second, operator)) {
                String title = section.getString("send-title", "");
                String sub = section.getString("send-subtitle", "");
                sendFeedback(player, title, sub, null, null, config.getBoolean(mmoId + ".action_bar", true));
                return false;
            }
        }
        return true;
    }

    /**
     * Hàm so sánh giá trị linh hoạt cho Condition
     */
    private boolean evaluate(String v1, String v2, String op) {
        try {
            double d1 = Double.parseDouble(v1);
            double d2 = Double.parseDouble(v2);
            return switch (op) {
                case ">" -> d1 > d2;
                case ">=" -> d1 >= d2;
                case "<" -> d1 < d2;
                case "<=" -> d1 <= d2;
                case "!=" -> d1 != d2;
                default -> d1 == d2;
            };
        } catch (NumberFormatException e) {
            // So sánh chuỗi nếu không phải là số
            return op.equals("!=") ? !v1.equalsIgnoreCase(v2) : v1.equalsIgnoreCase(v2);
        }
    }

    private void startRespawnLogic(Location baseLoc, String mmoId, FileConfiguration config, Interaction interaction) {
        // 1. CHUẨN BỊ TỌA ĐỘ VÀ THÔNG TIN CƠ BẢN
        Location loc = baseLoc.getBlock().getLocation();
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        // Lấy thông tin Group từ Database
        String groupId = plugin.getDatabase().getGroupIdAt(worldName, x, y, z);

        // --- BƯỚC 1: DỌN DẸP SẠCH SẼ QUẶNG CŨ ---
        plugin.getHoloManager().removeHolo(loc);
        interaction.setResponsive(false);
        interaction.addScoreboardTag("MMOBlock_Respawning");

        boolean breakBlock = config.getBoolean(mmoId + ".break-block", true);
        if (breakBlock) {
            loc.getBlock().setType(org.bukkit.Material.AIR);
            List<String> structIds = config.getStringList(mmoId + ".struct-ids");
            for (String sid : structIds) {
                if (!sid.isEmpty()) handleStructureBlocks(sid, true);
            }

            String asUuidStr = plugin.getDatabase().getArmorStandUUID(worldName, x, y, z);
            if (asUuidStr != null) {
                try {
                    org.bukkit.entity.Entity as = org.bukkit.Bukkit.getEntity(UUID.fromString(asUuidStr));
                    if (as != null) as.remove();
                } catch (Exception ignored) {}
            }
        }

        // --- BƯỚC 2: TÍNH TOÁN THỜI GIAN HỒI SINH (TIMESTAMP) ---
        int totalDelaySeconds = 0;
        if (groupId != null) {
            totalDelaySeconds = plugin.getGroupManager().getGroupDelay(groupId);
        } else {
            int deathDelay = config.getInt(mmoId + ".death_delay", 0);
            int respawnTime = config.getInt(mmoId + ".respawn", 4);
            totalDelaySeconds = deathDelay + respawnTime;
        }

        long respawnAt = System.currentTimeMillis() + (totalDelaySeconds * 1000L);
        plugin.getDatabase().updateRespawnTime(worldName, x, y, z, respawnAt);

        // --- BƯỚC 4: HIỂN THỊ HOLOGRAM ĐẾM NGƯỢC VÀ HỒI SINH TẠI CHỖ ---
        if (groupId == null && totalDelaySeconds > 0) {
            ConfigurationSection deathSection = config.getConfigurationSection(mmoId + ".hologram.deathHolo");
            if (deathSection != null) {
                final int[] timeLeft = { totalDelaySeconds };

                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        // 1. Nếu chunk bị unload, dừng task (Global Task sẽ lo phần còn lại khi chunk load)
                        if (!loc.getChunk().isLoaded()) {
                            plugin.getHoloManager().removeHolo(loc);
                            this.cancel();
                            return;
                        }

                        // 2. Khi đếm ngược kết thúc
                        if (timeLeft[0] <= 0) {
                            // Gọi hồi sinh ngay lập tức
                            handleInstantRespawn(loc, mmoId, config, interaction, null);

                            // QUAN TRỌNG: Reset thời gian trong DB về 0 để Global Task không hồi sinh đè lên lần nữa
                            plugin.getDatabase().updateRespawnTime(worldName, x, y, z, 0);

                            this.cancel();
                            return;
                        }

                        // 3. Hiển thị text đếm ngược
                        plugin.getHoloManager().spawnHolo(loc, deathSection, null, null, String.valueOf(timeLeft[0]));

                        timeLeft[0]--;
                    }
                }.runTaskTimer(plugin, 0L, 20L);
            }
        }
    }
    public void handleInstantRespawn(Location loc, String nextId, org.bukkit.configuration.file.FileConfiguration config, org.bukkit.entity.Interaction interaction, String groupId) {
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();

        // Lấy config của ID mới (nextId)
        org.bukkit.configuration.file.FileConfiguration nextConfig = plugin.getConfigManager().getConfig(nextId);
        if (nextConfig == null) nextConfig = config;

        // 1. Hồi phục Block vật lý theo cấu hình quặng mới (nextId)
        // 1. Hồi phục Block vật lý theo cấu hình quặng mới (nextId)
        ConfigurationSection nextBS = nextConfig.getConfigurationSection(nextId + ".block-settings");

// Mặc định là BARRIER để đảm bảo luôn có vật cản vật lý
        Material matToPlace = Material.BARRIER;

        if (nextBS != null && nextBS.getBoolean("enabled", true)) {
            String matStr = nextBS.getString("material", "BARRIER");
            Material foundMat = Material.matchMaterial(matStr.toUpperCase());
            if (foundMat != null) {
                matToPlace = foundMat;
            }
        }

// Luôn thực hiện đặt block, không để trong IF
        loc.getBlock().setType(matToPlace);
        // 2. Hồi phục Structure mới (nextId)
        java.util.List<String> nextStructs = nextConfig.getStringList(nextId + ".struct-ids");
        for (String sid : nextStructs) {
            if (!sid.isEmpty()) handleStructureBlocks(sid, false);
        }

        // 3. Cập nhật Hitbox & Offset theo quặng mới (nextId)
        org.bukkit.configuration.ConfigurationSection nextHB = nextConfig.getConfigurationSection(nextId + ".hitbox");
        double offX = 0, offY = 0, offZ = 0;
        if (nextHB != null) {
            interaction.setInteractionWidth((float) nextHB.getDouble("width", 1.0) + 0.01f);
            interaction.setInteractionHeight((float) nextHB.getDouble("height", 1.0) + 0.01f);
            offX = nextHB.getDouble("offset_x", 0.0);
            offY = nextHB.getDouble("offset_y", 0.0);
            offZ = nextHB.getDouble("offset_z", 0.0);
            interaction.teleport(loc.clone().add(0.5 + offX, 0.0 + offY, 0.5 + offZ));
        }

        // 4. Spawn Model mới (ItemsAdder) & Cập nhật Database
        java.util.UUID newAsUUID = null;
        boolean modelEnabled = nextConfig.getBoolean(nextId + ".block-itemsadder.enabled", false);
        if (modelEnabled) {
            newAsUUID = spawnModelAgain(loc, nextId, nextConfig);
        }

        // Lưu thông tin quặng mới vào Database (Ghi đè ID cũ bằng nextId và giữ nguyên groupId)
        plugin.getDatabase().savePlacedBlock(
                worldName, x, y, z,
                nextId, groupId,
                newAsUUID, interaction.getUniqueId(),
                offX, offY, offZ
        );

        // 5. Cập nhật Tag nhận diện quặng mới trên Hitbox (Dùng cho lần đào sau)
        interaction.getScoreboardTags().removeIf(tag -> tag.startsWith("MMO_ID_"));
        interaction.addScoreboardTag("MMO_ID_" + nextId);

        // 6. Hiển thị Hologram mặc định của quặng mới (customHolo)
        plugin.getHoloManager().removeHolo(loc);
        org.bukkit.configuration.ConfigurationSection custom = nextConfig.getConfigurationSection(nextId + ".hologram.customHolo");
        if (custom != null) {
            plugin.getHoloManager().spawnHolo(loc, custom, null, null, null);
        }

        // Mở khóa hitbox cho phép người chơi đào quặng mới
        interaction.setResponsive(true);
        interaction.getScoreboardTags().remove("MMOBlock_Respawning");
    }


    public UUID spawnModelAgain(Location loc, String id, FileConfiguration config) {
        ConfigurationSection modelSettings = config.getConfigurationSection(id + ".block-itemsadder");
        if (modelSettings == null || !modelSettings.getBoolean("enabled", false)) return null;

        String modelMatStr = modelSettings.getString("material", "PAPER");
        int modelId = modelSettings.getInt("model-id", 0);

        org.bukkit.entity.ArmorStand as = loc.getWorld().spawn(loc.clone().add(0.5, -0.2, 0.5), org.bukkit.entity.ArmorStand.class);

        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setBasePlate(false);
        as.addScoreboardTag("MMOBlock_Model");

        Material mMat = Material.matchMaterial(modelMatStr.toUpperCase());
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(mMat != null ? mMat : Material.PAPER);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelId);
            item.setItemMeta(meta);
        }
        as.getEquipment().setHelmet(item);

        return as.getUniqueId();
    }

    private void startRefundTimer(Location loc, String mmoId, FileConfiguration config) {
        int refundTime = config.getInt(mmoId + ".time-refund", 0);
        if (refundTime <= 0) return;

        // Debug (không dùng player nữa)


        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (blockProgress.containsKey(loc)) {
                    blockProgress.remove(loc);
                }
                refundTasks.remove(loc);

                ConfigurationSection customSection = config.getConfigurationSection(mmoId + ".hologram.customHolo");
                if (customSection != null) {
                    plugin.getHoloManager().spawnHolo(loc, customSection, null, null, null);
                }
            }
        }.runTaskLater(plugin, refundTime * 20L);

        refundTasks.put(loc, task);
    }

    private void sendFeedback(Player player, String title, String sub, String progress, String bar, boolean isActionBar) {
        // 1. Chỉ gửi Title/Subtitle nếu chúng thực sự có nội dung (không null)
        if (title != null || sub != null) {
            String finalTitle = plugin.getHoloManager().formatLegacy(title != null ? title : "", progress, bar, null);
            String finalSub = plugin.getHoloManager().formatLegacy(sub != null ? sub : "", progress, bar, null);
            player.sendTitle(finalTitle, finalSub, 0, 20, 5);
        }

        // 2. Gửi Action Bar cho tiến trình đào
        if (isActionBar && progress != null) {
            // Tùy biến format Action Bar hiển thị progress và thanh bar
            String actionBarMsg = plugin.getHoloManager().formatLegacy("&eTiến trình: %progress% &7[%progress_bar%&7]", progress, bar, null);
            player.sendActionBar(actionBarMsg);
        }
    }

    private void updateProgressHolo(Location loc, String mmoId, FileConfiguration config, String progressStr, String barStr) {
        ConfigurationSection section = config.getConfigurationSection(mmoId + ".hologram.progressHolo");
        if (section != null) plugin.getHoloManager().updateHoloText(loc, section, progressStr, barStr, null);
    }

    private boolean isInCooldown(UUID uuid, double seconds) {
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid) && now < cooldowns.get(uuid)) {
            return true; // Vẫn đang trong thời gian chờ
        }
        // Chỉ cập nhật cooldown MỚI khi đã hết cooldown cũ
        cooldowns.put(uuid, now + (long) (seconds * 1000));
        return false;
    }
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Interaction interaction) {
            if (interaction.getScoreboardTags().contains("MMOBlock_Hitbox")) {
                interaction.setSilent(true);
                event.setDamage(0);
            }
        }
    }
    private void handleStructureBlocks(String structId, boolean toAir) {

        if (structId == null || structId.isEmpty()) return;

        File folder = new File(plugin.getDataFolder(), "structures");
        File structFile = new File(folder, structId + ".yml");
        if (!structFile.exists()) return;

        FileConfiguration structConfig = YamlConfiguration.loadConfiguration(structFile);
        String worldName = structConfig.getString("info.world");
        if (worldName == null || worldName.isEmpty()) return;

        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[MMOBlock] World không tồn tại khi restore structure: " + worldName);
            return;
        }

        ConfigurationSection blocksSection = structConfig.getConfigurationSection("blocks");
        if (blocksSection == null) return;

        for (String key : blocksSection.getKeys(false)) {
            int absX = blocksSection.getInt(key + ".x");
            int absY = blocksSection.getInt(key + ".y");
            int absZ = blocksSection.getInt(key + ".z");

            Location target = new Location(world, absX, absY, absZ);

            if (toAir) {
                target.getBlock().setType(Material.AIR);
            } else {
                String matStr = blocksSection.getString(key + ".material", "STONE");
                Material mat = Material.matchMaterial(matStr.toUpperCase());
                if (mat == null) mat = Material.STONE;
                target.getBlock().setType(mat);
            }
        }
    }
}