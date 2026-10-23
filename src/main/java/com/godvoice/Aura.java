package com.godvoice;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/** Spinning "seed of life" (7 overlapping circles) plus drifting motes in the god's colour. */
final class Aura {
    private Aura() {}

    static void spawn(Player god, int color, int tick) {
        World w = god.getWorld();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double cx = god.getLocation().getX(), cy = god.getLocation().getY() + 1.0, cz = god.getLocation().getZ();
        double spin = tick * 0.06;
        double ux = Math.cos(spin), uz = Math.sin(spin);
        double R = 0.55;

        for (int i = 0; i < 10; i++) {
            int c = rnd.nextInt(7);
            double ox = 0, oy = 0;
            if (c > 0) {
                double a = Math.toRadians(60 * (c - 1));
                ox = Math.cos(a) * R;
                oy = Math.sin(a) * R;
            }
            double a2 = rnd.nextDouble() * Math.PI * 2;
            double u = ox + Math.cos(a2) * R;
            double v = oy + Math.sin(a2) * R;
            w.spawnParticle(Particle.END_ROD, cx + ux * u, cy + v, cz + uz * u, 1, 0, 0, 0, 0);
        }

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(color & 0xFFFFFF), 1.0f);
        for (int i = 0; i < 4; i++) {
            double th = rnd.nextDouble() * Math.PI * 2;
            double ph = Math.acos(2 * rnd.nextDouble() - 1);
            double r = 1.3;
            w.spawnParticle(Particle.DUST,
                    cx + r * Math.sin(ph) * Math.cos(th), cy + r * Math.cos(ph), cz + r * Math.sin(ph) * Math.sin(th),
                    1, 0, 0, 0, 0, dust);
        }
    }
}
