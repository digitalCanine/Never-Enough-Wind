package com.neverenoughwind.feature.hud;

import com.neverenoughwind.adapter.Scoreboards;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.mixin.PlayerListHudAccessor;
import com.neverenoughwind.parse.EventParser;
import com.neverenoughwind.state.Reminder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

// reminders for upcoming events, and your own score under the sidebar while an event runs
public final class EventWidgets {
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GOLD = 0xFFFFAA00;

    private static final Reminder reminder = new Reminder();

    private EventWidgets() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EventWidgets::tick);
        new ScoreWidget().add();
    }

    private static void tick(MinecraftClient mc) {
        // once a second is plenty, the header changes once a minute
        if (mc.world == null || mc.world.getTime() % 20 != 0 || !Worlds.onMinewind()) return;
        Text header = ((PlayerListHudAccessor) mc.inGameHud.getPlayerListHud()).new$header();
        EventParser.Upcoming next = EventParser.header(header == null ? null : header.getString());
        // the marks are still tracked while reminders are off, so turning them on mid-countdown doesnt replay old ones
        if (reminder.reached(next) && Config.get().eventReminders) Notifications.show(next.event() + " in " + next.minutes() + " min");
    }

    private static final class ScoreWidget extends HudWidget {
        // rows on the vanilla sidebar right now
        private int lines;

        ScoreWidget() {
            super("score", "Own event score");
        }

        @Override
        protected Content content(TextRenderer tr, boolean sample) {
            Scoreboards.OwnScore own = Worlds.onMinewind() ? Scoreboards.ownScore() : null;
            if (own == null && !sample) return null;
            lines = own == null ? 0 : own.lines();
            String label = own == null ? "Fish Caught" : own.label();
            String score = own == null ? "8" : String.valueOf(own.score());
            String rank = own == null ? "#3 of 22" : "#" + own.rank() + " of " + own.players();
            int scoreX = tr.getWidth(label) + 6, rankX = scoreX + tr.getWidth(score) + 6;
            return new Content(rankX + tr.getWidth(rank), tr.fontHeight, ctx -> {
                ctx.drawTextWithShadow(tr, label, 0, 0, GOLD);
                ctx.drawTextWithShadow(tr, score, scoreX, 0, WHITE);
                ctx.drawTextWithShadow(tr, rank, rankX, 0, GRAY);
            });
        }

        // the vanilla sidebar ends at half the screen plus a third of its rows, 3 px from the right edge
        @Override
        protected int[] home(int sw, int sh, int w, int h) {
            return new int[]{sw - 3 - w, sh / 2 + lines * 9 / 3 + 4};
        }
    }
}
