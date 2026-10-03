package com.neverenoughwind.feature;

import com.neverenoughwind.adapter.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.Map;

// thin gradient outline on the slot frame, by item type. gear is left alone
public final class Highlighter {
    // placeholder colors until the settings menu exists
    private static final Map<String, Integer> COLORS = Map.of(
            "essence", 0xFFFF4FA3,
            "magic", 0xFFA855FF,
            "infinite", 0xFF55FFFF,
            "keys", 0xFF55FF55,
            "glamour", 0xFFFF9BEA,
            "currency", 0xFFFFAA00,
            "rarity", 0xFFFF5555,
            "mechanics", 0xFFAAAAAA);

    // how dark the bottom gets, and how strong the inner line is (0-255)
    private static final double DARKEN = 0.65;
    private static final int GLOW = 0x66;

    private Highlighter() {}

    // x, y = where the item is drawn (its top left corner)
    public static void draw(DrawContext ctx, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) return;
        Integer color = COLORS.get(String.valueOf(Items.info(stack).category()));
        if (color == null) return;
        int dark = darker(color);
        // draw in real screen pixels so the line is one pixel at any gui scale
        int scale = Math.max(1, MinecraftClient.getInstance().getWindow().getScaleFactor());
        int size = 18 * scale;
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x - 1, y - 1);
        m.scale(1f / scale);
        ring(ctx, 0, size, color, dark);
        // a see-through second line inside, so it reads without being heavy
        ring(ctx, 1, size, alpha(color, GLOW), alpha(dark, GLOW));
        m.popMatrix();
    }

    // one pixel ring, inset pixels in from the slot edge. top bright, bottom dark, sides fade
    private static void ring(DrawContext ctx, int inset, int size, int top, int bottom) {
        int a = inset, b = size - inset;
        ctx.fill(a, a, b, a + 1, top);
        ctx.fill(a, b - 1, b, b, bottom);
        ctx.fillGradient(a, a + 1, a + 1, b - 1, top, bottom);
        ctx.fillGradient(b - 1, a + 1, b, b - 1, top, bottom);
    }

    private static int alpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    private static int darker(int argb) {
        int r = (int) (((argb >> 16) & 255) * DARKEN), g = (int) (((argb >> 8) & 255) * DARKEN), b = (int) ((argb & 255) * DARKEN);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
