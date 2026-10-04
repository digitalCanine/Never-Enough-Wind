package com.neverenoughwind.feature.hud;

import com.neverenoughwind.adapter.Chat;
import com.neverenoughwind.adapter.Scoreboards;
import com.neverenoughwind.state.Party;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

// online party members, the loot multiplier and the current rolls
public final class PartyWidget extends HudWidget {
    private static final int PAD = 3;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GOLD = 0xFFFFAA00, GREEN = 0xFF55FF55;

    private static final Party party = new Party();
    private static String lastPartyId;

    private PartyWidget() {
        super("party", "Party");
    }

    public static void register() {
        Chat.listen(m -> {
            if (!"party".equals(m.category())) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            party.onChat(m, System.currentTimeMillis(), mc.player == null ? null : mc.player.getNameForScoreboard());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> party.reset());
        new PartyWidget().add();
    }

    @Override
    protected Content content(TextRenderer tr, boolean sample) {
        // a different party, or none: the old rolls dont belong here
        String id = Scoreboards.partyId();
        if (id == null || !id.equals(lastPartyId)) {
            if (lastPartyId != null) party.clearRolls();
            lastPartyId = id;
        }
        List<String> members = new ArrayList<>(Scoreboards.partyMembers());
        List<String> rollTexts = new ArrayList<>();
        String bonus, leader;
        if (members.isEmpty()) {
            if (!sample) return null;
            members = List.of("Steve", "Alex", "Notch");
            rollTexts = List.of("Need 87", "Greed 42", "Pass");
            bonus = "20%";
            leader = "Steve";
        } else {
            // someone who rolled and isnt on the roster anymore still gets a line
            for (String roller : party.rolls().keySet()) {
                if (!members.contains(roller)) members.add(roller);
            }
            for (String name : members) {
                Party.Roll roll = party.rolls().get(name);
                rollTexts.add(roll == null ? null : roll.choice().equals("pass") ? "Pass" : (roll.choice().equals("need") ? "Need " : "Greed ") + roll.value());
            }
            bonus = party.multiplier() == null ? null : party.multiplier() + "%";
            leader = party.leader();
        }
        String header = "Party";
        int width = tr.getWidth(header) + (bonus == null ? 0 : 6 + tr.getWidth(bonus));
        for (int i = 0; i < members.size(); i++) {
            String text = rollTexts.get(i);
            width = Math.max(width, tr.getWidth(members.get(i)) + (text == null ? 0 : 8 + tr.getWidth(text)));
        }
        int line = tr.fontHeight + 1;
        int w = PAD + width + PAD, h = PAD + line * (members.size() + 1) + PAD - 1;
        List<String> names = members, texts = rollTexts;
        return new Content(w, h, ctx -> {
            int ty = PAD;
            ctx.drawTextWithShadow(tr, header, PAD, ty, GOLD);
            if (bonus != null) ctx.drawTextWithShadow(tr, bonus, w - PAD - tr.getWidth(bonus), ty, WHITE);
            for (int i = 0; i < names.size(); i++) {
                ty += line;
                String name = names.get(i), text = texts.get(i);
                boolean leading = name.equals(leader);
                ctx.drawTextWithShadow(tr, name, PAD, ty, leading ? GREEN : WHITE);
                if (text != null) ctx.drawTextWithShadow(tr, text, w - PAD - tr.getWidth(text), ty, leading ? GREEN : GRAY);
            }
        });
    }

    // right edge, halfway down
    @Override
    protected int[] home(int sw, int sh, int w, int h) {
        return new int[]{sw - w - 4, (sh - h) / 2};
    }
}
