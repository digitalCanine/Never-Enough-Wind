package com.neverenoughwind.feature;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.parse.ChatMatch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayDeque;
import java.util.Deque;

// the one place the mod sends anything: /clan find for the clans seen in chat, and only after the player
// ran the command twice. one lookup at a time, answers hidden, stops by itself when the server goes quiet
public final class ClanRefresh {
    private static final int GAP_TICKS = 5;
    private static final int TIMEOUT_TICKS = 100;
    private static final int MAX_MISSES = 3;
    private static final long CONFIRM_MS = 30_000;

    private static final Deque<String> queue = new ArrayDeque<>();
    private static String pending;
    private static int ticks, sentAt, nextSend, total, done, misses;
    private static long confirmUntil;

    private ClanRefresh() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClanRefresh::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop(null));
        // while we wait for an answer, take it out of chat and hand it to the roster ourselves
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || pending == null || NeverEnoughWind.data() == null) return true;
            ChatMatch m = NeverEnoughWind.data().chat().match(message.getString()).orElse(null);
            if (m == null) return true;
            boolean found = m.id().equals("clan_find") && pending.equalsIgnoreCase(m.get("clan"));
            boolean missing = m.id().equals("clan_find_not_found") && pending.equalsIgnoreCase(m.get("clan"));
            if (!found && !missing) return true;
            if (found) Clans.onChat(m);
            Clans.forgetSeen(pending);
            pending = null;
            misses = 0;
            done++;
            return false;
        });
    }

    public static boolean running() {
        return pending != null || !queue.isEmpty();
    }

    // first call explains, second call within 30 s starts
    public static Text request() {
        if (!Worlds.onMinewind()) return Text.literal("This only works on Minewind.").formatted(Formatting.GRAY);
        if (running()) return Text.literal("Already refreshing clans, " + done + " of " + total + " done.").formatted(Formatting.GRAY);
        int count = Clans.seenTags().size();
        if (count == 0) return Text.literal("No clan tags have shown up in chat yet this session.").formatted(Formatting.GRAY);
        long now = System.currentTimeMillis();
        if (now > confirmUntil) {
            confirmUntil = now + CONFIRM_MS;
            return Text.literal("This sends /clan find for the " + count + (count == 1 ? " clan" : " clans")
                    + " seen in chat this session. Run the command again to confirm.").formatted(Formatting.YELLOW);
        }
        confirmUntil = 0;
        queue.clear();
        queue.addAll(Clans.seenTags());
        total = queue.size();
        done = 0;
        misses = 0;
        nextSend = ticks;
        return Text.literal("Refreshing " + total + (total == 1 ? " clan." : " clans.")).formatted(Formatting.GREEN);
    }

    // why = shown to the player, null to stop quietly
    public static Text stop(String why) {
        boolean was = running();
        queue.clear();
        pending = null;
        if (!was) return Text.literal("No clan refresh is running.").formatted(Formatting.GRAY);
        return Text.literal("Clan refresh stopped" + (why == null ? "" : ", " + why) + ". " + done + " of " + total + " updated.").formatted(Formatting.GRAY);
    }

    private static void tick(MinecraftClient mc) {
        ticks++;
        if (!running()) return;
        if (mc.getNetworkHandler() == null || mc.player == null) {
            stop(null);
            return;
        }
        if (pending != null) {
            if (ticks - sentAt <= TIMEOUT_TICKS) return;
            // no answer, leave the tag for next time
            pending = null;
            if (++misses >= MAX_MISSES) {
                mc.player.sendMessage(stop("the server stopped answering"), false);
                return;
            }
        }
        if (queue.isEmpty()) {
            mc.player.sendMessage(Text.literal("Clans refreshed, " + done + " of " + total + " updated.").formatted(Formatting.GREEN), false);
            return;
        }
        if (ticks < nextSend) return;
        pending = queue.poll();
        sentAt = ticks;
        nextSend = ticks + GAP_TICKS;
        mc.getNetworkHandler().sendChatCommand("clan find " + pending);
    }
}
