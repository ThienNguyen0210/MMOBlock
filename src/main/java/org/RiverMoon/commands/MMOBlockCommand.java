package org.RiverMoon.commands;

import org.RiverMoon.Main;
import org.RiverMoon.commands.function.*; 
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MMOBlockCommand implements CommandExecutor, TabCompleter {
    private final ArrayList<SubCommand> subCommands = new ArrayList<>();

    public MMOBlockCommand(Main plugin) {
        
        subCommands.add(new ReloadCommand(plugin));
        subCommands.add(new CreateCommand(plugin));
        subCommands.add(new PlaceCommand(plugin));
        subCommands.add(new StructCommand(plugin));
        subCommands.add(new ListCommand(plugin));
        subCommands.add(new SaveItemCommand(plugin));
        subCommands.add(new PlaceGroupCommand(plugin));

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0) {
            for (SubCommand sub : subCommands) {
                if (args[0].equalsIgnoreCase(sub.getName())) {
                    sub.perform(sender, args);
                    return true;
                }
            }
        }

        sender.sendMessage("§8--- §6§lMMOBlock Admin §8---");
        for (SubCommand sub : subCommands) {
            sender.sendMessage("§e" + sub.getSyntax() + " §7- " + sub.getDescription());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (SubCommand sub : subCommands) list.add(sub.getName());
            return list;
        } else if (args.length > 1) {
            for (SubCommand sub : subCommands) {
                if (args[0].equalsIgnoreCase(sub.getName())) {
                    return sub.getSubcommandArguments(sender, args);
                }
            }
        }
        return new ArrayList<>();
    }
}