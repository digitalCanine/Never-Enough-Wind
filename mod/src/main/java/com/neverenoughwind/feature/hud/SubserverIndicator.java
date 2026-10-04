package com.neverenoughwind.feature.hud;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.model.World;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Identifier;

// which subserver you're on: icon and name, no background so it sits in the hud like vanilla text
public final class SubserverIndicator extends HudWidget {
    private static final int PAD = 3;

    private SubserverIndicator() {
        super("subserver", "Subserver");
    }

    public static void register() {
        new SubserverIndicator().add();
    }

    @Override
    protected Content content(TextRenderer tr, boolean sample) {
        // debug shows worlds that arent in the data by their seed, and says when a shared seed was a guess
        boolean debug = Config.get().debug;
        World world = Worlds.current();
        if (world == null) {
            if (debug && Worlds.onMinewind() && !Worlds.raw().isEmpty()) return plate(tr, null, "? " + Worlds.raw(), 0xFFAAAAAA);
            return sample ? plate(tr, icon("azure"), Config.get().subserverName ? "Azure" : null, 0xFFFFFFFF) : null;
        }
        Icon icon = icon(world.icon());
        String name = debug && Worlds.guessing() ? world.name() + " (fallback)" : world.name();
        return plate(tr, icon, Config.get().subserverName || icon == null ? name : null, 0xFFFFFFFF);
    }

    // left edge, halfway down: clear of minimaps, effect icons and the hotbar
    @Override
    protected int[] home(int sw, int sh, int w, int h) {
        return new int[]{4, (sh - h) / 2};
    }

    private static Icon icon(String name) {
        return Icon.of(Identifier.of("neverenoughwind", "textures/subserver/" + name + ".png"));
    }

    private static Content plate(TextRenderer tr, Icon icon, String text, int textColor) {
        // whole multiples only, so the pixel art stays sharp
        int scale = Math.max(1, Math.min(3, Config.get().subserverIconSize));
        int iconSize = icon == null ? 0 : icon.size(scale);
        int textWidth = text == null ? 0 : tr.getWidth(text);
        int gap = icon != null && text != null ? 4 : 0;
        int w = PAD + iconSize + gap + textWidth + PAD;
        int h = PAD + Math.max(iconSize, text == null ? 0 : tr.fontHeight) + PAD;
        return new Content(w, h, ctx -> {
            if (icon != null) icon.draw(ctx, PAD, (h - iconSize) / 2, scale);
            if (text != null) ctx.drawTextWithShadow(tr, text, PAD + iconSize + gap, (h - tr.fontHeight) / 2 + 1, textColor);
        });
    }
}
