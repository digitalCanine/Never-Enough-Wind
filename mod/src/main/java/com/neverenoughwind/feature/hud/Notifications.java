package com.neverenoughwind.feature.hud;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayDeque;
import java.util.Deque;

// one quiet line above the status bars: event reminders, dailies that are ready again. one at a time, in order
public final class Notifications extends HudWidget {
    private static final int YELLOW = 0xFFFFFF55;

    private static final Deque<String> waiting = new ArrayDeque<>();
    private static String current;
    private static int ticksLeft;

    private Notifications() {
        // the id stays "reminder" so a position saved before the rename is kept
        super("reminder", "Notifications");
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ticksLeft > 0) ticksLeft--;
            if (ticksLeft == 0) {
                current = waiting.poll();
                if (current != null) ticksLeft = Config.get().reminderSeconds * 20;
            }
        });
        new Notifications().add();
    }

    public static void show(String text) {
        if (text != null && !text.equals(current) && !waiting.contains(text)) waiting.add(text);
    }

    @Override
    protected Content content(TextRenderer tr, boolean sample) {
        String text = current != null && Worlds.onMinewind() ? current : sample ? "Bait in 15 min" : null;
        if (text == null) return null;
        return new Content(tr.getWidth(text), tr.fontHeight, ctx -> ctx.drawTextWithShadow(tr, text, 0, 0, YELLOW));
    }

    // centered over the hotbar, above whatever the status bars stack up to: extra heart rows, armor, air bubbles
    @Override
    protected int[] home(int sw, int sh, int w, int h) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int top = sh - 39;
        if (mc.player != null) {
            // same numbers the vanilla hud uses for its heart rows
            int hearts = (int) Math.ceil((Math.max(mc.player.getMaxHealth(), mc.player.getHealth()) + mc.player.getAbsorptionAmount()) / 2f);
            int rows = Math.max(1, (int) Math.ceil(hearts / 10f));
            int left = sh - 39 - (rows - 1) * Math.max(10 - (rows - 2), 3);
            if (mc.player.getArmor() > 0) left -= 10;
            int right = mc.player.getAir() < mc.player.getMaxAir() ? sh - 49 : sh - 39;
            top = Math.min(left, right);
        }
        // and never lower than the held item name that fades in at this height
        top = Math.min(top, sh - 59);
        return new int[]{(sw - w) / 2, top - h - 2};
    }
}
