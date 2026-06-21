package org.RiverMoon.commands.function;

import org.RiverMoon.Main;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class SaveItemCommand implements SubCommand {
    private final Main plugin;

    public SaveItemCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "saveitem";
    }

    @Override
    public String getDescription() {
        return "Lưu vật phẩm đang cầm trên tay vào database Base64";
    }

    @Override
    public String getSyntax() {
        return "/mmoblock saveitem <id>";
    }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cChỉ người chơi mới có thể dùng lệnh này.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c Cách dùng: " + getSyntax());
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§cBạn phải cầm vật phẩm trên tay để lưu!");
            return;
        }

        String id = args[1];
        plugin.getItemDataManager().saveItem(id, item);
        player.sendMessage("§e[MMOBlock] §aĐã lưu vật phẩm thành công với ID: §f" + id);
    }

    
    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return List.of("<id_vật_phẩm>");
        }
        return new ArrayList<>();
    }
}