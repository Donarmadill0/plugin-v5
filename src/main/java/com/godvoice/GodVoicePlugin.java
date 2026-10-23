package com.godvoice;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.UUID;

public final class GodVoicePlugin extends JavaPlugin implements Listener {
    private static final String HIDDEN_TEAM = "godvoice_hidden";

    private GodData data;
    private DialogueManager dialogue;
    private GodModel model;
    private VoiceBridge voice; // null when Simple Voice Chat isn't installed
    private int tick;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        data = new GodData(this);
        data.load();
        dialogue = new DialogueManager(this);
        model = new GodModel(this);

        GodCommand cmd = new GodCommand(this);
        for (String name : new String[]{"god", "vow"}) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(cmd);
                getCommand(name).setTabCompleter(cmd);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, this::onTick, 1L, 1L);

        if (getServer().getPluginManager().getPlugin("voicechat") != null) {
            try {
                voice = VoiceLink.register(this);
                getLogger().info("Simple Voice Chat found: /god voice is enabled.");
            } catch (Throwable t) {
                getLogger().warning("Could not hook Simple Voice Chat: " + t);
            }
        } else {
            getLogger().info("Simple Voice Chat not found: /god voice is disabled.");
        }
        getLogger().info("GodVoice enabled. Use /god for help.");
    }

    @Override
    public void onDisable() {
        for (UUID id : model.wearingIds()) {
            Player p = Bukkit.getPlayer(id);
            model.unwear(id);
            if (p != null) setVanished(p, false);
        }
        dialogue.clearAll();
        data.save();
    }

    public GodData data() {
        return data;
    }

    public DialogueManager dialogue() {
        return dialogue;
    }

    public GodModel model() {
        return model;
    }

    public VoiceBridge voice() {
        return voice;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dialogue.clear(e.getPlayer().getUniqueId());
        if (model.isWearing(e.getPlayer().getUniqueId())) {
            model.unwear(e.getPlayer().getUniqueId());
            setVanished(e.getPlayer(), false);
        }
        if (voice != null) voice.unlink(e.getPlayer().getUniqueId());
    }

    private void onTick() {
        int t = tick++;
        model.tickFollow();
        model.tickParticles(t);
        for (Map.Entry<UUID, GodData.God> e : data.gods.entrySet()) {
            Player god = Bukkit.getPlayer(e.getKey());
            if (god == null) continue;
            if (t % 2 == 0 && data.vanished.contains(e.getKey()) && !model.isWearing(e.getKey())) Aura.spawn(god, e.getValue().color, t);
            if (t % 80 == 0 && getConfig().getBoolean("blessing.enabled", true)) {
                for (UUID f : e.getValue().followers.keySet()) {
                    Player p = Bukkit.getPlayer(f);
                    if (p != null) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 0, true, false, true));
                    }
                }
            }
        }
    }

    /** Vanish = invisible + hidden nametag + particle aura. */
    public void setVanished(Player p, boolean on) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam(HIDDEN_TEAM);
        if (team == null) {
            team = sb.registerNewTeam(HIDDEN_TEAM);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        if (on) {
            data.vanished.add(p.getUniqueId());
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            team.addEntry(p.getName());
        } else {
            data.vanished.remove(p.getUniqueId());
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            team.removeEntry(p.getName());
        }
        data.save();
    }
}
