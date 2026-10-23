package com.godvoice.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * God dialogue HUD: a big, dark box glued to the RIGHT side of the screen (it does not move with the camera).
 * Title, a line under the name, typewriter text, and a coloured bar on the right edge.
 */
public final class DialogueOverlay {
    private record Line(String title, int color, String message) {}

    // ---- tweak these ----
    private static final float SCALE = 1.5f;       // box + text size (bigger = larger)
    private static final int BOX_WIDTH = 150;      // box width before scaling
    private static final int MARGIN = 6;           // gap from the right edge of the screen (screen px)
    private static final float BG_ALPHA = 0.92f;   // 0..1, higher = darker
    // ---------------------

    private static final long MS_PER_CHAR = 35;
    private static final long FADE_IN = 250;
    private static final long FADE_OUT = 600;

    private static final Deque<Line> queue = new ArrayDeque<>();
    private static Line current;
    private static List<String> lines = new ArrayList<>();
    private static int totalChars;
    private static long startMs;

    private DialogueOverlay() {}

    public static void push(DialoguePayload p) {
        queue.add(new Line(p.title(), p.color(), p.message()));
    }

    private static long holdMs() {
        return 2200 + totalChars * 45L;
    }

    private static long totalMs() {
        return totalChars * MS_PER_CHAR + holdMs() + FADE_OUT;
    }

    private static void start(Line next, TextRenderer font, long now) {
        current = next;
        startMs = now;
        lines = new ArrayList<>();
        totalChars = 0;
        for (StringVisitable v : font.getTextHandler().wrapLines(next.message(), BOX_WIDTH - 20, Style.EMPTY)) {
            String s = v.getString();
            lines.add(s);
            totalChars += s.length();
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        TextRenderer font = mc.textRenderer;
        long now = System.currentTimeMillis();

        if (current != null && now - startMs > totalMs()) current = null;
        if (current == null) {
            Line next = queue.poll();
            if (next == null) return;
            start(next, font, now);
        }

        long el = now - startMs;
        float alpha = 1f;
        if (el < FADE_IN) alpha = el / (float) FADE_IN;
        long fadeStart = totalMs() - FADE_OUT;
        if (el > fadeStart) alpha = 1f - (el - fadeStart) / (float) FADE_OUT;
        alpha = Math.max(0f, Math.min(1f, alpha));
        if (alpha < 0.03f) return;

        int lineH = font.fontHeight + 1;
        int titleH = font.fontHeight;
        int boxH = 6 + titleH + 5 + lines.size() * lineH + 6;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float x = sw - BOX_WIDTH * SCALE - MARGIN;      // glued to the right edge
        float y = (sh - boxH * SCALE) / 2f;             // vertically centred

        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(x, y);
        ctx.getMatrices().scale(SCALE, SCALE);

        // dark background + coloured bar on the right edge
        ctx.fill(0, 0, BOX_WIDTH, boxH, argb(alpha * BG_ALPHA, 0x000000));
        ctx.fill(BOX_WIDTH - 3, 0, BOX_WIDTH, boxH, argb(alpha, current.color()));

        // god name + a line underneath it
        ctx.drawText(font, Text.literal(current.title()).formatted(Formatting.BOLD), 7, 6, argb(alpha, 0xFFFFFF), true);
        int ly = 6 + titleH + 2;
        ctx.fill(7, ly, BOX_WIDTH - 10, ly + 1, argb(alpha * 0.85f, 0xFFFFFF));

        // typewriter body
        int remaining = (int) Math.min(totalChars, el / MS_PER_CHAR);
        int ty = 6 + titleH + 5;
        for (String s : lines) {
            if (remaining <= 0) break;
            String part = s.substring(0, Math.min(s.length(), remaining));
            remaining -= part.length();
            ctx.drawText(font, part, 7, ty, argb(alpha, 0xFFFFFF), true);
            ty += lineH;
        }

        ctx.getMatrices().popMatrix();
    }
}
