package com.neverenoughwind.feature;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Chat;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.parse.ChatMatch;
import com.neverenoughwind.parse.KillLine;
import com.neverenoughwind.state.KillCounts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

// counts the kills and deaths that go by in chat and shows them on a player's /pf profile.
// only what this player was online for, so it says "seen by you" and never passes for the real numbers
public final class SeenKills {
    private static final Gson GSON = new Gson();
    private static final int SAVE_EVERY = 1200;
    private static final String PLAYER_NAME = "[A-Za-z0-9_]{2,16}";

    private static final KillCounts counts = new KillCounts();
    private static boolean dirty;
    private static int ticks;

    private SeenKills() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("neverenoughwind").resolve("kills.json");
    }

    public static void register() {
        try {
            if (Files.exists(file())) {
                counts.putAll(GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), new TypeToken<Map<String, int[]>>() {}.getType()));
            }
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read {}: {}", file(), e.toString());
        }
        Chat.listen(SeenKills::onChat);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (++ticks % SAVE_EVERY == 0) save();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> save());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> save());
    }

    private static void onChat(ChatMatch m) {
        if (!"death".equals(m.category()) || !Config.get().seenKills) return;
        KillLine line = KillLine.parse(m.line());
        if (line == null) return;
        counts.death(line.victim());
        // mobs, spells and plain words get blamed too. only someone on the player list gets the kill
        for (String name : line.blamed()) {
            if (!isPlayer(name)) continue;
            counts.kill(name);
            break;
        }
        dirty = true;
    }

    private static boolean isPlayer(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        return !NeverEnoughWind.data().chat().deathWord(name) && mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(name) != null;
    }

    private static void save() {
        if (!dirty) return;
        dirty = false;
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(counts.all()) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not write {}: {}", file(), e.toString());
        }
    }

    // the line under a /pf profile. label = the nametag being drawn: ours hangs under the profile's lowest line, "Likes"
    public static Text profileLine(EntityRenderState state, Text label) {
        if (!Config.get().seenKills || !Worlds.onMinewind() || state.entityType != EntityType.ARMOR_STAND) return null;
        if (label == null || !label.getString().startsWith("Likes: ")) return null;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return null;
        // the profile is a column of named armor stands, the player's name on top
        Box column = new Box(state.x - 0.1, state.y, state.z - 0.1, state.x + 0.1, state.y + 2, state.z + 0.1);
        String name = mc.world.getEntitiesByClass(ArmorStandEntity.class, column, e -> e.getCustomName() != null).stream()
                .max(Comparator.comparingDouble(ArmorStandEntity::getY))
                .map(e -> e.getCustomName().getString().trim()).orElse(null);
        if (name == null || !name.matches(PLAYER_NAME)) return null;
        int[] c = counts.of(name);
        if (c == null) return null;
        // same two colors the server uses on the profile
        return Text.literal("Seen by you: ").formatted(Formatting.AQUA).append(Text.literal(KillCounts.text(c)).formatted(Formatting.YELLOW));
    }
}
