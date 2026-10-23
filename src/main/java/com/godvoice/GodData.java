package com.godvoice;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Gods, followers and vanish state, saved to plugins/GodVoice/gods.yml. */
public final class GodData {
    public static final class God {
        public String title = "GOD";
        public int color = 0x9B30FF;
        public final Map<UUID, String> followers = new LinkedHashMap<>();
    }

    private final GodVoicePlugin plugin;
    public final Map<UUID, God> gods = new HashMap<>();
    public final Set<UUID> vanished = new HashSet<>();

    GodData(GodVoicePlugin plugin) {
        this.plugin = plugin;
    }

    private File file() {
        return new File(plugin.getDataFolder(), "gods.yml");
    }

    public void load() {
        gods.clear();
        vanished.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection gs = y.getConfigurationSection("gods");
        if (gs != null) {
            for (String key : gs.getKeys(false)) {
                try {
                    ConfigurationSection s = gs.getConfigurationSection(key);
                    if (s == null) continue;
                    God g = new God();
                    g.title = s.getString("title", "GOD");
                    g.color = s.getInt("color", 0x9B30FF);
                    ConfigurationSection fs = s.getConfigurationSection("followers");
                    if (fs != null) {
                        for (String fk : fs.getKeys(false)) g.followers.put(UUID.fromString(fk), fs.getString(fk, "?"));
                    }
                    gods.put(UUID.fromString(key), g);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        for (String v : y.getStringList("vanished")) {
            try {
                vanished.add(UUID.fromString(v));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, God> e : gods.entrySet()) {
            String base = "gods." + e.getKey();
            y.set(base + ".title", e.getValue().title);
            y.set(base + ".color", e.getValue().color);
            for (Map.Entry<UUID, String> f : e.getValue().followers.entrySet()) {
                y.set(base + ".followers." + f.getKey(), f.getValue());
            }
        }
        List<String> v = new ArrayList<>();
        for (UUID u : vanished) v.add(u.toString());
        y.set("vanished", v);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save gods.yml: " + ex.getMessage());
        }
    }
}
