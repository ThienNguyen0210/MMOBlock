package org.RiverMoon;

import org.RiverMoon.commands.MMOBlockCommand;
import org.RiverMoon.config.ConfigManager;
import org.RiverMoon.database.Database;
import org.RiverMoon.hologram.HologramManager;
import org.RiverMoon.hologram.HologramProvider;
import org.RiverMoon.listeners.BlockListener;
import java.util.List;
import org.RiverMoon.listeners.MiningListener;
import org.RiverMoon.manager.ItemDataManager;
import org.RiverMoon.manager.LanguageManager;
import org.RiverMoon.manager.GroupManager;
import org.RiverMoon.manager.StructureManager;
import org.bukkit.Location;
import org.RiverMoon.database.PlacedBlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Interaction;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.UUID;

public class Main extends JavaPlugin {
    private ItemDataManager itemDataManager;
    private StructureManager structureManager;
    private ConfigManager configManager;
    private MiningListener miningListener;
    private Database database;
    private HologramManager hologramManager;
    private HologramProvider hologramProvider;
    private LanguageManager languageManager;
    private GroupManager groupManager;
    @Override
    public void onEnable() {
        this.groupManager = new GroupManager(this); // Khởi tạo Manager
        this.groupManager.loadGroups();
        this.itemDataManager = new ItemDataManager(this);
        // --- BƯỚC MỚI: TẠO CÁC FILE MẪU NẾU CHƯA CÓ ---
        this.structureManager = new StructureManager(this);
        this.miningListener = new MiningListener(this);
        getServer().getPluginManager().registerEvents(this.miningListener, this);
        startGlobalRespawnTask();
        new File(getDataFolder(), "structures").mkdirs();
        saveDefaultBlocks();
        this.languageManager = new LanguageManager(this);
        saveDefaultConfig();
        // 1. Khởi tạo Database và Config
        this.database = new Database(this);
        this.database.connect();

        this.configManager = new ConfigManager(this);
        configManager.reloadAll();
        getServer().getScheduler().runTaskLater(this, this::loadAllHolograms, 200L); // Delay 2 giây
        // 2. Khởi tạo Hệ thống Hologram
        this.hologramManager = new HologramManager();
        this.hologramProvider = new HologramProvider(this);
        // 3. Đăng ký Command
        MMOBlockCommand mainCommand = new MMOBlockCommand(this);
        getCommand("mmoblock").setExecutor(mainCommand);
        getCommand("mmoblock").setTabCompleter(mainCommand);

        // 4. Load dữ liệu từ DB
        loadAllHolograms();

        // 5. Đăng ký Events
        getServer().getPluginManager().registerEvents(new BlockListener(this), this);
        getServer().getPluginManager().registerEvents(new MiningListener(this), this);
        getServer().getScheduler().runTaskLater(this, () -> {
            loadAllHolograms();
        }, 60L); // 200 ticks = 10 giây (1 giây = 20 ticks)
    }

    /**
     * Tự động tạo thư mục blocks và copy file mẫu từ Resource nếu chưa tồn tại
     */
    private void saveDefaultBlocks() {
        File blockFolder = new File(getDataFolder(), "blocks");

        if (!blockFolder.exists()) {
            blockFolder.mkdirs();
        }

        File exampleFile = new File(blockFolder, "example.yml");
        if (!exampleFile.exists()) {
            saveResource("blocks/example.yml", false);
            getLogger().info("§a[MMOBlock] Đã tạo file blocks/example.yml mẫu!");
        }
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.disconnect();
        }
        if (hologramManager != null) {
            hologramManager.removeAll();
        }
        getLogger().info("§c[MMOBlock] Stop!");
    }
    public void loadAllHolograms() {
        int updatedCount = 0;
        // Lấy toàn bộ dữ liệu blocks từ Database
        List<PlacedBlockData> allBlocks = database.getAllPlacedBlocks();

        if (allBlocks == null || allBlocks.isEmpty()) return;

        long currentTime = System.currentTimeMillis();

        for (PlacedBlockData data : allBlocks) {
            Location loc = data.getLocation();
            if (loc == null || loc.getWorld() == null) continue;

            // 1. KIỂM TRA TRẠNG THÁI HỒI SINH TỪ DATABASE
            // Nếu quặng đang trong thời gian chờ hồi sinh (respawn_at > 0),
            // chúng ta bỏ qua không nạp lại model/hitbox để tránh lỗi.
            // Lưu ý: Bạn cần thêm field respawnAt vào PlacedBlockData hoặc check trực tiếp qua DB.
            if (database.isWaitingForRespawn(data.getWorldName(), data.getX(), data.getY(), data.getZ(), currentTime)) {
                continue;
            }

            if (!loc.getChunk().isLoaded()) {
                loc.getChunk().load();
            }

            String id = data.getMmoId();
            String groupId = data.getGroupId();

            FileConfiguration config = configManager.getConfig(id);
            if (config == null) continue;

            // 2. DỌN DẸP THỰC THỂ CŨ TRƯỚC KHI TẠO MỚI
            loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), 1.5, 1.5, 1.5).forEach(entity -> {
                if (entity.getScoreboardTags().contains("MMOBlock_Hitbox") ||
                        entity.getScoreboardTags().contains("MMOBlock_Holo") ||
                        entity.getScoreboardTags().contains("MMOBlock_Model")) {
                    entity.remove();
                }
            });
            hologramManager.removeHolo(loc);

            // 3. THIẾT LẬP BLOCK VẬT LÝ
            ConfigurationSection blockSettings = config.getConfigurationSection(id + ".block-settings");
            ConfigurationSection modelSettings = config.getConfigurationSection(id + ".block-itemsadder");

            boolean blockEnabled = blockSettings != null && blockSettings.getBoolean("enabled", true);
            boolean modelEnabled = modelSettings != null && modelSettings.getBoolean("enabled", false);

            org.bukkit.Material matToSet = org.bukkit.Material.STONE;

            if (blockEnabled) {
                String matStr = blockSettings.getString("material", "STONE");
                matToSet = org.bukkit.Material.matchMaterial(matStr.toUpperCase());
                if (matToSet == null) matToSet = org.bukkit.Material.STONE;
            } else if (modelEnabled) {
                matToSet = org.bukkit.Material.BARRIER; // Bắt buộc dùng vật cản nếu dùng model
            }

            if (loc.getBlock().getType() != matToSet) {
                loc.getBlock().setType(matToSet, false);
            }

            // 4. HỒI PHỤC MODEL (ARMORSTAND)
            UUID newAsUUID = null;
            if (modelEnabled) {
                String modelMatStr = modelSettings.getString("material", "PAPER");
                int modelId = modelSettings.getInt("model-id", 0);

                Location asLoc = loc.clone().add(0.5, -0.2, 0.5);
                org.bukkit.entity.ArmorStand as = asLoc.getWorld().spawn(asLoc, org.bukkit.entity.ArmorStand.class, armorStand -> {
                    armorStand.setVisible(false);
                    armorStand.setGravity(false);
                    armorStand.setMarker(true);
                    armorStand.setBasePlate(false);
                    armorStand.setCanPickupItems(false);
                    armorStand.addScoreboardTag("MMOBlock_Model");

                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(modelMatStr.toUpperCase());
                    org.bukkit.inventory.ItemStack helmet = new org.bukkit.inventory.ItemStack(mat != null ? mat : org.bukkit.Material.PAPER);
                    org.bukkit.inventory.meta.ItemMeta meta = helmet.getItemMeta();
                    if (meta != null) {
                        meta.setCustomModelData(modelId);
                        helmet.setItemMeta(meta);
                    }
                    if (armorStand.getEquipment() != null) armorStand.getEquipment().setHelmet(helmet);
                });
                newAsUUID = as.getUniqueId();
            }

            // 5. HỒI PHỤC HITBOX (INTERACTION)
            UUID newHitboxUUID = null;
            double offX = 0, offY = 0, offZ = 0;

            ConfigurationSection hitboxSection = config.getConfigurationSection(id + ".hitbox");
            if (hitboxSection != null) {
                float width = (float) hitboxSection.getDouble("width", 1.0) + 0.01f;
                float height = (float) hitboxSection.getDouble("height", 1.0) + 0.01f;

                offX = hitboxSection.getDouble("offset_x", 0.0);
                offY = hitboxSection.getDouble("offset_y", 0.0);
                offZ = hitboxSection.getDouble("offset_z", 0.0);

                Location hbLoc = loc.clone().add(0.5 + offX, 0.0 + offY, 0.5 + offZ);

                Interaction inter = loc.getWorld().spawn(hbLoc, Interaction.class, hitbox -> {
                    hitbox.setInteractionWidth(width);
                    hitbox.setInteractionHeight(height);
                    hitbox.setResponsive(true);
                    hitbox.setSilent(true);
                    hitbox.addScoreboardTag("MMOBlock_Hitbox");
                    hitbox.addScoreboardTag("MMO_ID_" + id);
                });
                newHitboxUUID = inter.getUniqueId();
            }

            // 6. HỒI PHỤC HOLOGRAM
            ConfigurationSection holoSection = config.getConfigurationSection(id + ".hologram.customHolo");
            if (holoSection != null) {
                hologramManager.spawnHolo(loc, holoSection, null, null, null);
            }

            // 7. ĐỒNG BỘ LẠI DATABASE
            database.savePlacedBlock(
                    loc.getWorld().getName(),
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ(),
                    id,
                    groupId,
                    newAsUUID,
                    newHitboxUUID,
                    offX,
                    offY,
                    offZ
            );
            updatedCount++;
        }

        if (updatedCount > 0) {
            getLogger().info("§e[MMOBlock] Đã hồi phục thành công " + updatedCount + " quặng đang hoạt động.");
        }
    }
    public void startGlobalRespawnTask() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            // Lấy danh sách quặng đã quá giờ hồi sinh từ DB
            List<PlacedBlockData> readyBlocks = database.getBlocksReadyToRespawn(now);

            for (PlacedBlockData data : readyBlocks) {
                Location loc = data.getLocation();
                if (loc == null || loc.getWorld() == null) continue;

                // KIỂM TRA CHUNK: Chỉ hồi sinh nếu Chunk đang nạp (Tránh lag/lỗi unload)
                if (loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {

                    // Lấy thực thể Interaction cũ dựa trên UUID lưu trong Database
                    org.bukkit.entity.Entity entity = org.bukkit.Bukkit.getEntity(data.getHitboxUuid());

                    if (entity instanceof Interaction interaction) {
                        String nextId = data.getMmoId();

                        // Nếu là Group, hãy chọn một ID ngẫu nhiên mới
                        if (data.getGroupId() != null) {
                            nextId = groupManager.getRandomId(data.getGroupId());
                        }

                        FileConfiguration config = configManager.getConfig(nextId);

                        // Gọi hàm hồi sinh chính (Bạn cần chắc chắn hàm này trong MiningListener là public)
                        miningListener.handleInstantRespawn(loc, nextId, config, interaction, data.getGroupId());

                        // QUAN TRỌNG: Đánh dấu đã hồi sinh xong bằng cách set respawn_at = 0
                        database.updateRespawnTime(data.getWorldName(), data.getX(), data.getY(), data.getZ(), 0);
                    } else {
                        // Nếu không tìm thấy Interaction (bị xóa do bug/clear entity),
                        // bạn có thể thêm logic spawn lại Hitbox mới ở đây nếu cần.
                        getLogger().warning("Không tìm thấy Hitbox cho quặng tại " + data.getX() + ", " + data.getZ() + ". Bỏ qua hồi sinh.");
                        database.updateRespawnTime(data.getWorldName(), data.getX(), data.getY(), data.getZ(), 0);
                    }
                }
            }
        }, 40L, 40L); // Chạy sau 2 giây và lặp lại mỗi 2 giây (40 ticks)
    }
    // Getters
    public GroupManager getGroupManager() {
        return groupManager;
    }
    public ItemDataManager getItemDataManager() {
        return itemDataManager;
    }
    public LanguageManager getLang() {
        return languageManager;
    }
    public StructureManager getStructureManager() { return structureManager; }
    public ConfigManager getConfigManager() { return configManager; }
    public Database getDatabase() { return database; }
    public HologramManager getHoloManager() { return hologramManager; }
    public HologramProvider getHoloProvider() { return hologramProvider; }
}