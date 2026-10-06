package com.neverenoughwind.feature.hud;

import com.neverenoughwind.config.Config;
import com.neverenoughwind.config.LayoutScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// one thing on the hud. the player can move, resize and turn off every one of them in the layout screen
public abstract class HudWidget {
    private static final List<HudWidget> ALL = new ArrayList<>();

    // what to draw this frame: its size, and how to draw it with its top left corner at 0 0
    public record Content(int width, int height, Consumer<DrawContext> draw) {
    }

    // where it ends up on screen
    public record Placed(int x, int y, int width, int height, float scale, Content content) {
        public boolean has(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public final String id, name;

    protected HudWidget(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public static List<HudWidget> all() {
        return ALL;
    }

    public Config.Widget config() {
        return Config.get().widget(id);
    }

    protected void add() {
        ALL.add(this);
        HudElementRegistry.addLast(Identifier.of("neverenoughwind", id), (ctx, tick) -> renderHud(ctx));
    }

    // null = nothing to show. sample = the layout screen wants something to show even when there is nothing
    protected abstract Content content(TextRenderer tr, boolean sample);

    // top left corner for as long as the player hasnt moved it
    protected abstract int[] home(int sw, int sh, int w, int h);

    public Placed place(TextRenderer tr, int sw, int sh, boolean sample) {
        Content c = content(tr, sample);
        if (c == null) return null;
        Config.Widget cfg = config();
        int w = Math.round(c.width() * cfg.scale), h = Math.round(c.height() * cfg.scale);
        int[] at;
        if (cfg.moved) {
            HudPos pos = new HudPos(cfg.anchorX, cfg.anchorY, cfg.offsetX, cfg.offsetY,
                    cfg.pivotX < 0 ? cfg.anchorX : cfg.pivotX, cfg.pivotY < 0 ? cfg.anchorY : cfg.pivotY);
            at = new int[]{pos.x(sw, w), pos.y(sh, h)};
        } else {
            at = home(sw, sh, w, h);
        }
        return new Placed(at[0], at[1], w, h, cfg.scale, c);
    }

    public static void draw(DrawContext ctx, Placed p) {
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(p.x(), p.y());
        m.scale(p.scale(), p.scale());
        p.content().draw().accept(ctx);
        m.popMatrix();
    }

    private void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // the layout screen draws the widgets itself
        if (mc.world == null || mc.currentScreen instanceof LayoutScreen || !config().enabled) return;
        Placed p = place(mc.textRenderer, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), false);
        if (p != null) draw(ctx, p);
    }

    // trailer mode: the game skips the whole hud in f1, so the widgets get drawn from here
    public static void renderHidden(DrawContext ctx) {
        for (HudWidget w : ALL) w.renderHud(ctx);
    }
}
