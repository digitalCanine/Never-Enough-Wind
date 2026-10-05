package com.neverenoughwind.feature.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Scoreboards;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.feature.Clans;
import com.neverenoughwind.feature.hud.EventWidgets;
import com.neverenoughwind.model.World;
import com.neverenoughwind.parse.EventParser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// discord rich presence. off until the player turns it on, and every part of it is their pick.
// its own module: nothing else in the mod depends on it
public final class Presence {
    // discord allows a handful of updates per 20 s, this stays well under
    private static final int UPDATE_TICKS = 300, RETRY_TICKS = 600;

    private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "neverenoughwind-discord");
        t.setDaemon(true);
        return t;
    });

    private static String applicationId = "", headUrl = "", serverIconUrl = "";
    // only touched on the worker thread
    private static DiscordIpc ipc;
    private static String sent;
    private static int ticks, retryAt;
    private static long since;

    private Presence() {}

    public static void register() {
        try (InputStream in = Presence.class.getResourceAsStream("/neverenoughwind/data/discord.json")) {
            if (in != null) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                applicationId = text(root, "application_id");
                headUrl = text(root, "head_url");
                serverIconUrl = text(root, "server_icon_url");
            }
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read discord.json: {}", e.toString());
        }
        ClientTickEvents.END_CLIENT_TICK.register(Presence::tick);
    }

    // false = no application set up, the settings say so instead of offering a switch that does nothing
    public static boolean available() {
        return !applicationId.isBlank();
    }

    private static String text(JsonObject o, String field) {
        return o.has(field) && o.get(field).isJsonPrimitive() ? o.get(field).getAsString() : "";
    }

    private static void tick(MinecraftClient mc) {
        if (!available() || ++ticks % UPDATE_TICKS != 0) return;
        boolean on = Config.get().discord && Worlds.onMinewind() && mc.player != null;
        if (!on) since = 0;
        else if (since == 0) since = System.currentTimeMillis() / 1000;
        JsonObject activity = on ? activity(mc) : null;
        String wanted = String.valueOf(activity);
        int now = ticks;
        worker.execute(() -> push(activity, wanted, now));
    }

    private static void push(JsonObject activity, String wanted, int now) {
        try {
            if (activity == null && ipc == null) return;
            if (ipc == null) {
                if (now < retryAt) return;
                ipc = DiscordIpc.connect(applicationId);
                if (ipc == null) {
                    retryAt = now + RETRY_TICKS;
                    return;
                }
                sent = null;
            }
            if (Objects.equals(wanted, sent)) return;
            ipc.setActivity(activity);
            sent = wanted;
            // nothing to show anymore: let go of discord until its wanted again
            if (activity == null) drop();
        } catch (Exception e) {
            drop();
            retryAt = now + RETRY_TICKS;
        }
    }

    private static void drop() {
        if (ipc != null) ipc.close();
        ipc = null;
        sent = null;
    }

    private static JsonObject activity(MinecraftClient mc) {
        Config c = Config.get();
        JsonObject a = new JsonObject();
        String first = line(c.discordLine1, mc), second = line(c.discordLine2, mc);
        if (first != null) a.addProperty("details", first);
        if (second != null) a.addProperty("state", second);
        if (c.discordElapsed) {
            JsonObject time = new JsonObject();
            time.addProperty("start", since);
            a.add("timestamps", time);
        }
        JsonObject assets = new JsonObject();
        String[] big = picture(c.discordBigPicture, mc), small = picture(c.discordSmallPicture, mc);
        if (big != null) {
            assets.addProperty("large_image", big[0]);
            // hovering the big picture tells the event when there is one
            String event = event();
            assets.addProperty("large_text", event != null ? event : big[1]);
        }
        // the same picture twice says nothing
        if (small != null && (big == null || !small[0].equals(big[0]))) {
            assets.addProperty("small_image", small[0]);
            assets.addProperty("small_text", small[1]);
        }
        if (assets.size() > 0) a.add("assets", assets);
        return a;
    }

    // discord wants 2 to 128 characters, null leaves the line out
    private static String line(Config.DiscordLine what, MinecraftClient mc) {
        String text = switch (what) {
            case NOTHING -> null;
            case SUBSERVER -> {
                World world = Worlds.current();
                yield world == null ? null : "On " + world.name();
            }
            case CLAN -> Clans.ownClan();
            case NAME_CLAN -> nameAndClan(mc);
            case NAME -> mc.getSession().getUsername();
            case EVENT -> event();
            case PARTY -> {
                int size = Scoreboards.partyMembers().size();
                yield size < 2 ? null : "In a party of " + size;
            }
            case HELD_ITEM -> {
                ItemStack held = mc.player.getMainHandStack();
                yield held.isEmpty() ? null : "Holding " + held.getName().getString();
            }
        };
        if (text == null || text.length() < 2) return null;
        return text.length() > 128 ? text.substring(0, 125) + "..." : text;
    }

    // "digitalCanine, AXE Leader", or only the name without a clan
    private static String nameAndClan(MinecraftClient mc) {
        String clan = Clans.ownClan();
        return clan == null ? mc.getSession().getUsername() : mc.getSession().getUsername() + ", " + clan;
    }

    // the event and your own standing when you have a score in a running one, else what is coming up
    private static String event() {
        Scoreboards.OwnScore own = Scoreboards.ownScore();
        EventParser.Upcoming next = EventWidgets.upcoming();
        if (own != null) {
            // the scoreboard title only says what is counted ("22 remain"), the event's name is in the tab list
            String standing = own.label() + ", #" + own.rank() + " of " + own.players();
            return next != null && next.running() ? next.event() + ": " + standing : standing;
        }
        if (next == null) return null;
        return next.running() ? next.event() + " is running" : next.event() + " in " + next.minutes() + " min";
    }

    // {picture, hover text}, null for none. subserver = an art asset of the application, the others are urls discord loads.
    // the hover text says something the picture alone doesnt: where you are, who you are, which server
    private static String[] picture(Config.DiscordPicture what, MinecraftClient mc) {
        switch (what) {
            case SUBSERVER -> {
                World world = Worlds.current();
                return world == null || world.icon() == null ? null : new String[]{world.icon(), "On " + world.name()};
            }
            case HEAD -> {
                UUID id = mc.getSession().getUuidOrNull();
                return id == null || headUrl.isBlank() ? null
                        : new String[]{headUrl.replace("{uuid}", id.toString()), nameAndClan(mc)};
            }
            case SERVER_ICON -> {
                ServerInfo server = mc.getCurrentServerEntry();
                return server == null || serverIconUrl.isBlank() ? null
                        : new String[]{serverIconUrl.replace("{address}", server.address), "Minewind, " + server.address};
            }
            default -> {
                return null;
            }
        }
    }
}
