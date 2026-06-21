package org.RiverMoon.commands.function;

import org.RiverMoon.Main;
import org.RiverMoon.manager.LanguageManager;
import org.bukkit.command.CommandSender;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CreateCommand implements SubCommand {
    private final Main plugin;
    private final LanguageManager lang;

    public CreateCommand(Main plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLang();
    }

    @Override
    public String getName() { return "create"; }

    @Override
    public String getDescription() { return lang.getMessage("commands.create.desc"); }

    @Override
    public String getSyntax() { return "/mmoblock create <id>"; }

    @Override
    public void perform(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(lang.getMessage("general.usage").replace("%syntax%", getSyntax()));
            return;
        }

        String id = args[1];
        File folder = new File(plugin.getDataFolder(), "blocks");
        if (!folder.exists()) folder.mkdirs();

        File file = new File(folder, id + ".yml");
        if (file.exists()) {
            sender.sendMessage(lang.getMessage("create.already-exists").replace("%id%", id));
            return;
        }

        try {
            String template = getYamlTemplate(id);
            Files.writeString(file.toPath(), template);

            sender.sendMessage(lang.getMessage("create.success").replace("%id%", id));

            plugin.getConfigManager().reloadAll();

        } catch (IOException e) {
            sender.sendMessage(lang.getMessage("general.error-save") + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getYamlTemplate(String id) {
        return "#config file version 2.8-latest\n" +
                id + ":\n" +
                "  send-title: \"&eYour tool not matching! %progress%\"\n" +
                "  send-subtitle: \"<red>or Tools are too low</red>\"\n" +
                "  click_cooldown: 0.5\n" +
                "  action_bar: false\n" +
                "  time-refund: 5\n" +
                "  respawn: 4\n" +
                "  break-block: false\n" +
                "  health-block: 5\n" +
                "  death_delay: 1\n" +
                "  hitbox:\n" +
                "    width: 1\n" +
                "    length: 1\n" +
                "    height: 1\n" +
                "  block-settings:\n" +
                "    enabled: true\n" +
                "    material: \"DRAGON_EGG\"\n" +
                "  block-itemsadder:\n" +
                "    enabled: false\n" +
                "    model-id: 10043\n" +
                "    material: paper\n" +
                "  hologram:\n" +
                "    enable: true\n" +
                "    customHolo:\n" +
                "      '1':\n" +
                "        value: <color:#35FF0A>Tutorial Mining</color>\n" +
                "        height: 0.0\n" +
                "      '2':\n" +
                "        value: <gradient:#FFFFFF:#FFFFFF>No requirement</gradient>\n" +
                "        height: 0.2\n" +
                "      '3':\n" +
                "        value: <color:#AAAAAA>Left-Click to mine</color>\n" +
                "        height: 0.2\n" +
                "      '4':\n" +
                "        value: item\n" +
                "        id: WOODEN_PICKAXE\n" +
                "        custom_model_data: 0\n" +
                "        height: 1.5\n" +
                "    progressHolo:\n" +
                "      \"1\":\n" +
                "        value: <color:#AAAAAA>Left-Click to receive &7CobbleStone</color>\n" +
                "        height: 0.4\n" +
                "      \"2\":\n" +
                "        value: <color:#AAAAAA>Right-Click to receive &7Cobble Fragment</color>\n" +
                "        height: 0.2\n" +
                "      \"3\":\n" +
                "        value: \"&7[&f%progress_bar%&7]\"\n" +
                "        height: 0.4\n" +
                "    deathHolo:\n" +
                "      \"1\":\n" +
                "        value: <color:#FFF800>Respawning</color>\n" +
                "        height: 0.4\n" +
                "      \"2\":\n" +
                "        value: \"<gradient:#FFA500:#F92986>Respawning in: %respawnTime%s</gradient>\"\n" +
                "        height: 0.4\n" +
                "    height: 1.5\n" +
                "    clickHeight: 1.5\n" +
                "    respawnHeigth: 1.5\n" +
                "    shadowed: true\n" +
                "    background: false\n" +
                "  sounds:\n" +
                "    onClick: block.stone.break\n" +
                "    onDeath: block.stone.break\n" +
                "  drop-options:\n" +
                "    Lootsplosion: true\n" +
                "    ItemGlow:\n" +
                "      enable: false\n" +
                "  allowed_tools:\n" +
                "    \"1\":\n" +
                "      material: WOODEN_PICKAXE\n" +
                "      left_click:\n" +
                "        decreaseDurability: 10\n" +
                "        clickNeeded: 3\n" +
                "      allowedDrops:\n" +
                "        - example_drop\n" +
                "  drops:\n" +
                "    example_drop:\n" +
                "      item:\n" +
                "        material: COBBLESTONE\n" +
                "        total: [1-3]\n" +
                "        chances: 1.0\n" +
                "        drop_type: center_ground\n" +
                "      target: left_click";
    }

    @Override
    public List<String> getSubcommandArguments(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}