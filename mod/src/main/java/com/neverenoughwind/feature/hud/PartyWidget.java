package com.neverenoughwind.feature.hud;

import com.neverenoughwind.adapter.Chat;
import com.neverenoughwind.adapter.Scoreboards;
import com.neverenoughwind.state.Party;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

// online party members, the loot multiplier and the current rolls
public final class PartyWidget {
    // placeholder until the settings menu exists: right edge, halfway down
    public static HudPos pos = new HudPos(1f, 0.5f, -4, 0);

    private static final int PAD = 3;
    private static final int PLATE = 0x90000000;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFFAAAAAA, GOLD = 0xFFFFAA00, GREEN = 0xFF55FF55;

    private static final Party party = new Party();
    private static String lastPartyId;

    private PartyWidget() {}

    public static void register() {
        Chat.listen(m -> {
            if (!"party".equals(m.category())) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            party.onChat(m, System.currentTimeMillis(), mc.player == null ? null : mc.player.getNameForScoreboard());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> party.reset());
        HudElementRegistry.addLast(Identifier.of("neverenoughwind", "party"), (ctx, tick) -> render(ctx));
    }

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.world == null) return;
        // a different party, or none: the old rolls dont belong here
        String id = Scoreboards.partyId();
        if (id == null || !id.equals(lastPartyId)) {
            if (lastPartyId != null) party.clearRolls();
            lastPartyId = id;
        }
        List<String> members = new ArrayList<>(Scoreboards.partyMembers());
        if (members.isEmpty()) return;
        // someone who rolled and isnt on the roster anymore still gets a line
        for (String roller : party.rolls().keySet()) {
            if (!members.contains(roller)) members.add(roller);
        }
        TextRenderer tr = mc.textRenderer;
        String header = "Party";
        String bonus = party.multiplier() == null ? null : party.multiplier() + "%";
        String leader = party.leader();

        int width = tr.getWidth(header) + (bonus == null ? 0 : 6 + tr.getWidth(bonus));
        List<String> rollTexts = new ArrayList<>();
        for (String name : members) {
            Party.Roll roll = party.rolls().get(name);
            String text = roll == null ? null : roll.choice().equals("pass") ? "pass" : roll.choice() + " " + roll.value();
            rollTexts.add(text);
            width = Math.max(width, tr.getWidth(name) + (text == null ? 0 : 8 + tr.getWidth(text)));
        }
        int line = tr.fontHeight + 1;
        int w = PAD + width + PAD, h = PAD + line * (members.size() + 1) + PAD - 1;
        int x = pos.x(ctx.getScaledWindowWidth(), w), y = pos.y(ctx.getScaledWindowHeight(), h);

        ctx.fill(x, y, x + w, y + h, PLATE);
        int ty = y + PAD;
        ctx.drawTextWithShadow(tr, header, x + PAD, ty, GOLD);
        if (bonus != null) ctx.drawTextWithShadow(tr, bonus, x + w - PAD - tr.getWidth(bonus), ty, WHITE);
        for (int i = 0; i < members.size(); i++) {
            ty += line;
            String name = members.get(i), text = rollTexts.get(i);
            boolean leading = name.equals(leader);
            ctx.drawTextWithShadow(tr, name, x + PAD, ty, leading ? GREEN : WHITE);
            if (text != null) ctx.drawTextWithShadow(tr, text, x + w - PAD - tr.getWidth(text), ty, leading ? GREEN : GRAY);
        }
    }
}
