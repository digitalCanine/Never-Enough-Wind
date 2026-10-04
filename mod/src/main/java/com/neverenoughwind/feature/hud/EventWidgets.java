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

// event reminder above the status bars, and your own score under the sidebar while an event runs
public final class EventWidgets {
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, YELLOW = 0xFFFFFF55, GOLD = 0xFFFFAA00;

    private static final Reminder reminder = new Reminder();
    private static String reminderText;
    private static int reminderLeft;

    private EventWidgets() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(EventWidgets::tick);
        new ReminderWidget().add();
        new ScoreWidget().add();
    }

    private static void tick(MinecraftClient mc) {
        if (reminderLeft > 0) reminderLeft--;
        // once a second is plenty, the header changes once a minute
        if (mc.world == null || mc.world.getTime() % 20 != 0 || !Worlds.onMinewind()) return;
        Text header = ((PlayerListHudAccessor) mc.inGameHud.getPlayerListHud()).new$header();
        EventParser.Upcoming next = EventParser.header(header == null ? null : header.getString());
        if (reminder.reached(next)) {
            reminderText = next.event() + " in " + next.minutes() + " min";
            reminderLeft = Config.get().reminderSeconds * 20;
        }
    }

    private static final class ReminderWidget extends HudWidget {
        ReminderWidget() {
            super("reminder", "Event reminder");
        }

        @Override
        protected Content content(TextRenderer tr, boolean sample) {
            String text = reminderLeft > 0 && reminderText != null && Worlds.onMinewind() ? reminderText : sample ? "Bait in 15 min" : null;
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
