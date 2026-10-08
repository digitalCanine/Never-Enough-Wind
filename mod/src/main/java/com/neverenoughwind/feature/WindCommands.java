package com.neverenoughwind.feature;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.config.EnderColorsScreen;
import com.neverenoughwind.config.LayoutScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.neverenoughwind.dao.PriceDao;
import com.neverenoughwind.state.DailyTimers;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

// /wind commands. answered by the mod, nothing goes to the server except /wind clans after its confirmation
public final class WindCommands {
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private WindCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("wind")
                .then(literal("calc")
                        .then(argument("amount", StringArgumentType.word())
                                .then(argument("unit", StringArgumentType.word()).executes(c -> {
                                    c.getSource().sendFeedback(calc(StringArgumentType.getString(c, "amount"), StringArgumentType.getString(c, "unit")));
                                    return 1;
                                }))))
                .then(literal("sharp")
                        .then(argument("from", IntegerArgumentType.integer(0, 30))
                                .then(argument("to", IntegerArgumentType.integer(1, 30)).executes(c -> {
                                    c.getSource().sendFeedback(sharp(IntegerArgumentType.getInteger(c, "from"), IntegerArgumentType.getInteger(c, "to")));
                                    return 1;
                                }))))
                .then(literal("clans")
                        .executes(c -> {
                            c.getSource().sendFeedback(ClanRefresh.request());
                            return 1;
                        })
                        .then(literal("stop").executes(c -> {
                            c.getSource().sendFeedback(ClanRefresh.stop(null));
                            return 1;
                        })))
                .then(literal("reload").executes(c -> {
                    Clans.reload();
                    c.getSource().sendFeedback(Text.literal("Reloaded your settings and clan file.").formatted(Formatting.GREEN));
                    return 1;
                }))
                .then(dailies())
                .then(literal("browse")
                        .executes(c -> browse(c.getSource().getClient(), null))
                        .then(argument("search", StringArgumentType.greedyString())
                                .executes(c -> browse(c.getSource().getClient(), StringArgumentType.getString(c, "search")))))
                .then(literal("echest").executes(c -> {
                    MinecraftClient mc = c.getSource().getClient();
                    mc.send(() -> mc.setScreen(new EnderColorsScreen(null)));
                    return 1;
                }))
                .then(literal("config").executes(c -> {
                    // a tick later, the chat screen is still closing right now
                    MinecraftClient mc = c.getSource().getClient();
                    mc.send(() -> mc.setScreen(LayoutScreen.settings(null)));
                    if (!LayoutScreen.hasMenu()) {
                        c.getSource().sendFeedback(Text.literal("Install the mod YACL for the full settings menu. This is the widget layout.").formatted(Formatting.GRAY));
                    }
                    return 1;
                }))));
    }

    // a tick later, the chat screen is still closing right now
    private static int browse(MinecraftClient mc, String search) {
        mc.send(() -> Browser.open(search));
        return 1;
    }

    // /wind dailies, and /wind dailies <which> done|reset to correct one by hand
    private static LiteralArgumentBuilder<FabricClientCommandSource> dailies() {
        LiteralArgumentBuilder<FabricClientCommandSource> node = literal("dailies").executes(c -> {
            c.getSource().sendFeedback(Dailies.report());
            return 1;
        });
        for (DailyTimers.Kind kind : DailyTimers.Kind.values()) {
            node.then(literal(kind.key())
                    .then(literal("done").executes(c -> {
                        c.getSource().sendFeedback(Dailies.set(kind, true));
                        return 1;
                    }))
                    .then(literal("reset").executes(c -> {
                        c.getSource().sendFeedback(Dailies.set(kind, false));
                        return 1;
                    })));
        }
        return node;
    }

    // "9sh" "d" -> "9sh = 15,552d"
    public static Text calc(String amount, String unit) {
        if (NeverEnoughWind.data() == null) return Text.literal("The price data did not load.").formatted(Formatting.RED);
        PriceDao prices = NeverEnoughWind.data().prices();
        Optional<Double> result = prices.convert(amount, unit);
        if (result.isEmpty()) {
            return Text.literal("Write it like /wind calc 9sh d. Units: " + String.join(", ", prices.unitNames().stream().sorted().toList()) + ".")
                    .formatted(Formatting.GRAY);
        }
        return Text.literal(amount.toLowerCase(Locale.ROOT) + " = ").formatted(Formatting.GRAY)
                .append(Text.literal(NUMBER.format(result.get()) + unit.toLowerCase(Locale.ROOT)).formatted(Formatting.WHITE));
    }

    public static Text sharp(int from, int to) {
        if (NeverEnoughWind.data() == null) return Text.literal("The price data did not load.").formatted(Formatting.RED);
        if (to <= from) return Text.literal("The second level has to be higher than the first.").formatted(Formatting.GRAY);
        int cost = NeverEnoughWind.data().prices().sharpenCost(from, to);
        if (cost < 0) return Text.literal("The sharpening table only goes up to 30.").formatted(Formatting.GRAY);
        return Text.literal("Sharpness " + from + " to " + to + " costs ").formatted(Formatting.GRAY)
                .append(Text.literal(NUMBER.format(cost) + " dragon eggs").formatted(Formatting.WHITE))
                .append(Text.literal(cost >= 64 ? " (" + PriceDao.text(cost) + ")." : ".").formatted(Formatting.GRAY));
    }
}
