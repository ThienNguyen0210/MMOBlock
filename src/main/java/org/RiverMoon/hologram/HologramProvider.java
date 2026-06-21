package org.RiverMoon.hologram;

import org.RiverMoon.Main;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HologramProvider {
    private final Main plugin;

    public HologramProvider(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Tạo thanh Progress Bar động dựa trên config.yml
     * Thay vì fix cứng xanh/đỏ, nó sẽ đọc 'progressing', 'noprogress' và 'barlength'
     */
    public String createBar(double currentHealth, double maxHealth) {
        FileConfiguration config = plugin.getConfig();

        String progressing = config.getString("progressbar.progressing", "&a|");
        String noprogress = config.getString("progressbar.noprogress", "<gray>|");
        int totalBars = config.getInt("progressbar.barlength", 16);

        if (maxHealth <= 0) return noprogress.repeat(totalBars);

        double ratio = (maxHealth - currentHealth) / maxHealth;
        int completed = (int) (ratio * totalBars);

        completed = Math.min(totalBars, Math.max(0, completed));

        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < completed; i++) {
            bar.append(progressing);
        }

        for (int i = 0; i < (totalBars - completed); i++) {
            bar.append(noprogress);
        }

        return bar.toString();
    }

    /**
     * Tính toán chuỗi phần trăm (Ví dụ: "75%")
     */
    public String calculatePercent(double currentHealth, double maxHealth) {
        if (maxHealth <= 0) return "0%";
        double percent = ((maxHealth - currentHealth) / maxHealth) * 100;
        return (int) percent + "%";
    }

    /**
     * Lấy danh sách các dòng text đã được xử lý Placeholder.
     */
    public List<String> getProcessedLines(ConfigurationSection section, String progress, String progressBar, String respawnTime) {
        List<String> lines = new ArrayList<>();
        if (section == null) return lines;

        List<String> keys = new ArrayList<>(section.getKeys(false));
        // Sắp xếp thứ tự 1, 2, 3...
        try {
            keys.sort((a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));
        } catch (NumberFormatException e) {
            Collections.sort(keys);
        }

        for (String key : keys) {
            String val = section.getString(key + ".value");
            if (val == null) continue;

            // Xử lý placeholder
            if (progress != null) val = val.replace("%progress%", progress);
            if (progressBar != null) val = val.replace("%progress_bar%", progressBar);
            if (respawnTime != null) val = val.replace("%respawnTime%", respawnTime);

            lines.add(val);
        }
        return lines;
    }
}