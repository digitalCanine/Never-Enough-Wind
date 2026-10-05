package com.neverenoughwind.config;

import com.neverenoughwind.feature.hud.HudWidget;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import org.lwjgl.glfw.GLFW;

// every hud widget with something in it. drag to move, drag the corner or scroll to resize, right click to turn off, middle click to reset
// it is a chat screen on purpose: minimaps and other hud mods hide themselves under any other screen,
// and you need to see them to place things around them. none of the chat parts are used
public final class LayoutScreen extends ChatScreen {
    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 3f;
    // the square on a widget's bottom right corner
    private static final int HANDLE = 5;
    // this close to a screen edge counts as against it
    private static final int EDGE = 8;
    private static final int WHITE = 0xFFFFFFFF, FAINT = 0x80FFFFFF, OFF = 0xFFFF5555, GRAY = 0xFFAAAAAA;

    private final Screen parent;
    private HudWidget dragging;
    private boolean resizing;
    private int grabX, grabY;

    public LayoutScreen(Screen parent) {
        super("");
        this.parent = parent;
    }

    public static boolean hasMenu() {
        return FabricLoader.getInstance().isModLoaded("yet_another_config_lib_v3");
    }

    // the full menu when YACL is installed, else at least the layout
    public static Screen settings(Screen parent) {
        return hasMenu() ? ConfigScreen.create(parent) : new LayoutScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        HudWidget hovered = dragging != null ? dragging : at(mouseX, mouseY);
        for (HudWidget w : HudWidget.all()) {
            HudWidget.Placed p = w.place(textRenderer, width, height, true);
            if (p == null) continue;
            boolean on = w.config().enabled;
            HudWidget.draw(ctx, p);
            if (!on) ctx.fill(p.x(), p.y(), p.x() + p.width(), p.y() + p.height(), 0xB0000000);
            outline(ctx, p, !on ? OFF : w == hovered ? WHITE : FAINT);
            int hx = p.x() + p.width(), hy = p.y() + p.height();
            ctx.fill(hx - 2, hy - 2, hx + HANDLE - 2, hy + HANDLE - 2, w == hovered ? WHITE : FAINT);
            if (w == hovered) {
                String label = w.name + (on ? "" : " (off)") + (p.scale() == 1f ? "" : " " + Math.round(p.scale() * 100) + "%");
                int ly = p.y() - 11 < 2 ? p.y() + p.height() + 3 : p.y() - 11;
                int lx = Math.max(2, Math.min(p.x(), width - textRenderer.getWidth(label) - 2));
                ctx.drawTextWithShadow(textRenderer, label, lx, ly, WHITE);
            }
        }
        int y = height / 2 - 30;
        ctx.drawCenteredTextWithShadow(textRenderer, "Drag to move, drag the corner or scroll to resize", width / 2, y, WHITE);
        ctx.drawCenteredTextWithShadow(textRenderer, "Right click turns a widget on or off, middle click resets it", width / 2, y + 11, GRAY);
    }

    // in a world the game stays visible behind the widgets
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (client == null || client.world == null) ctx.fill(0, 0, width, height, 0xFF101010);
    }

    // the chat screen's own parts, switched off
    @Override
    protected void init() {
    }

    @Override
    protected void setInitialFocus() {
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        init(client, width, height);
    }

    @Override
    public void removed() {
    }

    @Override
    protected void insertText(String text, boolean override) {
    }

    @Override
    protected void addScreenNarrations(NarrationMessageBuilder builder) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode != GLFW.GLFW_KEY_ESCAPE) return false;
        close();
        return true;
    }

    private static void outline(DrawContext ctx, HudWidget.Placed p, int color) {
        int x1 = p.x() - 1, y1 = p.y() - 1, x2 = p.x() + p.width() + 1, y2 = p.y() + p.height() + 1;
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    private HudWidget at(double x, double y) {
        HudWidget found = null;
        for (HudWidget w : HudWidget.all()) {
            HudWidget.Placed p = w.place(textRenderer, width, height, true);
            if (p != null && (p.has(x, y) || onHandle(p, x, y))) found = w;
        }
        return found;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        HudWidget w = at(mouseX, mouseY);
        if (w == null) return false;
        Config.Widget cfg = w.config();
        if (button == 0) {
            HudWidget.Placed p = w.place(textRenderer, width, height, true);
            dragging = w;
            resizing = onHandle(p, mouseX, mouseY);
            // resizing keeps the top left corner where it is, so remember that instead
            grabX = resizing ? p.x() : (int) mouseX - p.x();
            grabY = resizing ? p.y() : (int) mouseY - p.y();
        } else if (button == 1) {
            cfg.enabled = !cfg.enabled;
        } else if (button == 2) {
            cfg.enabled = true;
            cfg.moved = false;
            cfg.scale = 1f;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging == null || button != 0) return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        HudWidget.Placed p = dragging.place(textRenderer, width, height, true);
        if (p == null) return true;
        Config.Widget cfg = dragging.config();
        if (resizing) {
            HudWidget.Content c = p.content();
            float want = Math.max((float) (mouseX - grabX) / c.width(), (float) (mouseY - grabY) / c.height());
            cfg.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round(want * 20) / 20f));
            pin(cfg, grabX, grabY, Math.round(c.width() * cfg.scale), Math.round(c.height() * cfg.scale));
            return true;
        }
        int x = Math.max(0, Math.min((int) mouseX - grabX, width - p.width()));
        int y = Math.max(0, Math.min((int) mouseY - grabY, height - p.height()));
        pin(cfg, x, y, p.width(), p.height());
        return true;
    }

    // hold on to the nearest edge or the middle of the screen, so it stays put on any window size
    private void pin(Config.Widget cfg, int x, int y, int w, int h) {
        cfg.anchorX = anchor(x + w / 2, width);
        cfg.anchorY = anchor(y + h / 2, height);
        // and decide what stays put when the content changes size: a widget against a screen edge keeps that edge,
        // anything else keeps its middle, so text of any length stays centered where you put it. lists grow downward
        cfg.pivotX = x <= EDGE ? 0f : x + w >= width - EDGE ? 1f : 0.5f;
        cfg.pivotY = y + h >= height - EDGE ? 1f : 0f;
        cfg.offsetX = x + Math.round(w * cfg.pivotX) - Math.round(width * cfg.anchorX);
        cfg.offsetY = y + Math.round(h * cfg.pivotY) - Math.round(height * cfg.anchorY);
        cfg.moved = true;
    }

    private static boolean onHandle(HudWidget.Placed p, double x, double y) {
        int hx = p.x() + p.width() - 2, hy = p.y() + p.height() - 2;
        return x >= hx - 1 && x < hx + HANDLE + 1 && y >= hy - 1 && y < hy + HANDLE + 1;
    }

    private static float anchor(int center, int size) {
        return center < size / 3 ? 0f : center > size * 2 / 3 ? 1f : 0.5f;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        HudWidget w = at(mouseX, mouseY);
        if (w == null || vertical == 0) return false;
        Config.Widget cfg = w.config();
        float next = Math.round((cfg.scale + (vertical > 0 ? 0.1f : -0.1f)) * 10) / 10f;
        cfg.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, next));
        return true;
    }

    @Override
    public void close() {
        Config.save();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
