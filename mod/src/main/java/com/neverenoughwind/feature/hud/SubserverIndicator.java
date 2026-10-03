package com.neverenoughwind.feature.hud;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.model.World;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

// which subserver you're on: icon and name on a dark plate
public final class SubserverIndicator {
    // placeholders until the settings menu exists
    // left edge, halfway down: clear of minimaps, effect icons and the hotbar
    public static HudPos pos = new HudPos(0f, 0.5f, 4, 0);
    public static int iconScale = 2;
    public static boolean showName = true;
    // debug: on minewind in a world thats not in the data, show its seed instead of nothing
    public static boolean showUnknown = true;

    private static final int PAD = 3;
    private static final int PLATE = 0x90000000;

    private SubserverIndicator() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.of("neverenoughwind", "subserver"), (ctx, tick) -> render(ctx));
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.world == null) return;
        World world = Worlds.current();
        if (world == null) {
            if (showUnknown && Worlds.onMinewind() && !Worlds.raw().isEmpty()) drawPlate(ctx, mc.textRenderer, null, "? " + Worlds.raw(), 0xFFAAAAAA);
            return;
        }
        Icon icon = Icon.of(Identifier.of("neverenoughwind", "textures/subserver/" + world.icon() + ".png"));
        // debug: say when its the fallback for a shared seed and not a sure match
        String name = showUnknown && Worlds.guessing() ? world.name() + " (fallback)" : world.name();
        drawPlate(ctx, mc.textRenderer, icon, showName || icon == null ? name : null, 0xFFFFFFFF);
    }

    private static void drawPlate(DrawContext ctx, TextRenderer tr, Icon icon, String text, int textColor) {
        int iconSize = icon == null ? 0 : icon.size(iconScale);
        int textWidth = text == null ? 0 : tr.getWidth(text);
        int gap = icon != null && text != null ? 4 : 0;
        int w = PAD + iconSize + gap + textWidth + PAD;
        int h = PAD + Math.max(iconSize, text == null ? 0 : tr.fontHeight) + PAD;
        int x = pos.x(ctx.getScaledWindowWidth(), w), y = pos.y(ctx.getScaledWindowHeight(), h);
        ctx.fill(x, y, x + w, y + h, PLATE);
        if (icon != null) icon.draw(ctx, x + PAD, y + (h - iconSize) / 2, iconScale);
        if (text != null) ctx.drawTextWithShadow(tr, text, x + PAD + iconSize + gap, y + (h - tr.fontHeight) / 2 + 1, textColor);
    }
}
