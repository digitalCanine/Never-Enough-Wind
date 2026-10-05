package com.neverenoughwind.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Chat;
import com.neverenoughwind.adapter.Items;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.feature.hud.Notifications;
import com.neverenoughwind.parse.ChatMatch;
import com.neverenoughwind.state.DailyTimers;
import com.neverenoughwind.state.DailyTimers.Kind;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// the daily boss key, /daily, /weekly, the daily wild key and votes: when each can be done again.
// everything comes from lines the server prints to you and from your own inventory
public final class Dailies {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final DailyTimers timers = new DailyTimers();
    // whose timers are loaded, every account has its own
    private static String loadedFor;

    // wild key watch: the chest screen being looked at and the keys counted in it last tick
    private static Screen watched;
    private static int chestKeys, mostChestKeys, keysBefore;
    private static boolean keyLeftChest;

    private Dailies() {}

    public static void register() {
        Chat.listen(Dailies::onChat);
        ClientTickEvents.END_CLIENT_TICK.register(Dailies::tick);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("neverenoughwind").resolve("dailies.json");
    }

    private static Map<String, Map<String, Long>> readFile() {
        try {
            if (Files.exists(file())) {
                Map<String, Map<String, Long>> all = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8),
                        new TypeToken<Map<String, Map<String, Long>>>() {}.getType());
                if (all != null) return all;
            }
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read {}: {}", file(), e.toString());
        }
        return new HashMap<>();
    }

    private static void save() {
        if (loadedFor == null) return;
        try {
            Map<String, Map<String, Long>> all = readFile();
            all.put(loadedFor, timers.export());
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(all) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not write {}: {}", file(), e.toString());
        }
    }

    // makes sure the timers in memory are this account's. false when not in a world
    private static boolean ready() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        String me = mc.player.getNameForScoreboard();
        if (!me.equals(loadedFor)) {
            timers.restore(readFile().get(me));
            loadedFor = me;
        }
        return true;
    }

    private static void onChat(ChatMatch m) {
        if (!ready()) return;
        long now = System.currentTimeMillis();
        switch (m.id()) {
            // only the first boss kill of the day prints this
            case "boss_daily" -> timers.claimed(Kind.BOSS, now);
            case "daily_reward" -> {
                if (!loadedFor.equals(m.get("name"))) return;
                timers.claimed(Kind.DAILY, now);
            }
            // asking while its not ready gives the exact time left
            case "daily_cooldown" -> timers.sync(Kind.DAILY, Integer.parseInt(m.get("minutes")), now);
            // one vote pays out several of these lines in a row, the last one sets the time
            case "vote" -> {
                if (!loadedFor.equals(m.get("name"))) return;
                timers.claimed(Kind.VOTE, now);
            }
            case "vote_ready" -> timers.reset(Kind.VOTE, now);
            // fewer votes than sites means one can be voted on right now, else wait for the first to come back
            case "vote_time" -> {
                int least = DailyTimers.leastMinutes(m.get("rows"));
                if (!m.get("done").equals(m.get("sites")) || least < 0) timers.reset(Kind.VOTE, now);
                else timers.sync(Kind.VOTE, least, now);
            }
            case "weekly_cooldown" -> timers.sync(Kind.WEEKLY, Integer.parseInt(m.get("minutes")), now);
            default -> {
                return;
            }
        }
        save();
    }

    private static void tick(MinecraftClient mc) {
        if (!Worlds.onMinewind() || !ready()) {
            watched = null;
            return;
        }
        watchChest(mc);
        // once a second is plenty
        if (mc.world == null || mc.world.getTime() % 20 != 0) return;
        for (Kind kind : timers.justReady(System.currentTimeMillis())) {
            if (Config.get().dailyReminders) Notifications.show(kind.readyText);
        }
    }

    // the server says nothing when you take the daily key out of a wild chest, so this goes by what it looks like:
    // a plain chest that held exactly one key, and that key ends up with you. taking a key out of your own
    // storage can look the same, which is why a running timer is never restarted and /wind dailies can fix it
    private static void watchChest(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen) || !plainChest(screen.getTitle())) {
            watched = null;
            return;
        }
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int chestSlots = handler.getRows() * 9, inChest = 0, withYou = 0;
        for (int i = 0; i < handler.slots.size(); i++) {
            var stack = handler.slots.get(i).getStack();
            if (stack.isEmpty() || !"keys".equals(Items.info(stack).category())) continue;
            if (i < chestSlots) inChest += stack.getCount();
            else withYou += stack.getCount();
        }
        if (screen != watched) {
            watched = screen;
            chestKeys = inChest;
            mostChestKeys = inChest;
            keysBefore = withYou;
            keyLeftChest = false;
            return;
        }
        mostChestKeys = Math.max(mostChestKeys, inChest);
        if (chestKeys == 1 && inChest == 0) {
            // on the cursor or already in your inventory
            keyLeftChest = true;
        } else if (inChest > 0) {
            keyLeftChest = false;
            keysBefore = withYou;
        }
        chestKeys = inChest;
        if (!keyLeftChest || mostChestKeys != 1 || withYou <= keysBefore) return;
        keyLeftChest = false;
        long now = System.currentTimeMillis();
        if (timers.state(Kind.WILD, now) == DailyTimers.State.RUNNING) return;
        timers.claimed(Kind.WILD, now);
        save();
        if (mc.player != null) mc.player.sendMessage(Text.literal("Daily wild key noted.").formatted(Formatting.GRAY), false);
    }

    // the vanilla chest title, not a menu the server made out of a chest
    private static boolean plainChest(Text title) {
        return title.getContent() instanceof TranslatableTextContent t
                && (t.getKey().equals("container.chest") || t.getKey().equals("container.chestDouble"));
    }

    // /wind dailies
    public static Text report() {
        if (!ready()) return Text.literal("Join the server first.").formatted(Formatting.GRAY);
        long now = System.currentTimeMillis();
        MutableText out = Text.literal("Dailies").formatted(Formatting.GOLD);
        for (Kind kind : Kind.values()) {
            out.append(Text.literal("\n" + kind.label + ": ").formatted(Formatting.GRAY));
            switch (timers.state(kind, now)) {
                case READY -> out.append(Text.literal("Ready").formatted(Formatting.GREEN));
                case RUNNING -> out.append(Text.literal("in " + DailyTimers.span(timers.msLeft(kind, now))).formatted(Formatting.WHITE));
                case UNKNOWN -> out.append(Text.literal(switch (kind) {
                    case BOSS -> "Not seen yet. Starts with your next first boss kill of the day.";
                    case DAILY -> "Not seen yet. Run /daily once.";
                    case WEEKLY -> "Not seen yet. Run /weekly once.";
                    case WILD -> "Not seen yet. Starts with your next daily wild key.";
                    case VOTE -> "Not seen yet. Run /votetime once.";
                }).formatted(Formatting.DARK_GRAY));
            }
        }
        return out;
    }

    // /wind dailies <which> done|reset, for when the mod guessed wrong or missed one
    public static Text set(Kind kind, boolean done) {
        if (!ready()) return Text.literal("Join the server first.").formatted(Formatting.GRAY);
        long now = System.currentTimeMillis();
        if (done) timers.claimed(kind, now);
        else timers.reset(kind, now);
        save();
        return Text.literal(kind.label + (done ? ": marked as done, ready again in " + DailyTimers.span(kind.cooldown) + "." : ": marked as ready."))
                .formatted(Formatting.GRAY);
    }
}
