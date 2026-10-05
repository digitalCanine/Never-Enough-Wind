package com.neverenoughwind.feature.browse;

import com.neverenoughwind.parse.Catalog;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the item browser: sections on the left, a searchable list in the middle, the picked entry on the right
public final class BrowserScreen extends Screen {
    private static final int ROW = 18, TABS = 64, GAP = 6, LINE = 10;
    private static final int PANEL = 0xB0101010, HOVER = 0x30FFFFFF, PICKED = 0x50FFFFFF;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, DIM = 0xFF808080, GOLD = 0xFFFFAA00, YELLOW = 0xFFFFFF55, GREEN = 0xFF55FF55;

    // which section and search were open last, so reopening picks up where you left
    private static int lastSection;
    private static String lastQuery = "";

    private final List<Catalog.Section> sections;
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final String startQuery;
    private final List<ButtonWidget> tabs = new ArrayList<>();
    private TextFieldWidget search;
    private List<Catalog.Entry> shown = List.of();
    private Catalog.Entry picked;
    private int section;
    private double listScroll, detailScroll;
    // edges of the three columns
    private int left, top, bottom, listX, listW, listTop, detailX, detailW;

    // query = what to search for right away, null to keep the last search
    public BrowserScreen(List<Catalog.Section> sections, String query) {
        super(Text.literal("Minewind Items"));
        this.sections = sections;
        this.section = Math.min(lastSection, sections.size() - 1);
        this.startQuery = query == null ? lastQuery : query;
    }

    @Override
    protected void init() {
        int total = Math.min(width - 20, 480);
        left = (width - total) / 2;
        top = 30;
        bottom = height - 14;
        listX = left + TABS + GAP;
        listW = (left + total - listX - GAP) * 45 / 100;
        listTop = top + 20;
        detailX = listX + listW + GAP;
        detailW = left + total - detailX;

        tabs.clear();
        for (int i = 0; i < sections.size(); i++) {
            int index = i;
            ButtonWidget tab = ButtonWidget.builder(Text.literal(sections.get(i).name()), b -> open(index))
                    .dimensions(left, top + i * 22, TABS, 20).build();
            tabs.add(addDrawableChild(tab));
        }
        String text = search == null ? startQuery : search.getText();
        search = new TextFieldWidget(textRenderer, listX, top, listW, 16, Text.literal("Search"));
        search.setPlaceholder(Text.literal("Search..."));
        search.setMaxLength(60);
        search.setText(text);
        search.setChangedListener(q -> refilter());
        addDrawableChild(search);
        open(section);
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(search);
    }

    private void open(int index) {
        section = index;
        lastSection = index;
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).active = i != index;
        refilter();
    }

    private void refilter() {
        lastQuery = search.getText();
        shown = sections.get(section).search(search.getText());
        listScroll = 0;
        if (!shown.contains(picked)) pick(shown.isEmpty() ? null : shown.get(0));
    }

    private void pick(Catalog.Entry entry) {
        picked = entry;
        detailScroll = 0;
    }

    private ItemStack icon(String id) {
        return icons.computeIfAbsent(id, k -> {
            Identifier key = Identifier.tryParse(k);
            return key != null && Registries.ITEM.containsId(key) ? new ItemStack(Registries.ITEM.get(key)) : new ItemStack(net.minecraft.item.Items.PAPER);
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, WHITE);

        // the list
        ctx.fill(listX, listTop, listX + listW, bottom, PANEL);
        ctx.enableScissor(listX, listTop, listX + listW, bottom);
        int first = (int) (listScroll / ROW);
        for (int i = first; i < shown.size(); i++) {
            int y = listTop + i * ROW - (int) listScroll;
            if (y >= bottom) break;
            Catalog.Entry e = shown.get(i);
            boolean hover = mouseX >= listX && mouseX < listX + listW && mouseY >= y && mouseY < y + ROW && mouseY >= listTop && mouseY < bottom;
            if (e == picked) ctx.fill(listX, y, listX + listW, y + ROW, PICKED);
            else if (hover) ctx.fill(listX, y, listX + listW, y + ROW, HOVER);
            ctx.drawItem(icon(e.icon()), listX + 1, y + 1);
            int tagW = e.tag().isEmpty() ? 0 : textRenderer.getWidth(e.tag()) + 6;
            ctx.drawTextWithShadow(textRenderer, fit(e.name(), listW - 26 - tagW), listX + 21, y + 5, WHITE);
            if (tagW > 0) ctx.drawTextWithShadow(textRenderer, e.tag(), listX + listW - tagW + 2, y + 5, YELLOW);
        }
        ctx.disableScissor();
        if (shown.isEmpty()) ctx.drawCenteredTextWithShadow(textRenderer, "Nothing found", listX + listW / 2, listTop + 12, DIM);
        scrollbar(ctx, listX + listW - 2, listTop, bottom, listScroll, shown.size() * ROW);

        // the picked entry
        ctx.fill(detailX, top, detailX + detailW, bottom, PANEL);
        if (picked == null) return;
        ctx.enableScissor(detailX, top, detailX + detailW, bottom);
        int y = top + 6 - (int) detailScroll, inner = detailW - 12;
        boolean head = true;
        for (Catalog.Line line : picked.lines()) {
            int x = detailX + 6, w = inner;
            if (head) {
                // the icon sits left of the name
                ctx.drawItem(icon(picked.icon()), x, y - 4);
                x += 20;
                w -= 20;
            }
            if (line.right() != null) {
                int rw = textRenderer.getWidth(line.right());
                ctx.drawTextWithShadow(textRenderer, line.left(), x, y, color(line.leftTone()));
                // a long value goes on its own lines under the label
                if (textRenderer.getWidth(line.left()) + 8 + rw <= w) {
                    ctx.drawTextWithShadow(textRenderer, line.right(), x + w - rw, y, color(line.rightTone()));
                    y += LINE;
                } else {
                    y += LINE;
                    for (OrderedText part : textRenderer.wrapLines(Text.literal(line.right()), w)) {
                        ctx.drawTextWithShadow(textRenderer, part, x, y, color(line.rightTone()));
                        y += LINE;
                    }
                }
            } else if (line.left().isEmpty()) {
                y += LINE / 2;
            } else {
                for (OrderedText part : textRenderer.wrapLines(Text.literal(line.left()), w)) {
                    ctx.drawTextWithShadow(textRenderer, part, x, y, color(line.leftTone()));
                    y += LINE;
                }
            }
            if (head) {
                y = Math.max(y, top + 6 - (int) detailScroll + 16);
                head = false;
            }
        }
        ctx.disableScissor();
        detailHeight = y + (int) detailScroll - top + 6;
        scrollbar(ctx, detailX + detailW - 2, top, bottom, detailScroll, detailHeight);
    }

    // how tall the picked entry came out last frame, for scrolling it
    private int detailHeight;

    private static int color(Catalog.Tone tone) {
        return switch (tone) {
            case TITLE -> GOLD;
            case LABEL -> YELLOW;
            case TEXT -> GRAY;
            case VALUE -> WHITE;
            case GOOD -> GREEN;
            case DIM -> DIM;
        };
    }

    private String fit(String text, int room) {
        if (textRenderer.getWidth(text) <= room) return text;
        return textRenderer.trimToWidth(text, Math.max(0, room - textRenderer.getWidth("..."))) + "...";
    }

    private static void scrollbar(DrawContext ctx, int x, int y1, int y2, double scroll, int content) {
        int view = y2 - y1;
        if (content <= view) return;
        int size = Math.max(12, view * view / content);
        int at = y1 + (int) ((view - size) * scroll / (content - view));
        ctx.fill(x, at, x + 2, at + size, 0x80FFFFFF);
    }

    private double clamp(double scroll, int content, int view) {
        return Math.max(0, Math.min(scroll, Math.max(0, content - view)));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= detailX && mouseX < detailX + detailW) {
            detailScroll = clamp(detailScroll - vertical * LINE * 3, detailHeight, bottom - top);
        } else if (mouseX >= listX && mouseX < listX + listW) {
            listScroll = clamp(listScroll - vertical * ROW * 3, shown.size() * ROW, bottom - listTop);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listX && mouseX < listX + listW && mouseY >= listTop && mouseY < bottom) {
            int index = (int) ((mouseY - listTop + listScroll) / ROW);
            if (index >= 0 && index < shown.size()) pick(shown.get(index));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // up and down walk the list while the search box keeps the typing
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) && !shown.isEmpty()) {
            int index = Math.max(0, Math.min(shown.size() - 1, shown.indexOf(picked) + (keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1)));
            pick(shown.get(index));
            int view = bottom - listTop;
            if (index * ROW < listScroll) listScroll = index * ROW;
            else if ((index + 1) * ROW > listScroll + view) listScroll = (index + 1) * ROW - view;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
