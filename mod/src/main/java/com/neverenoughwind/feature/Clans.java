package com.neverenoughwind.feature;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Chat;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.dao.ClanDao;
import com.neverenoughwind.parse.ChatMatch;
import com.neverenoughwind.state.Relations;
import com.neverenoughwind.state.Roster;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// clan tags on nametags and in chat, colored by how the player stands with that clan
public final class Clans {
    private static final Roster roster = new Roster();
    private static final Relations relations = new Relations();
    private static final int RANK_COLOR = 0xAAAAAA;
    private static long shippedTime;
    private static final Map<String, String> seen = new java.util.LinkedHashMap<>();

    private Clans() {}

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("neverenoughwind");
    }

    public static void register() {
        List<Roster.Clan> shipped = ClanDao.shipped();
        shippedTime = shipped.stream().mapToLong(Roster.Clan::checked).max().orElse(0);
        roster.putAll(shipped);
        ClanDao.loadPlayer(dir().resolve("clans.json"), roster);
        applyConfig();
        NeverEnoughWind.LOG.info("clans: {} in the roster", roster.size());

        Chat.listen(Clans::onChat);
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> overlay ? message : recolorChat(message));
    }

    // tags that showed up in chat this session, as written. /wind clans refreshes these
    public static List<String> seenTags() {
        return List.copyOf(seen.values());
    }

    public static void forgetSeen(String tag) {
        seen.remove(tag.toLowerCase(java.util.Locale.ROOT));
    }

    // rereads the player's files
    public static void reload() {
        ClanDao.loadPlayer(dir().resolve("clans.json"), roster);
        Config.load(dir());
        applyConfig();
    }

    // the relation lists live in the settings
    public static void applyConfig() {
        Config c = Config.get();
        relations.set(c.own, c.ally, c.enemy, c.tradebanned);
    }

    public static void onChat(ChatMatch m) {
        switch (m.id()) {
            // any /clan find answer updates the roster, whoever asked for it
            case "clan_find" -> {
                Roster.Clan clan = new Roster.Clan(m.get("clan"), m.get("clanName"), m.get("leader") == null ? "" : m.get("leader"),
                        names(m.get("officers")), names(m.get("members")), names(m.get("recruits")), System.currentTimeMillis());
                if (roster.put(clan)) save();
            }
            // only members can read a clan's chat, so this is our own clan
            case "clan_chat" -> relations.detected(m.get("clan"));
            // a real name speaking with a tag settles which of their listed clans is the current one
            case "public_chat" -> {
                if (m.get("clan") == null) break;
                seen.putIfAbsent(m.get("clan").toLowerCase(java.util.Locale.ROOT), m.get("clan"));
                if (roster.settle(m.get("name"), m.get("clan"))) save();
            }
            default -> {
            }
        }
    }

    private static List<String> names(String row) {
        List<String> out = new ArrayList<>();
        if (row == null) return out;
        for (String n : row.trim().split("\\s+")) {
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    private static void save() {
        ClanDao.savePlayer(dir().resolve("clans.json"), roster, shippedTime);
    }

    private static TextColor color(String tag) {
        Config c = Config.get();
        return TextColor.fromRgb(switch (relations.of(tag)) {
            case OWN -> c.ownColor;
            case ALLY -> c.allyColor;
            case ENEMY -> c.enemyColor;
            case TRADEBANNED -> c.tradebannedColor;
            case NEUTRAL -> c.neutralColor;
        });
    }

    // the line above a player's name: "AXE Leader", or every listed clan when the roster isnt sure. null = no line
    public static Text nametagLine(String player) {
        if (!Config.get().nametagTags || !Worlds.onMinewind()) return null;
        List<Roster.Membership> clans = roster.of(player);
        if (clans.isEmpty()) return null;
        MutableText line = Text.empty();
        for (int i = 0; i < clans.size(); i++) {
            Roster.Membership m = clans.get(i);
            if (i > 0) line.append(Text.literal(" · ").styled(s -> s.withColor(RANK_COLOR)));
            line.append(Text.literal(m.tag()).styled(s -> s.withColor(color(m.tag()))));
            String rank = Character.toUpperCase(m.rank().charAt(0)) + m.rank().substring(1);
            line.append(Text.literal(" " + rank).styled(s -> s.withColor(RANK_COLOR)));
        }
        return line;
    }

    // the name on a nametag, in the color of that player's clan. neutral players keep the server's color
    public static Text nametagName(String player, Text original) {
        if (!Config.get().nameColors || !Worlds.onMinewind() || player == null) return original;
        // listed in several clans: the first one you have a stance on decides
        for (Roster.Membership m : roster.of(player)) {
            if (relations.of(m.tag()) == Relations.Kind.NEUTRAL) continue;
            return recolor(original, player, color(m.tag()), new boolean[]{false});
        }
        return original;
    }

    // chat: color the tag the line itself carries. the speaker can be nicked, so the tag is all we go by
    private static Text recolorChat(Text message) {
        try {
            if (NeverEnoughWind.data() == null || !Config.get().chatColors || !Worlds.onMinewind()) return message;
            ChatMatch m = NeverEnoughWind.data().chat().match(message.getString()).orElse(null);
            if (m == null || !m.id().equals("public_chat") || m.get("clan") == null) return message;
            if (relations.of(m.get("clan")) == Relations.Kind.NEUTRAL) return message;
            // only the tag. the name keeps its rank or custom color
            return recolor(message, m.get("clan") + ".", color(m.get("clan")), new boolean[]{false});
        } catch (RuntimeException e) {
            return message;
        }
    }

    // copies the text, repainting the first piece that is exactly the token
    private static Text recolor(Text text, String token, TextColor color, boolean[] done) {
        MutableText copy = text.copyContentOnly().setStyle(text.getStyle());
        if (!done[0] && text.getContent() instanceof PlainTextContent plain && plain.string().equals(token)) {
            copy.setStyle(text.getStyle().withColor(color));
            done[0] = true;
        }
        for (Text sibling : text.getSiblings()) copy.append(recolor(sibling, token, color, done));
        return copy;
    }
}
