package com.neverenoughwind.feature.hud;

import com.neverenoughwind.adapter.Scoreboards;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.mixin.PlayerListHudAccessor;
import com.neverenoughwind.parse.EventParser;
import com.neverenoughwind.state.Reminder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

// event reminder above the hunger bar, and your own score under the sidebar while an event runs
public final class EventWidgets {
    // how long a reminder stays, in ticks
    public static int reminderTicks = 200;

    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, YELLOW = 0xFFFFFF55, GOLD = 0xFFFFAA00;

    private static final Reminder reminder = new Reminder();
    private static String reminderText;
    private static int reminderLeft;

    private EventWidgets() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EventWidgets::tick);
        HudElementRegistry.addLast(Identifier.of("neverenoughwind", "events"), (ctx, tick) -> render(ctx));
    }

    private static void tick(MinecraftClient mc) {
        if (reminderLeft > 0) reminderLeft--;
        // once a second is plenty, the header changes once a minute
        if (mc.world == null || mc.world.getTime() % 20 != 0 || !Worlds.onMinewind()) return;
        Text header = ((PlayerListHudAccessor) mc.inGameHud.getPlayerListHud()).new$header();
        EventParser.Upcoming next = EventParser.header(header == null ? null : header.getString());
        if (reminder.reached(next)) {
            reminderText = next.event() + " in " + next.minutes() + " min";
            reminderLeft = reminderTicks;
        }
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.world == null || !Worlds.onMinewind()) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        if (reminderLeft > 0 && reminderText != null) {
            // above the hunger bar, ending at the hotbar's right edge. one row higher while the air bubbles show
            int y = sh - 50;
            if (mc.player != null && mc.player.getAir() < mc.player.getMaxAir()) y -= 10;
            ctx.drawTextWithShadow(tr, reminderText, sw / 2 + 91 - tr.getWidth(reminderText), y, YELLOW);
        }

        Scoreboards.OwnScore own = Scoreboards.ownScore();
        if (own == null) return;
        String label = own.label();
        String score = String.valueOf(own.score());
        String rank = "#" + own.rank() + " of " + own.players();
        int w = tr.getWidth(label) + 6 + tr.getWidth(score) + 6 + tr.getWidth(rank);
        // the vanilla sidebar ends at half the screen plus a third of its rows, 3 px from the right edge
        int x = sw - 3 - w, y = sh / 2 + own.lines() * 9 / 3 + 4;
        ctx.drawTextWithShadow(tr, label, x, y, GOLD);
        x += tr.getWidth(label) + 6;
        ctx.drawTextWithShadow(tr, score, x, y, WHITE);
        x += tr.getWidth(score) + 6;
        ctx.drawTextWithShadow(tr, rank, x, y, GRAY);
    }
}
