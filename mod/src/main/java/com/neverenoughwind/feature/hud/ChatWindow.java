package com.neverenoughwind.feature.hud;

import com.neverenoughwind.feature.chat.Tabs;
import com.neverenoughwind.state.ChatTabs.Tab;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// a chat tab that was given its own window. looks and fades like the chat box, newest line at the bottom
public final class ChatWindow extends HudWidget {
    private static final int ROWS = 8, ROW = 9, MAX_WIDTH = 240;

    private record Row(OrderedText text, float opacity) {
    }

    private final Tab tab;
    private final int slot;
    private final String sample;

    private ChatWindow(Tab tab, int slot, String sample) {
        super("chat_" + tab.id(), tab.label + " Chat Window");
        this.tab = tab;
        this.slot = slot;
        this.sample = sample;
    }

    public static void register() {
        new ChatWindow(Tab.CLAN, 0, "[WOOL] Solaresque: anyone on?").add();
        new ChatWindow(Tab.WHISPERS, 1, "Nowher >> hey").add();
        new ChatWindow(Tab.EVENTS, 2, "Bait event begins in 5 minutes.").add();
        new ChatWindow(Tab.SYSTEM, 3, "Welcome Dogeram!").add();
    }

    // same curve the chat box uses: full for most of ten seconds, then gone quickly
    private static float fade(int age) {
        double d = MathHelper.clamp((1.0 - age / 200.0) * 10.0, 0.0, 1.0);
        return (float) (d * d);
    }

    @Override
    protected Content content(TextRenderer tr, boolean sample) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean live = Tabs.on() && Tabs.popped(tab);
        if (!live && !sample) return null;
        int width = Math.min(ChatHud.getWidth(mc.options.getChatWidth().getValue()), MAX_WIDTH);
        boolean open = mc.currentScreen instanceof ChatScreen;
        int now = mc.inGameHud.getTicks();

        // newest first
        List<Row> rows = new ArrayList<>();
        if (live) {
            Iterator<Tabs.Line> lines = Tabs.history(tab).descendingIterator();
            while (lines.hasNext() && rows.size() < ROWS) {
                Tabs.Line line = lines.next();
                float opacity = open || sample ? 1f : fade(now - line.tick());
                if (opacity <= 0f) break;
                List<OrderedText> wrapped = tr.wrapLines(line.text(), width);
                for (int i = wrapped.size() - 1; i >= 0 && rows.size() < ROWS; i--) rows.add(new Row(wrapped.get(i), opacity));
            }
        }
        if (rows.isEmpty() && sample) rows.add(new Row(Text.literal(this.sample).asOrderedText(), 1f));
        if (rows.isEmpty()) return null;

        float textOpacity = (float) (mc.options.getChatOpacity().getValue() * 0.9 + 0.1);
        float backOpacity = mc.options.getTextBackgroundOpacity().getValue().floatValue();
        return new Content(width + 4, ROWS * ROW, ctx -> {
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                int y = (ROWS - 1 - i) * ROW;
                int text = (int) (255 * textOpacity * row.opacity()), back = (int) (255 * backOpacity * row.opacity());
                if (text <= 3) continue;
                ctx.fill(0, y, width + 4, y + ROW, back << 24);
                ctx.drawTextWithShadow(tr, row.text(), 2, y + 1, ColorHelper.withAlpha(text, 0xFFFFFF));
            }
        });
    }

    // stacked up the right edge, clear of the chat box on the left
    @Override
    protected int[] home(int sw, int sh, int w, int h) {
        return new int[]{sw - w - 2, Math.max(2, sh - 40 - (slot + 1) * (h + 2))};
    }
}
