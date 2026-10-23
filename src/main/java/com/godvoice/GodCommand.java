package com.godvoice;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

final class GodCommand implements CommandExecutor, TabCompleter {
    private static final List<String> GOD_SUBS =
            List.of("grant", "revoke", "speak", "broadcast", "vanish", "voice", "model", "followers", "release");

    private final GodVoicePlugin plugin;

    GodCommand(GodVoicePlugin plugin) {
        this.plugin = plugin;
    }

    private static void info(CommandSender s, String m) {
        s.sendMessage(Component.text(m, NamedTextColor.GRAY));
    }

    private static void err(CommandSender s, String m) {
        s.sendMessage(Component.text(m, NamedTextColor.RED));
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("vow")) vow(s, a);
        else god(s, a);
        return true;
    }

    // ---------------- /god ----------------

    private void god(CommandSender s, String[] a) {
        if (a.length == 0) {
            help(s);
            return;
        }
        switch (a[0].toLowerCase()) {
            case "grant" -> grant(s, a);
            case "revoke" -> revoke(s, a);
            case "speak" -> speak(s, a);
            case "broadcast" -> broadcast(s, a);
            case "vanish" -> vanish(s);
            case "voice" -> voice(s, a);
            case "model" -> model(s, a);
            case "followers" -> followers(s);
            case "release" -> release(s, a);
            default -> help(s);
        }
    }

    private void help(CommandSender s) {
        info(s, "God commands:");
        info(s, "/god grant <player> <#hex> <title...>   (op)");
        info(s, "/god revoke <player>   (op)");
        info(s, "/god speak <player> <message...>");
        info(s, "/god broadcast <message...>");
        info(s, "/god vanish");
        info(s, "/god voice <player|followers|off>");
        info(s, "/god model   (become / stop being your god form)");
        info(s, "/god model statue | clear");
        info(s, "/god followers   |   /god release <player>");
        info(s, "Players: /vow swear <god>, /vow break <god>, /vow list");
    }

    private GodData.God godOf(CommandSender s) {
        if (!(s instanceof Player p)) {
            err(s, "Only players can use this.");
            return null;
        }
        GodData.God g = plugin.data().gods.get(p.getUniqueId());
        if (g == null) err(s, "You are not a god. An op can make you one with /god grant.");
        return g;
    }

    private static Integer parseColor(String s) {
        try {
            return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void grant(CommandSender s, String[] a) {
        if (!s.hasPermission("godvoice.admin")) {
            err(s, "You need to be an operator to do that.");
            return;
        }
        if (a.length < 4) {
            err(s, "Usage: /god grant <player> <#hex> <title...>   e.g. /god grant Nox #9B30FF The Void");
            return;
        }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) {
            err(s, a[1] + " is not online.");
            return;
        }
        Integer color = parseColor(a[2]);
        if (color == null) {
            err(s, "Colour must be hex, e.g. #9B30FF");
            return;
        }
        String title = String.join(" ", Arrays.copyOfRange(a, 3, a.length)).toUpperCase();
        GodData data = plugin.data();
        GodData.God g = data.gods.computeIfAbsent(p.getUniqueId(), k -> new GodData.God());
        g.title = title;
        g.color = color;
        data.save();
        info(s, p.getName() + " is now " + title + ".");
        info(p, "You are now " + title + ". Type /god for your commands.");
    }

    private void revoke(CommandSender s, String[] a) {
        if (!s.hasPermission("godvoice.admin")) {
            err(s, "You need to be an operator to do that.");
            return;
        }
        if (a.length < 2) {
            err(s, "Usage: /god revoke <player>");
            return;
        }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) {
            err(s, a[1] + " is not online.");
            return;
        }
        if (plugin.data().vanished.contains(p.getUniqueId())) plugin.setVanished(p, false);
        if (plugin.voice() != null) plugin.voice().unlink(p.getUniqueId());
        plugin.data().gods.remove(p.getUniqueId());
        plugin.data().save();
        info(s, p.getName() + " is no longer a god.");
    }

    private void speak(CommandSender s, String[] a) {
        GodData.God g = godOf(s);
        if (g == null) return;
        if (a.length < 3) {
            err(s, "Usage: /god speak <player> <message...>");
            return;
        }
        Player t = Bukkit.getPlayerExact(a[1]);
        if (t == null) {
            err(s, a[1] + " is not online.");
            return;
        }
        String msg = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
        plugin.dialogue().send(t, g.title, g.color, msg);
        info(s, "-> " + t.getName() + ": " + msg);
    }

    private void broadcast(CommandSender s, String[] a) {
        GodData.God g = godOf(s);
        if (g == null) return;
        if (a.length < 2) {
            err(s, "Usage: /god broadcast <message...>");
            return;
        }
        String msg = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        int n = 0;
        for (UUID u : g.followers.keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                plugin.dialogue().send(p, g.title, g.color, msg);
                n++;
            }
        }
        info(s, "Spoke to " + n + " follower(s).");
    }

    private void vanish(CommandSender s) {
        GodData.God g = godOf(s);
        if (g == null) return;
        Player me = (Player) s;
        boolean now = !plugin.data().vanished.contains(me.getUniqueId());
        plugin.setVanished(me, now);
        info(s, now ? "You fade into the aura." : "You take form again.");
    }

    private void voice(CommandSender s, String[] a) {
        GodData.God g = godOf(s);
        if (g == null) return;
        VoiceBridge v = plugin.voice();
        if (v == null) {
            err(s, "Simple Voice Chat isn't installed on this server.");
            return;
        }
        if (a.length < 2) {
            err(s, "Usage: /god voice <player|followers|off>");
            return;
        }
        Player me = (Player) s;
        String arg = a[1].toLowerCase();
        if (arg.equals("off")) {
            v.unlink(me.getUniqueId());
            info(s, "Voice line closed. People nearby can hear you again.");
            return;
        }
        List<UUID> targets = new ArrayList<>();
        if (arg.equals("followers")) {
            for (UUID u : g.followers.keySet()) if (Bukkit.getPlayer(u) != null) targets.add(u);
        } else {
            Player t = Bukkit.getPlayerExact(a[1]);
            if (t == null) {
                err(s, a[1] + " is not online.");
                return;
            }
            targets.add(t.getUniqueId());
        }
        int n = v.link(me.getUniqueId(), targets);
        if (n == 0) {
            err(s, "Nobody reachable. They must be online with Simple Voice Chat connected.");
            return;
        }
        info(s, "Voice line open to " + n + " player(s). Only they hear you now. Use /god voice off to end it.");
    }

    private void model(CommandSender s, String[] a) {
        GodData.God g = godOf(s);
        if (g == null) return;
        Player me = (Player) s;
        if (a.length < 2) { // toggle: become / stop being the god
            if (plugin.model().isWearing(me.getUniqueId())) {
                plugin.model().unwear(me.getUniqueId());
                plugin.setVanished(me, false);
                info(s, "You take your mortal form again.");
            } else {
                plugin.model().wear(me, g);
                plugin.setVanished(me, true);
                info(s, "You are now " + g.title + ". Use /god model again to return to normal.");
            }
            return;
        }
        switch (a[1].toLowerCase()) {
            case "statue" -> {
                plugin.model().spawnStatue(me, g);
                info(s, "A statue of your form stands before you. /god model clear removes statues.");
            }
            case "clear" -> {
                int n = plugin.model().removeStatues(me);
                info(s, n > 0 ? "Statues removed." : "No statues found (they must be in loaded chunks).");
            }
            default -> err(s, "Usage: /god model   |   /god model statue   |   /god model clear");
        }
    }

    private void followers(CommandSender s) {
        GodData.God g = godOf(s);
        if (g == null) return;
        if (g.followers.isEmpty()) {
            info(s, "No followers yet.");
            return;
        }
        StringJoiner j = new StringJoiner(", ");
        g.followers.values().forEach(j::add);
        info(s, "Followers: " + j);
    }

    private void release(CommandSender s, String[] a) {
        GodData.God g = godOf(s);
        if (g == null) return;
        if (a.length < 2) {
            err(s, "Usage: /god release <player>");
            return;
        }
        UUID found = null;
        for (Map.Entry<UUID, String> e : g.followers.entrySet()) {
            if (e.getValue().equalsIgnoreCase(a[1])) found = e.getKey();
        }
        if (found == null) {
            err(s, "They are not your follower.");
            return;
        }
        g.followers.remove(found);
        plugin.data().save();
        info(s, "Released " + a[1] + ".");
    }

    // ---------------- /vow ----------------

    private void vow(CommandSender s, String[] a) {
        if (!(s instanceof Player me)) {
            err(s, "Only players can use this.");
            return;
        }
        GodData data = plugin.data();
        if (a.length >= 1 && a[0].equalsIgnoreCase("list")) {
            StringJoiner j = new StringJoiner(", ");
            data.gods.forEach((id, g) -> {
                if (g.followers.containsKey(me.getUniqueId())) j.add(g.title);
            });
            info(s, j.length() == 0 ? "You serve no one." : "You serve: " + j);
            return;
        }
        if (a.length < 2 || !(a[0].equalsIgnoreCase("swear") || a[0].equalsIgnoreCase("break"))) {
            err(s, "Usage: /vow swear <god> | /vow break <god> | /vow list");
            return;
        }
        Player godPlayer = Bukkit.getPlayerExact(a[1]);
        GodData.God g = godPlayer == null ? null : data.gods.get(godPlayer.getUniqueId());
        if (g == null || godPlayer.getUniqueId().equals(me.getUniqueId())) {
            err(s, "That is not a god you can vow to (they must be online).");
            return;
        }
        if (a[0].equalsIgnoreCase("swear")) {
            if (g.followers.containsKey(me.getUniqueId())) {
                err(s, "You already serve " + g.title + ".");
                return;
            }
            g.followers.put(me.getUniqueId(), me.getName());
            data.save();
            plugin.dialogue().send(me, g.title, g.color, "Your vow is heard.");
            info(godPlayer, me.getName() + " has sworn a vow to you.");
        } else {
            if (g.followers.remove(me.getUniqueId()) == null) {
                err(s, "You have no vow with them.");
                return;
            }
            data.save();
            info(s, "Your vow to " + g.title + " is broken.");
            info(godPlayer, me.getName() + " broke their vow.");
        }
    }

    // ---------------- tab complete ----------------

    private List<String> onlineNames() {
        List<String> n = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) n.add(p.getName());
        return n;
    }

    private List<String> godNames() {
        List<String> n = new ArrayList<>();
        for (UUID id : plugin.data().gods.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) n.add(p.getName());
        }
        return n;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (cmd.getName().equalsIgnoreCase("vow")) {
            if (a.length == 1) out = List.of("swear", "break", "list");
            else if (a.length == 2 && !a[0].equalsIgnoreCase("list")) out = godNames();
        } else if (a.length == 1) {
            out = GOD_SUBS;
        } else if (a.length == 2) {
            switch (a[0].toLowerCase()) {
                case "grant", "revoke", "speak", "release" -> out = onlineNames();
                case "model" -> out = List.of("statue", "clear");
                case "voice" -> {
                    out = new ArrayList<>(onlineNames());
                    out.add("followers");
                    out.add("off");
                }
                default -> {
                }
            }
        } else if (a.length == 3 && a[0].equalsIgnoreCase("grant")) {
            out = List.of("#9B30FF", "#FFB000", "#00C8FF", "#FF3030");
        }
        String last = a[a.length - 1].toLowerCase();
        List<String> res = new ArrayList<>();
        for (String o : out) if (o.toLowerCase().startsWith(last)) res.add(o);
        return res;
    }
}
