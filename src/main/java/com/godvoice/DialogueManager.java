package com.godvoice;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers god dialogue.
 * - Players with the GodVoice client mod: a packet is sent and the mod draws a big dark HUD box glued to the right side.
 * - Everyone else: the right-side sidebar (fixed to the screen, not floating in the world).
 */
public final class DialogueManager {
    /** Plugin channel, must match the client mod's payload id. */
    public static final String CHANNEL = "godvoice:dialogue";

    private record Line(String title, int color, String text) {}

    private final GodVoicePlugin plugin;
    private final Map<UUID, Deque<Line>> queues = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, Scoreboard> original = new HashMap<>();

    DialogueManager(GodVoicePlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public boolean hasHud(Player p) {
        return p.getListeningPluginChannels().contains(CHANNEL);
    }

    public void send(Player target, String title, int color, String text) {
        if (text.length() > 300) text = text.substring(0, 300);
        if (hasHud(target)) {
            sendPacket(target, title, color, text);
            return;
        }
        UUID id = target.getUniqueId();
        queues.computeIfAbsent(id, k -> new ArrayDeque<>()).add(new Line(title, color, text));
        if (!active.containsKey(id)) next(id);
    }

    // ---------- client mod path ----------

    private void sendPacket(Player p, String title, int color, String text) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bos);
            writeString(out, title);
            out.writeInt(color & 0xFFFFFF);
            writeString(out, text);
            p.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.6f);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not send dialogue packet: " + e.getMessage());
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int v = b.length;
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
        out.write(b);
    }

    // ---------- sidebar path ----------

    public void clear(UUID id) {
        queues.remove(id);
        Active a = active.remove(id);
        if (a != null) a.stopQuietly();
        restore(id);
    }

    public void clearAll() {
        for (UUID id : new ArrayList<>(active.keySet())) clear(id);
    }

    private void restore(UUID id) {
        Scoreboard orig = original.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null && orig != null) p.setScoreboard(orig);
    }

    private void next(UUID id) {
        Deque<Line> q = queues.get(id);
        Player p = Bukkit.getPlayer(id);
        if (q == null || q.isEmpty() || p == null) {
            queues.remove(id);
            restore(id);
            return;
        }
        active.put(id, new Active(id, q.poll(), p));
    }

    /** Width of a character in Minecraft's default font, in pixels (incl. 1px spacing). */
    private static int charPx(char c) {
        switch (c) {
            case ' ': return 4;
            case '!': case '.': case ',': case ':': case ';': case 'i': case '|': return 2;
            case '\'': case '`': case 'l': return 3;
            case 'I': case '[': case ']': case 't': return 4;
            case '"': case '(': case ')': case '*': case '<': case '>': case 'f': case 'k': case '{': case '}': return 5;
            case '@': case '~': return 7;
            default: return 6;
        }
    }

    private static int px(String s, boolean bold) {
        int w = 0;
        for (char c : s.toCharArray()) w += charPx(c) + (bold ? 1 : 0);
        return w;
    }

    private static List<String> wrap(String text, int widthPx) {
        List<String> rows = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.trim().split("\\s+")) {
            if (cur.length() > 0 && px(cur + " " + w, false) > widthPx) {
                rows.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) rows.add(cur.toString());
        return rows;
    }

    private static int pad(int T, int w) {
        return Math.max(1, (T - w + 2) / 4 + 1);
    }

    private final class Active extends BukkitRunnable {
        private final UUID id;
        private final int hold;
        private int age;

        Active(UUID id, Line line, Player p) {
            this.id = id;
            this.hold = Math.min(400, 60 + line.text().length() * 3);
            original.putIfAbsent(id, p.getScoreboard());

            TextColor c = TextColor.color(line.color() & 0xFFFFFF);
            Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective obj = sb.registerNewObjective("godvoice", Criteria.DUMMY,
                    Component.text(line.title(), c, TextDecoration.BOLD));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());

            List<String> rows = wrap(line.text(), plugin.getConfig().getInt("sidebar.wrap-px", 150));
            int T = px(line.title(), true);
            for (String r : rows) T = Math.max(T, px(r, false));
            T = Math.max(T, 100);

            Component bar = Component.text(" \u2590", c); // coloured bar on the right
            List<Component> lines = new ArrayList<>();
            lines.add(Component.empty() // line under the name
                    .append(Component.text(" ".repeat(T / 4 + 1), NamedTextColor.WHITE, TextDecoration.UNDERLINED))
                    .append(bar));
            for (String row : rows) {
                lines.add(Component.empty()
                        .append(Component.text(row, NamedTextColor.WHITE))
                        .append(Component.text(" ".repeat(pad(T, px(row, false))), NamedTextColor.WHITE))
                        .append(bar));
            }
            int n = lines.size();
            for (int i = 0; i < n; i++) {
                Score sc = obj.getScore(" ".repeat(i + 1));
                sc.setScore(n - i);
                sc.customName(lines.get(i));
            }
            p.setScoreboard(sb);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.6f);
            runTaskTimer(plugin, 1L, 1L);
        }

        @Override
        public void run() {
            if (Bukkit.getPlayer(id) == null || ++age > hold) {
                stopQuietly();
                active.remove(id);
                next(id);
            }
        }

        void stopQuietly() {
            try {
                cancel();
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
