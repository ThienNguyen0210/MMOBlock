package org.RiverMoon.commands.function;

import org.RiverMoon.Main;
import org.RiverMoon.manager.LanguageManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StructCommand implements SubCommand {
    private final Main plugin;
    private final LanguageManager lang;

    public StructCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @Override
    public String getName() { return "struct"; }

    @Override
    public String getDescription() { return lang.getMessage("commands.struct.desc"); }

    @Override
    public String getSyntax() { return "/mmoblock struct <edit|save|remove|undo> [id] [shape] [size]"; }

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

        String action = args[1].toLowerCase();
        UUID uuid = player.getUniqueId();

        switch (action) {
            case "edit" -> {
                String shape = "single";
                int size = 1;

                // /mmoblock struct edit [shape] [size]
                if (args.length >= 3) {
                    shape = args[2].toLowerCase();
                }
                if (args.length >= 4) {
                    try {
                        size = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cĐộ lớn (size) phải là một con số nguyên.");
                        return;
                    }
                }

                // Cập nhật công cụ và bật/tắt mode
                plugin.getStructureManager().setPlayerEditTool(uuid, shape, size);
                plugin.getStructureManager().toggleEditMode(uuid);

                if (plugin.getStructureManager().isEditing(uuid)) {
                    player.sendMessage("§e[MMOBlock] §aChế độ chỉnh sửa: §f" + shape.toUpperCase() + " §7(Size: " + size + ")");
                    player.sendMessage("§7- Chuột trái: Chọn block (Biến thành AIR)");
                    player.sendMessage("§7- Shift + Chuột phải: Hoàn tác (Undo tối đa 10 lần)");
                }
            }
            case "undo" -> {
                // Thêm lệnh /mmoblock struct undo để người dùng có thêm lựa chọn ngoài phím tắt
                if (!plugin.getStructureManager().isEditing(uuid)) {
                    player.sendMessage("§cBạn phải bật chế độ Edit trước khi Undo.");
                    return;
                }
                plugin.getStructureManager().undoLastAction(uuid);
            }
            case "save" -> {
                if (args.length < 3) {
                    player.sendMessage("§cThiếu ID cấu trúc. Cú pháp: /mmoblock struct save <id>");
                    return;
                }
                saveStructure(player, args[2]);
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage("§cThiếu ID cấu trúc. Cú pháp: /mmoblock struct remove <id>");
                    return;
                }
                removeStructure(player, args[2]);
            }
            default -> player.sendMessage(lang.getMessage("general.invalid-action"));
        }
    }

    private void saveStructure(Player player, String id) {
        UUID uuid = player.getUniqueId();
        // Lấy danh sách block đã tổng hợp từ StructureManager
        Map<Location, Material> blocks = plugin.getStructureManager().getSavedBlocks(uuid);

        if (blocks == null || blocks.isEmpty()) {
            player.sendMessage("§cKhông có block nào được chọn để lưu!");
            return;
        }

        File folder = new File(plugin.getDataFolder(), "structures");
        if (!folder.exists()) folder.mkdirs();
        File file = new File(folder, id + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        // Lấy tên world từ block đầu tiên
        String worldName = blocks.keySet().iterator().next().getWorld().getName();
        config.set("info.world", worldName);

        int count = 0;
        for (Map.Entry<Location, Material> entry : blocks.entrySet()) {
            Location loc = entry.getKey();
            Material originalMat = entry.getValue();

            String path = "blocks.b" + count;
            config.set(path + ".material", originalMat.name());
            config.set(path + ".x", loc.getBlockX());
            config.set(path + ".y", loc.getBlockY());
            config.set(path + ".z", loc.getBlockZ());

            String mmoId = plugin.getDatabase().getMMOIdAt(loc);
            if (mmoId != null) config.set(path + ".mmo_id", mmoId);

            count++;
        }

        try {
            config.set("info.total_blocks", count);
            config.save(file);

            player.sendMessage("§e[MMOBlock] §aĐã lưu cấu trúc §f" + id + " §avới §f" + count + " §ablocks.");

            // --- ĐOẠN SỬA QUAN TRỌNG: Đặt lại block vật lý trước khi dọn dẹp bộ nhớ ---
            blocks.forEach((loc, mat) -> {
                // Nếu vị trí đó đang là AIR hoặc CAVE_AIR thì mới đặt lại block gốc
                if (loc.getBlock().getType() == Material.AIR || loc.getBlock().getType() == Material.CAVE_AIR) {
                    loc.getBlock().setType(mat);
                }
            });
            // -----------------------------------------------------------------------

            // Sau khi đã fill lại block rồi mới tắt Edit Mode
            plugin.getStructureManager().toggleEditMode(uuid);

        } catch (IOException e) {
            player.sendMessage("§cCó lỗi xảy ra khi lưu tệp tin .yml!");
            e.printStackTrace();
        }
    }

    private void removeStructure(Player player, String id) {
        File folder = new File(plugin.getDataFolder(), "structures");
        File file = new File(folder, id + ".yml");

        if (!file.exists()) {
            player.sendMessage("§cKhông tìm thấy cấu trúc có ID: " + id);
            return;
        }

        if (file.delete()) {
            player.sendMessage("§e[MMOBlock] §aĐã xóa thành công cấu trúc: §f" + id);
        } else {
            player.sendMessage("§cKhông thể xóa tệp tin cấu trúc.");
        }
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 2) return List.of("edit", "save", "remove", "undo");

        if (args.length == 3) {
            if (args[1].equalsIgnoreCase("edit")) return List.of("single", "sphere", "solid");
            if (args[1].equalsIgnoreCase("save") || args[1].equalsIgnoreCase("remove")) return List.of("<id>");
        }

        if (args.length == 4 && args[1].equalsIgnoreCase("edit")) {
            return List.of("1", "3", "5");
        }

        return new ArrayList<>();
    }
}