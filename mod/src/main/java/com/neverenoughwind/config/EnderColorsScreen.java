package com.neverenoughwind.config;

import com.neverenoughwind.feature.EnderChest;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// paint the slots of an ender tab: pick a color, click or drag over slots, right click clears
public final class EnderColorsScreen extends Screen {
    private static final int[] PALETTE = {0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF, 0xAA00AA, 0xFFFFFF, 0x555555};
    private static final int CELL = 18, ROWS = EnderChest.STORAGE / EnderChest.COLUMNS, SWATCH = 14, TAB_W = 76;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFA0A0A0, SLOT = 0x90000000, EDGE = 0x50FFFFFF;

    private final Screen parent;
    private final List<String> tabs = new ArrayList<>();
    private String tab;
    // PALETTE.length = the eraser
    private int picked;
    private int gridX, gridY, paletteX, paletteY;

    public EnderColorsScreen(Screen parent) {
        super(Text.literal("Ender Chest Colors"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        tabs.clear();
        tabs.addAll(Config.get().enderTabs.keySet());
        tabs.sort(Comparator.comparingInt(EnderChest::order));
        if (tab == null || !tabs.contains(tab)) tab = tabs.isEmpty() ? null : tabs.get(0);

        gridX = (width - EnderChest.COLUMNS * CELL) / 2;
        gridY = Math.max(34, height / 2 - 96);
        paletteX = (width - (PALETTE.length + 1) * (SWATCH + 4) + 4) / 2;
        paletteY = gridY + ROWS * CELL + 8;

        // what the picked color stands for, and the switch for sorting by it
        int ruleY = paletteY + SWATCH + 8;
        if (tab != null) {
            Config.EnderRule rule = picked < PALETTE.length ? Config.get().enderRules.get(EnderChest.key(PALETTE[picked])) : null;
            String kind = rule == null ? null : rule.kind;
            ButtonWidget holds = ButtonWidget.builder(Text.literal("Holds: " + (kind == null ? "Anything" : EnderChest.KINDS.getOrDefault(kind, kind))), b -> {
                List<String> ids = new ArrayList<>(EnderChest.KINDS.keySet());
                int at = ids.indexOf(rule().kind);
                rule().kind = at + 1 >= ids.size() ? null : ids.get(at + 1);
                clearAndInit();
            }).dimensions(width / 2 - 152, ruleY, 150, 20).build();
            holds.active = picked < PALETTE.length;
            addDrawableChild(holds);

            TextFieldWidget name = new TextFieldWidget(textRenderer, width / 2 + 2, ruleY, 150, 20, Text.literal("Name"));
            name.setMaxLength(100);
            name.setPlaceholder(Text.literal("Name has... (optional)"));
            name.setText(rule == null || rule.name == null ? "" : rule.name);
            name.setEditable(picked < PALETTE.length);
            name.setChangedListener(text -> {
                if (picked < PALETTE.length) rule().name = text.isBlank() ? null : text;
            });
            addDrawableChild(name);

            addDrawableChild(ButtonWidget.builder(Text.literal("Shift Click Sorting: " + (Config.get().enderSort ? "On" : "Off")), b -> {
                Config.get().enderSort = !Config.get().enderSort;
                clearAndInit();
            }).dimensions(width / 2 - 152, ruleY + 24, 304, 20)
                    .tooltip(Tooltip.of(Text.literal("Shift clicking an item into your ender chest sends it to a free slot of the color that holds its kind. With no such slot it goes where it always went. This is the one thing the mod clicks for you.")))
                    .build());
        }

        // one button per tab the mod has seen, a row holds as many as fit
        int perRow = Math.max(1, Math.min(tabs.size(), (width - 20) / (TAB_W + 4)));
        int top = ruleY + 48;
        for (int i = 0; i < tabs.size(); i++) {
            String id = tabs.get(i);
            int inRow = Math.min(perRow, tabs.size() - (i / perRow) * perRow);
            int left = (width - inRow * (TAB_W + 4) + 4) / 2;
            ButtonWidget button = ButtonWidget.builder(Text.literal(Config.get().enderTabs.get(id)), b -> {
                tab = id;
                clearAndInit();
            }).dimensions(left + (i % perRow) * (TAB_W + 4), top + (i / perRow) * 24, TAB_W, 20).build();
            button.active = !id.equals(tab);
            addDrawableChild(button);
        }
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close()).dimensions(width / 2 - 50, height - 28, 100, 20).build());
    }

    // the rule of the picked color, made on first use
    private Config.EnderRule rule() {
        return Config.get().enderRules.computeIfAbsent(EnderChest.key(PALETTE[picked]), k -> new Config.EnderRule());
    }

    private Map<String, Integer> colors(boolean create) {
        if (tab == null) return null;
        Map<String, Map<String, Integer>> all = Config.get().enderSlots;
        return create ? all.computeIfAbsent(tab, t -> new LinkedHashMap<>()) : all.get(tab);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, WHITE);
        if (tab == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Open your ender chest, and each of its tabs once."), width / 2, height / 2 - 14, GRAY);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("They show up here afterwards."), width / 2, height / 2 - 2, GRAY);
            return;
        }
        Map<String, Integer> colors = colors(false);
        for (int i = 0; i < EnderChest.STORAGE; i++) {
            int x = gridX + (i % EnderChest.COLUMNS) * CELL, y = gridY + (i / EnderChest.COLUMNS) * CELL;
            ctx.fill(x, y, x + CELL - 2, y + CELL - 2, SLOT);
            Integer rgb = colors == null ? null : colors.get(String.valueOf(i));
            if (rgb != null) ctx.fill(x, y, x + CELL - 2, y + CELL - 2, 0xC0000000 | rgb);
            if (slotAt(mouseX, mouseY) == i) ctx.fill(x, y, x + CELL - 2, y + CELL - 2, EDGE);
        }
        for (int i = 0; i <= PALETTE.length; i++) {
            int x = paletteX + i * (SWATCH + 4);
            if (i == picked) ctx.fill(x - 1, paletteY - 1, x + SWATCH + 1, paletteY + SWATCH + 1, WHITE);
            ctx.fill(x, paletteY, x + SWATCH, paletteY + SWATCH, i < PALETTE.length ? 0xFF000000 | PALETTE[i] : 0xFF202020);
            // the last one takes a color off again
            if (i == PALETTE.length) ctx.drawCenteredTextWithShadow(textRenderer, "x", x + SWATCH / 2, paletteY + 3, GRAY);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Click or drag to color slots. Right click clears one."), width / 2, gridY - 12, GRAY);
    }

    private int slotAt(double mouseX, double mouseY) {
        if (tab == null || mouseX < gridX || mouseY < gridY) return -1;
        int column = (int) ((mouseX - gridX) / CELL), row = (int) ((mouseY - gridY) / CELL);
        return column >= EnderChest.COLUMNS || row >= ROWS ? -1 : row * EnderChest.COLUMNS + column;
    }

    private boolean paint(double mouseX, double mouseY, int button) {
        int slot = slotAt(mouseX, mouseY);
        if (slot < 0 || button > 1) return false;
        if (button == 1 || picked == PALETTE.length) {
            Map<String, Integer> colors = colors(false);
            if (colors != null) colors.remove(String.valueOf(slot));
        } else {
            colors(true).put(String.valueOf(slot), PALETTE[picked]);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab != null && button == 0 && mouseY >= paletteY && mouseY < paletteY + SWATCH && mouseX >= paletteX) {
            int i = (int) ((mouseX - paletteX) / (SWATCH + 4));
            if (i <= PALETTE.length && mouseX - paletteX - i * (SWATCH + 4) < SWATCH) {
                picked = i;
                clearAndInit();
                return true;
            }
        }
        return paint(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return paint(mouseX, mouseY, button) || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void removed() {
        Config.save();
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
