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

// the item browser: sections on the left, a searchable list, the picked entry, and the filters as drop-down menus on the right
public final class BrowserScreen extends Screen {
    private static final int ROW = 18, TABS = 64, GAP = 6, LINE = 10, FILTER = 16, FILTERS = 104, OPTION = 11;
    private static final int PANEL = 0xB0101010, HOVER = 0x30FFFFFF, PICKED = 0x50FFFFFF;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, DIM = 0xFF808080, GOLD = 0xFFFFAA00, YELLOW = 0xFFFFFF55, GREEN = 0xFF55FF55;

    // which section and search were open last, so reopening picks up where you left
    private static int lastSection;
    private static String lastQuery = "";
    // the filters and sorting picked in each section, by section name. a filter that isnt in the map shows everything
    private static final Map<String, Map<String, String>> pickedFilters = new HashMap<>();
    private static final Map<String, Catalog.Sort> pickedSort = new HashMap<>();

    private final List<Catalog.Section> sections;
    private final Map<String, ItemStack> icons = new HashMap<>();
    private final String startQuery;
    private final List<ButtonWidget> tabs = new ArrayList<>();
    // one drop-down per filter. options[0] is "everything", at = which one is picked
    private final class Menu {
        final String name;
        final List<String> options;
        final java.util.function.IntSupplier at;
        final java.util.function.IntConsumer set;
        ButtonWidget button;

        Menu(String name, List<String> options, java.util.function.IntSupplier at, java.util.function.IntConsumer set) {
            this.name = name;
            this.options = options;
            this.at = at;
            this.set = set;
        }

        void pick(int index) {
            int size = options.size();
            set.accept((index % size + size) % size);
            button.setMessage(Text.literal(name + ": " + options.get(at.getAsInt())));
            refilter();
        }
    }

    private final List<Menu> menus = new ArrayList<>();
    // the drop-down that is open, null for none, and how far its list is scrolled
    private Menu open;
    private int menuScroll;
    private TextFieldWidget search;
    private List<Catalog.Entry> shown = List.of();
    private Catalog.Entry picked;
    private int section;
    private double listScroll, detailScroll;
    // edges of the three columns
    private int left, top, bottom, listX, listW, listTop, detailX, detailW, filterX;

    // query = what to search for right away, null to keep the last search
    public BrowserScreen(List<Catalog.Section> sections, String query) {
        super(Text.literal("Minewind Items"));
        this.sections = sections;
        this.section = Math.min(lastSection, sections.size() - 1);
        this.startQuery = query == null ? lastQuery : query;
    }

    @Override
    protected void init() {
        Catalog.Section current = sections.get(section);
        boolean filters = !current.filters().isEmpty() || current.hasPrices();
        int total = Math.min(width - 20, filters ? 480 + FILTERS + GAP : 480);
        left = (width - total) / 2;
        top = 30;
        bottom = height - 14;
        listX = left + TABS + GAP;
        // the filters get their own column at the far right, the rest is shared like before
        int right = filters ? left + total - FILTERS - GAP : left + total;
        filterX = right + GAP;
        listW = (right - listX - GAP) * 45 / 100;
        listTop = top + 20;
        detailX = listX + listW + GAP;
        detailW = right - detailX;

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

        menus.clear();
        open = null;
        Map<String, String> chosen = pickedFilters.computeIfAbsent(current.name(), k -> new HashMap<>());
        for (Catalog.Filter filter : current.filters()) {
            List<String> options = new ArrayList<>();
            options.add("All");
            options.addAll(filter.options());
            addMenu(new Menu(filter.name(), options,
                    () -> chosen.get(filter.name()) == null ? 0 : Math.max(0, options.indexOf(chosen.get(filter.name()))),
                    index -> chosen.put(filter.name(), index == 0 ? null : options.get(index))));
        }
        if (current.hasPrices()) {
            List<String> options = new ArrayList<>();
            for (Catalog.Sort sort : Catalog.Sort.values()) options.add(sort.label);
            addMenu(new Menu("Sort", options,
                    () -> pickedSort.getOrDefault(current.name(), Catalog.Sort.DEFAULT).ordinal(),
                    index -> pickedSort.put(current.name(), Catalog.Sort.values()[index])));
        }
        for (int i = 0; i < tabs.size(); i++) tabs.get(i).active = i != section;
        refilter();
    }

    private void addMenu(Menu menu) {
        int y = top + menus.size() * (FILTER + 2);
        menu.button = addDrawableChild(ButtonWidget.builder(Text.literal(menu.name + ": " + menu.options.get(menu.at.getAsInt())), b -> {
            // a click opens the list under the button, a second one puts it away
            open = open == menu ? null : menu;
            menuScroll = 0;
        }).dimensions(filterX, y, FILTERS, FILTER).build());
        menus.add(menu);
    }

    private Menu menuAt(double x, double y) {
        for (Menu m : menus) {
            if (m.button.isMouseOver(x, y)) return m;
        }
        return null;
    }

    // where the open drop-down is drawn: right under its button, as tall as its options or the room there is
    private int[] menuBox() {
        int y = open.button.getY() + FILTER, rows = Math.min(open.options.size(), Math.max(3, (bottom - y - 2) / OPTION));
        return new int[]{filterX, y, filterX + FILTERS, y + rows * OPTION + 2, rows};
    }

    private boolean inMenu(double x, double y) {
        if (open == null) return false;
        int[] box = menuBox();
        return x >= box[0] && x < box[2] && y >= box[1] && y < box[3];
    }

    // another section has other filters, so the whole screen is laid out again
    private void open(int index) {
        section = index;
        lastSection = index;
        clearAndInit();
    }

    private void refilter() {
        lastQuery = search.getText();
        Catalog.Section current = sections.get(section);
        shown = current.search(search.getText(), pickedFilters.getOrDefault(current.name(), Map.of()),
                pickedSort.getOrDefault(current.name(), Catalog.Sort.DEFAULT));
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
        if (picked == null) {
            drawMenu(ctx, mouseX, mouseY);
            return;
        }
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
        drawMenu(ctx, mouseX, mouseY);
    }

    private void drawMenu(DrawContext ctx, int mouseX, int mouseY) {
        if (open == null) return;
        // above the buttons under it
        ctx.createNewRootLayer();
        int[] box = menuBox();
        ctx.fill(box[0], box[1], box[2], box[3], 0xFF000000);
        ctx.fill(box[0] + 1, box[1], box[2] - 1, box[3] - 1, 0xFF1E1E1E);
        ctx.enableScissor(box[0] + 1, box[1], box[2] - 1, box[3] - 1);
        for (int i = menuScroll; i < Math.min(open.options.size(), menuScroll + box[4]); i++) {
            int oy = box[1] + 1 + (i - menuScroll) * OPTION;
            boolean hover = mouseX >= box[0] && mouseX < box[2] && mouseY >= oy && mouseY < oy + OPTION;
            if (hover) ctx.fill(box[0] + 1, oy, box[2] - 1, oy + OPTION, HOVER);
            ctx.drawTextWithShadow(textRenderer, fit(open.options.get(i), FILTERS - 10), box[0] + 4, oy + 1,
                    i == open.at.getAsInt() ? YELLOW : WHITE);
        }
        ctx.disableScissor();
        scrollbar(ctx, box[2] - 3, box[1] + 1, box[3] - 1, menuScroll * OPTION, open.options.size() * OPTION);
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
        if (open != null) {
            // the open list scrolls, everything else waits
            if (inMenu(mouseX, mouseY)) {
                int rows = menuBox()[4];
                menuScroll = Math.max(0, Math.min(open.options.size() - rows, menuScroll + (vertical < 0 ? 3 : -3)));
            }
            return true;
        }
        // scrolling over a closed drop-down steps through it without opening
        Menu over = menuAt(mouseX, mouseY);
        if (over != null && vertical != 0) {
            over.pick(over.at.getAsInt() + (vertical < 0 ? 1 : -1));
            return true;
        }
        if (mouseX >= detailX && mouseX < detailX + detailW) {
            detailScroll = clamp(detailScroll - vertical * LINE * 3, detailHeight, bottom - top);
        } else if (mouseX >= listX && mouseX < listX + listW) {
            listScroll = clamp(listScroll - vertical * ROW * 3, shown.size() * ROW, bottom - listTop);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (open != null) {
            Menu menu = open;
            if (inMenu(mouseX, mouseY)) {
                int[] box = menuBox();
                int index = menuScroll + (int) ((mouseY - box[1] - 1) / OPTION);
                open = null;
                if (index >= 0 && index < menu.options.size()) menu.pick(index);
                return true;
            }
            // a click anywhere else only puts the list away, except on its own button, which does that itself
            if (!menu.button.isMouseOver(mouseX, mouseY)) {
                open = null;
                return true;
            }
        }
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
        if (open != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            open = null;
            return true;
        }
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
