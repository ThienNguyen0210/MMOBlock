package org.RiverMoon.commands.function;

import org.RiverMoon.Main;
import org.RiverMoon.manager.LanguageManager;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;

public class ReloadCommand implements SubCommand {
    private final Main plugin;
    private final LanguageManager lang;

    public ReloadCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @Override
    public String getName() { return "reload"; }

    @Override
    public String getDescription() { return lang.getMessage("commands.reload.desc"); }

    @Override
    public String getSyntax() { return "/mmoblock reload"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        long startTime = System.currentTimeMillis();

        plugin.reloadConfig();

        if (plugin.getLang() != null) {
            plugin.getLang().reloadLanguage();
        }

        if (plugin.getGroupManager() != null) {
            plugin.getGroupManager().loadGroups();
        }

        if (plugin.getHoloManager() != null) {
            plugin.getHoloManager().removeAll();
        }

        if (plugin.getConfigManager() != null) {
            plugin.getConfigManager().reloadAll();
        }

        plugin.loadAllHolograms();

        long duration = System.currentTimeMillis() - startTime;

        sender.sendMessage(lang.getMessage("reload.success")
                .replace("%ms%", String.valueOf(duration)));
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}