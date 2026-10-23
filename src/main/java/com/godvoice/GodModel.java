package com.godvoice;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The god's form: a tall black Enderman (real mob model, animated, glowing purple eyes) scaled up,
 * with a crown of block-display antlers and a particle light-beam. It can be worn or placed as a statue.
 */
final class GodModel {
    private final GodVoicePlugin plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey rootKey;

    private record Form(Enderman body, List<BlockDisplay> antlers) {}

    private final Map<UUID, Form> worn = new HashMap<>();

    GodModel(GodVoicePlugin plugin) {
        this.plugin = plugin;
        ownerKey = new NamespacedKey(plugin, "model_owner");
        rootKey = new NamespacedKey(plugin, "model_root");
    }

    private double scale() {
        return Math.max(0.5, Math.min(16.0, plugin.getConfig().getDouble("model.scale", 2.6)));
    }

    private Form build(Location base, Player god, GodData.God g, boolean persistent) {
        final double S = scale();
        final String owner = god.getUniqueId().toString();

        Enderman body = base.getWorld().spawn(base, Enderman.class, en -> {
            en.setAI(false);
            en.setSilent(true);
            en.setInvulnerable(true);
            en.setPersistent(persistent);
            en.setRemoveWhenFarAway(false);
            en.setCanPickupItems(false);
            en.setCollidable(false);
            en.setCarriedBlock(null);
            AttributeInstance a = en.getAttribute(Attribute.SCALE);
            if (a != null) a.setBaseValue(S);
            en.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner);
            en.getPersistentDataContainer().set(rootKey, PersistentDataType.INTEGER, g.color & 0xFFFFFF);
        });

        // Antlers, positioned relative to the top of the enderman's head (2.9 blocks * scale).
        double m = S / 2.2;           // size multiplier for antler parts
        double yt = 2.9 * S / m;      // head top, in antler units
        List<BlockDisplay> antlers = new ArrayList<>();
        Material B = Material.BLACK_CONCRETE;
        for (int s = -1; s <= 1; s += 2) {
            antlers.add(part(base, owner, persistent, m, B, s * 0.5, yt + 0.5, 0.12, 1.3, 0.12, -s * 28));
            antlers.add(part(base, owner, persistent, m, B, s * 1.05, yt + 1.2, 0.09, 1.0, 0.09, -s * 62));
            antlers.add(part(base, owner, persistent, m, B, s * 0.95, yt + 1.75, 0.09, 0.9, 0.09, -s * 12));
            antlers.add(part(base, owner, persistent, m, B, s * 1.6, yt + 1.55, 0.08, 1.1, 0.08, -s * 80));
            antlers.add(part(base, owner, persistent, m, B, s * 1.35, yt + 2.2, 0.07, 0.8, 0.07, -s * 35));
            antlers.add(part(base, owner, persistent, m, B, s * 0.2, yt + 0.5, 0.08, 0.8, 0.08, s * 10));
        }
        return new Form(body, antlers);
    }

    /** One antler box: centre (cx,cy,0) and size (sx,sy,sz) in antler units, tilted rzDeg around Z. */
    private BlockDisplay part(Location base, String owner, boolean persistent, double k, Material mat,
                              double cx, double cy, double sx, double sy, double sz, double rzDeg) {
        Quaternionf q = new Quaternionf().rotateZ((float) Math.toRadians(rzDeg));
        Vector3f half = new Vector3f((float) (sx * k / 2), (float) (sy * k / 2), (float) (sz * k / 2));
        Vector3f t = new Vector3f((float) (cx * k), (float) (cy * k), 0f).sub(q.transform(half, new Vector3f()));
        Transformation tr = new Transformation(t, q,
                new Vector3f((float) (sx * k), (float) (sy * k), (float) (sz * k)), new Quaternionf());
        return base.getWorld().spawn(base, BlockDisplay.class, e -> {
            e.setBlock(mat.createBlockData());
            e.setTransformation(tr);
            e.setViewRange(3.0f);
            e.setInvulnerable(true);
            e.setPersistent(persistent);
            e.setTeleportDuration(1);
            e.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner);
        });
    }

    // ---------------- wearing the form ----------------

    boolean isWearing(UUID id) {
        return worn.containsKey(id);
    }

    Set<UUID> wearingIds() {
        return new HashSet<>(worn.keySet());
    }

    void wear(Player p, GodData.God g) {
        unwear(p.getUniqueId());
        Form f = build(p.getLocation(), p, g, false);
        if (plugin.getConfig().getBoolean("model.hide-from-self", false)) {
            p.hideEntity(plugin, f.body());
            for (BlockDisplay d : f.antlers()) p.hideEntity(plugin, d);
        }
        worn.put(p.getUniqueId(), f);
    }

    void unwear(UUID id) {
        Form f = worn.remove(id);
        if (f != null) discard(f);
    }

    private void discard(Form f) {
        f.body().remove();
        for (BlockDisplay d : f.antlers()) d.remove();
    }

    /** Keeps every worn form on its owner. Call every tick. */
    void tickFollow() {
        if (worn.isEmpty()) return;
        Iterator<Map.Entry<UUID, Form>> it = worn.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Form> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Form f = e.getValue();
            if (p == null || !p.isOnline() || !f.body().isValid()) {
                discard(f);
                it.remove();
                continue;
            }
            Location loc = p.getLocation();
            f.body().teleport(loc);
            Location flat = loc.clone();
            flat.setPitch(0f);
            for (BlockDisplay d : f.antlers()) if (d.isValid()) d.teleport(flat);
        }
    }

    // ---------------- statues ----------------

    void spawnStatue(Player god, GodData.God g) {
        Location base = god.getLocation().clone();
        Vector fwd = base.getDirection().setY(0);
        if (fwd.lengthSquared() < 1e-6) fwd = new Vector(0, 0, 1);
        base.add(fwd.normalize().multiply(4));
        base.setYaw(god.getLocation().getYaw() + 180f); // face the player
        base.setPitch(0f);
        build(base, god, g, true);
    }

    /** Removes this god's statues in loaded chunks (not the form being worn). */
    int removeStatues(Player god) {
        String owner = god.getUniqueId().toString();
        Form mine = worn.get(god.getUniqueId());
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!(e instanceof Enderman) && !(e instanceof BlockDisplay)) continue;
                String o = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                if (!owner.equals(o)) continue;
                if (mine != null && (e.equals(mine.body()) || mine.antlers().contains(e))) continue;
                e.remove();
                n++;
            }
        }
        return n;
    }

    // ---------------- particles ----------------

    /** Light beam + mist around every form near a player. Call every tick. */
    void tickParticles(int tick) {
        if (tick % 4 != 0) return;
        Set<UUID> seen = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (Entity e : p.getNearbyEntities(48, 40, 48)) {
                if (!(e instanceof Enderman) || !seen.add(e.getUniqueId())) continue;
                Integer color = e.getPersistentDataContainer().get(rootKey, PersistentDataType.INTEGER);
                if (color != null) emit(e.getLocation(), color);
            }
        }
    }

    private void emit(Location base, int color) {
        World w = base.getWorld();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double S = scale();
        Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(color), 2.2f);
        Particle.DustOptions mist = new Particle.DustOptions(Color.fromRGB(0x0A0A12), 1.8f);
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = r.nextDouble() * 1.1;
            w.spawnParticle(Particle.DUST, base.getX() + Math.cos(a) * rad, base.getY() + r.nextDouble() * 18,
                    base.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0, glow);
        }
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2, rad = r.nextDouble() * 0.7 * S / 2;
            w.spawnParticle(Particle.DUST, base.getX() + Math.cos(a) * rad, base.getY() + r.nextDouble() * 1.4 * S,
                    base.getZ() + Math.sin(a) * rad, 1, 0, 0, 0, 0, mist);
        }
        if (r.nextInt(3) == 0) {
            w.spawnParticle(Particle.END_ROD, base.getX() + r.nextDouble(-1, 1), base.getY() + r.nextDouble() * 8,
                    base.getZ() + r.nextDouble(-1, 1), 1, 0, 0, 0, 0);
        }
    }
}
