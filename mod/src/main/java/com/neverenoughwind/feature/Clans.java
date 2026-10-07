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
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.PlainTextContent;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // the line above a player's name: "WOOL Leader", or every listed clan when the roster isnt sure. null = no line
    public static Text nametagLine(String player) {
        if (!Config.get().nametagTags || !Worlds.onMinewind()) return null;
        List<Roster.Membership> clans = roster.of(player);
        if (clans.isEmpty()) return null;
        MutableText line = Text.empty();
        for (int i = 0; i < clans.size(); i++) {
            Roster.Membership m = clans.get(i);
            if (i > 0) line.append(Text.literal(" · ").styled(s -> s.withColor(RANK_COLOR)));
            Text tag = Text.literal(m.tag()).styled(s -> s.withColor(color(m.tag())));
            String rank = Character.toUpperCase(m.rank().charAt(0)) + m.rank().substring(1);
            // only the tag takes the clan's color
            switch (Config.get().tagStyle) {
                case TAG_RANK -> line.append(tag).append(gray(" " + rank));
                case RANK_OF_TAG -> line.append(gray(rank + " of ")).append(tag);
                case BRACKETS -> line.append(gray("[")).append(tag).append(gray("] " + rank));
                case TAG -> line.append(tag);
            }
        }
        return line;
    }

    private static Text gray(String text) {
        return Text.literal(text).styled(s -> s.withColor(RANK_COLOR));
    }

    // your own clan and rank, "WOOL Leader". null when the roster doesnt have you
    public static String ownClan() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;
        List<Roster.Membership> clans = roster.of(mc.player.getNameForScoreboard());
        if (clans.isEmpty()) return null;
        Roster.Membership m = clans.get(0);
        return m.tag() + " " + Character.toUpperCase(m.rank().charAt(0)) + m.rank().substring(1);
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

    // the outline of a player who already glows, in the color of their clan. neutral players keep the server's color
    public static int glowColor(String player, int original) {
        if (!Config.get().glowColors || !Worlds.onMinewind() || player == null) return original;
        for (Roster.Membership m : roster.of(player)) {
            if (relations.of(m.tag()) == Relations.Kind.NEUTRAL) continue;
            return color(m.tag()).getRgb();
        }
        return original;
    }

    // chat: color the tag the line itself carries. the speaker can be nicked, so the tag is all we go by
    private static Text recolorChat(Text message) {
        try {
            if (NeverEnoughWind.data() == null || !Worlds.onMinewind()) return message;
            ChatMatch m = NeverEnoughWind.data().chat().match(message.getString()).orElse(null);
            if (m != null && m.category().equals("death")) return Config.get().killColors ? recolorNames(message) : message;
            if (m == null || !Config.get().chatColors || !m.id().equals("public_chat") || m.get("clan") == null) return message;
            if (relations.of(m.get("clan")) == Relations.Kind.NEUTRAL) return message;
            // only the tag. the name keeps its rank or custom color
            return recolor(message, m.get("clan") + ".", color(m.get("clan")), new boolean[]{false});
        } catch (RuntimeException e) {
            return message;
        }
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{2,16}");

    // kill and death lines use real names: every name of a clan you have a stance on takes that clan's color
    private static Text recolorNames(Text text) {
        MutableText copy;
        if (text.getContent() instanceof PlainTextContent plain) {
            copy = Text.empty().setStyle(text.getStyle());
            String s = plain.string();
            Matcher m = NAME.matcher(s);
            int from = 0;
            while (m.find()) {
                TextColor color = relationColor(m.group());
                if (color == null) continue;
                copy.append(Text.literal(s.substring(from, m.start())));
                copy.append(Text.literal(m.group()).styled(st -> st.withColor(color)));
                from = m.end();
            }
            copy.append(Text.literal(s.substring(from)));
        } else {
            copy = text.copyContentOnly().setStyle(text.getStyle());
        }
        for (Text sibling : text.getSiblings()) copy.append(recolorNames(sibling));
        return copy;
    }

    // null for players without a clan you have a stance on
    private static TextColor relationColor(String player) {
        if (NeverEnoughWind.data().chat().deathWord(player)) return null;
        for (Roster.Membership m : roster.of(player)) {
            if (relations.of(m.tag()) != Relations.Kind.NEUTRAL) return color(m.tag());
        }
        return null;
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
