package com.neverenoughwind.feature.chat;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.parse.ChatMatch;
import com.neverenoughwind.state.ChatTabs;
import com.neverenoughwind.state.ChatTabs.Tab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

// the tabs over the chat box. the game keeps drawing chat itself, we only decide which lines the open tab lets through
public final class Tabs {
    public record Line(Text text, int tick) {
    }

    private static final int KEPT = 100;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFA0A0A0, YELLOW = 0xFFFFFF55, DARK = 0xFF606060;
    private static final int PAD = 3, GAP = 1, HEIGHT = 11;

    private static Tab active = Tab.MAIN;
    private static final Set<Tab> unread = EnumSet.noneOf(Tab.class);
    private static final Map<Tab, Deque<Line>> history = new EnumMap<>(Tab.class);
    // the same text object comes back every time the game rebuilds the chat
    private static final Map<Text, Tab> known = new IdentityHashMap<>();
    private static String prefilled = "";

    private Tabs() {}

    public static boolean on() {
        return Config.get().chatTabs && NeverEnoughWind.data() != null && Worlds.onMinewind();
    }

    public static Tab active() {
        return active;
    }

    public static boolean popped(Tab tab) {
        return Config.get().poppedTabs.contains(tab.id());
    }

    public static Deque<Line> history(Tab tab) {
        return history.computeIfAbsent(tab, t -> new ArrayDeque<>());
    }

    private static Set<Tab> poppedSet() {
        Set<Tab> out = EnumSet.noneOf(Tab.class);
        for (Tab t : Tab.values()) {
            if (popped(t)) out.add(t);
        }
        return out;
    }

    private static Set<Tab> inMain() {
        Set<Tab> out = EnumSet.noneOf(Tab.class);
        if (Config.get().clanInMain) out.add(Tab.CLAN);
        if (Config.get().whispersInMain) out.add(Tab.WHISPERS);
        return out;
    }

    private static Tab tabOf(Text text) {
        Tab tab = known.get(text);
        if (tab == null) {
            if (known.size() > 4000) known.clear();
            tab = ChatTabs.of(NeverEnoughWind.data().chat().match(text.getString()).map(ChatMatch::category).orElse(null));
            known.put(text, tab);
        }
        return tab;
    }

    // does the chat box show this line right now
    public static boolean shows(Text text) {
        return !on() || ChatTabs.shows(active, tabOf(text), inMain(), poppedSet());
    }

    // every line that reaches the chat, whatever tab is open
    public static void onLine(Text text) {
        if (!on()) return;
        Tab tab = tabOf(text);
        Deque<Line> lines = history(tab);
        lines.addLast(new Line(text, MinecraftClient.getInstance().inGameHud.getTicks()));
        while (lines.size() > KEPT) lines.removeFirst();
        if (!popped(tab) && !ChatTabs.shows(active, tab, inMain(), poppedSet())) unread.add(tab);
    }

    public static void clear() {
        history.clear();
        known.clear();
        unread.clear();
    }

    // settings changed, or a tab was switched: the game rebuilds the chat box through shows()
    public static void refresh() {
        if (popped(active)) active = Tab.MAIN;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().reset();
    }

    private static void select(Tab tab, TextFieldWidget field) {
        if (popped(tab) || tab == active) return;
        active = tab;
        unread.remove(tab);
        refresh();
        // only swap what we wrote ourselves, never something the player typed
        if (field != null && (field.getText().isEmpty() || field.getText().equals(prefilled))) {
            field.setText("");
            prefill(field);
        }
    }

    private static void togglePop(Tab tab) {
        if (tab == Tab.MAIN) return;
        Config c = Config.get();
        if (!c.poppedTabs.remove(tab.id())) c.poppedTabs.add(tab.id());
        unread.remove(tab);
        Config.save();
        refresh();
    }

    // the command for the open tab, written into an empty chat box. the player still sends it
    public static void prefill(TextFieldWidget field) {
        prefilled = "";
        if (!on() || !Config.get().chatPrefill) return;
        String text = NeverEnoughWind.data().chat().prefill(active.id());
        if (text == null || text.isEmpty()) return;
        prefilled = text;
        field.setText(text);
        field.setCursorToEnd(false);
    }

    // enter on nothing but our own prefill sends nothing
    public static boolean onlyPrefill(String text) {
        return on() && !prefilled.isEmpty() && text != null && text.trim().equals(prefilled.trim());
    }

    private static int top(int screenHeight) {
        // between the lowest chat line and the box you type in
        return screenHeight - 38;
    }

    public static void renderBar(DrawContext ctx, int mouseX, int mouseY) {
        if (!on()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        int back = mc.options.getTextBackgroundColor(Integer.MIN_VALUE);
        int x = 2, y = top(ctx.getScaledWindowHeight());
        for (Tab tab : Tab.values()) {
            int w = tr.getWidth(tab.label) + PAD * 2;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + HEIGHT;
            int color = popped(tab) ? DARK : tab == active ? WHITE : unread.contains(tab) ? YELLOW : hover ? WHITE : GRAY;
            ctx.fill(x, y, x + w, y + HEIGHT, back);
            ctx.drawTextWithShadow(tr, tab.label, x + PAD, y + 2, color);
            if (tab == active) ctx.fill(x, y + HEIGHT - 1, x + w, y + HEIGHT, WHITE);
            x += w + GAP;
        }
    }

    // left click opens a tab, right click gives it its own window or takes it back
    public static boolean click(double mouseX, double mouseY, int button, TextFieldWidget field) {
        if (!on()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        int x = 2, y = top(mc.getWindow().getScaledHeight());
        if (mouseY < y || mouseY >= y + HEIGHT) return false;
        for (Tab tab : Tab.values()) {
            int w = mc.textRenderer.getWidth(tab.label) + PAD * 2;
            if (mouseX >= x && mouseX < x + w) {
                if (button == 0) select(tab, field);
                else if (button == 1) togglePop(tab);
                return true;
            }
            x += w + GAP;
        }
        return false;
    }
}
