package com.neverenoughwind.config;

import com.neverenoughwind.feature.Clans;
import com.neverenoughwind.feature.hud.HudWidget;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.ListOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.ColorControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

// the settings menu, built with YACL. only touched when YACL is installed
public final class ConfigScreen {
    private ConfigScreen() {}

    public static Screen create(Screen parent) {
        Config c = Config.get();
        Config d = new Config();

        ConfigCategory.Builder items = ConfigCategory.createBuilder().name(Text.literal("Items"))
                .option(Option.<Config.PanelMode>createBuilder()
                        .name(Text.literal("Side Panel"))
                        .description(about("The panel next to an item's tooltip with what its essences do, its true damage and its worth."))
                        .binding(d.sidePanel, () -> c.sidePanel, v -> c.sidePanel = v)
                        .controller(o -> EnumControllerBuilder.create(o).enumClass(Config.PanelMode.class).formatValue(v -> Text.literal(v.label)))
                        .build())
                .option(toggle("Original Item Names", "Renamed auction gear shows the name color it had when it was made.",
                        d.originalNames, () -> c.originalNames, v -> c.originalNames = v))
                .option(toggle("Item Borders", "A thin colored frame around essences, keys, currencies and other Minewind items.",
                        d.borders, () -> c.borders, v -> c.borders = v));
        OptionGroup.Builder borderColors = OptionGroup.createBuilder().name(Text.literal("Border Colors")).collapsed(true);
        Config.defaultBorderColors().forEach((type, def) -> borderColors.option(color(
                Character.toUpperCase(type.charAt(0)) + type.substring(1), null, def,
                () -> c.borderColors.getOrDefault(type, def), v -> c.borderColors.put(type, v))));
        items.group(borderColors.build());

        ConfigCategory.Builder hud = ConfigCategory.createBuilder().name(Text.literal("HUD"))
                .option(ButtonOption.createBuilder()
                        .name(Text.literal("Widget Layout"))
                        .description(about("Move, resize and turn off everything the mod draws on the screen."))
                        .text(Text.literal("Open"))
                        .action((screen, option) -> MinecraftClient.getInstance().setScreen(new LayoutScreen(screen)))
                        .build());
        for (HudWidget w : HudWidget.all()) {
            hud.group(OptionGroup.createBuilder().name(Text.literal(w.name))
                    .option(toggle("Show", null, true, () -> w.config().enabled, v -> w.config().enabled = v))
                    .option(Option.<Integer>createBuilder()
                            .name(Text.literal("Size"))
                            .binding(100, () -> Math.round(w.config().scale * 100), v -> w.config().scale = v / 100f)
                            .controller(o -> IntegerSliderControllerBuilder.create(o)
                                    .range(Math.round(LayoutScreen.MIN_SCALE * 100), Math.round(LayoutScreen.MAX_SCALE * 100)).step(5)
                                    .formatValue(v -> Text.literal(v + "%")))
                            .build())
                    .build());
        }
        hud.option(toggle("Event Reminders", "A notification when an event is 60, 30, 15, 5 and 1 minute away.",
                        d.eventReminders, () -> c.eventReminders, v -> c.eventReminders = v))
                .option(toggle("Daily Reminders", "A notification when your daily boss key, /daily, /weekly or daily wild key can be done again. /wind dailies lists them all.",
                        d.dailyReminders, () -> c.dailyReminders, v -> c.dailyReminders = v));
        hud.option(toggle("Subserver Name", "Show the name next to the subserver icon.",
                        d.subserverName, () -> c.subserverName, v -> c.subserverName = v))
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Subserver Icon Size"))
                        .description(about("How big the icon is next to the name. The widget's own size scales both together."))
                        .binding(d.subserverIconSize, () -> c.subserverIconSize, v -> c.subserverIconSize = v)
                        .controller(o -> IntegerSliderControllerBuilder.create(o).range(1, 3).step(1).formatValue(v -> Text.literal(v + "x")))
                        .build())
                .option(Option.<Integer>createBuilder()
                        .name(Text.literal("Notification Time"))
                        .description(about("How long a notification stays on screen."))
                        .binding(d.reminderSeconds, () -> c.reminderSeconds, v -> c.reminderSeconds = v)
                        .controller(o -> IntegerSliderControllerBuilder.create(o).range(3, 30).step(1).formatValue(v -> Text.literal(v + " s")))
                        .build());

        ConfigCategory.Builder clans = ConfigCategory.createBuilder().name(Text.literal("Clans"))
                .option(toggle("Clan Above Names", "Show a player's clan and rank above their name.",
                        d.nametagTags, () -> c.nametagTags, v -> c.nametagTags = v))
                .option(toggle("Clan Colors in Chat", "Color the clan tags in chat by how you stand with that clan.",
                        d.chatColors, () -> c.chatColors, v -> c.chatColors = v))
                .option(toggle("Color Names Above Heads", "The name above a player's head takes their clan's color, so you can tell friend from enemy at a glance. Names in chat keep their own colors.",
                        d.nameColors, () -> c.nameColors, v -> c.nameColors = v))
                .group(OptionGroup.createBuilder().name(Text.literal("Colors"))
                        .option(color("Your Clans", null, d.ownColor, () -> c.ownColor, v -> c.ownColor = v))
                        .option(color("Allies", null, d.allyColor, () -> c.allyColor, v -> c.allyColor = v))
                        .option(color("Enemies", null, d.enemyColor, () -> c.enemyColor, v -> c.enemyColor = v))
                        .option(color("Trade Banned", null, d.tradebannedColor, () -> c.tradebannedColor, v -> c.tradebannedColor = v))
                        .option(color("Everyone Else", null, d.neutralColor, () -> c.neutralColor, v -> c.neutralColor = v))
                        .build())
                .group(tags("Your Clans", "The clan whose chat you can read always counts as yours. Add more tags here.", () -> c.own, v -> c.own = v))
                .group(tags("Allies", null, () -> c.ally, v -> c.ally = v))
                .group(tags("Enemies", null, () -> c.enemy, v -> c.enemy = v))
                .group(tags("Trade Banned", "Clans you do not trade with.", () -> c.tradebanned, v -> c.tradebanned = v));

        ConfigCategory.Builder other = ConfigCategory.createBuilder().name(Text.literal("Other"))
                .option(toggle("Inventory View", "Looking into another player's inventory shows it laid out like a real inventory instead of a chest, without your own items underneath.",
                        d.inventoryView, () -> c.inventoryView, v -> c.inventoryView = v))
                .option(toggle("Tidy Join Dates", "Profiles of players who joined before the merge say \"pre-merge\" instead of twenty thousand days.",
                        d.fixJoinDate, () -> c.fixJoinDate, v -> c.fixJoinDate = v))
                .option(toggle("Debug Info", "For testers. Extra rows in the side panel showing how an item was read, worlds the mod does not know, and the Save Item Info key (set it under Controls): hover an item and press it to save and copy what the item looks like.",
                        d.debug, () -> c.debug, v -> c.debug = v));

        return YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Never Enough Wind"))
                .category(items.build())
                .category(hud.build())
                .category(clans.build())
                .category(other.build())
                .save(() -> {
                    Config.save();
                    Clans.applyConfig();
                })
                .build()
                .generateScreen(parent);
    }

    private static OptionDescription about(String text) {
        return text == null ? OptionDescription.EMPTY : OptionDescription.of(Text.literal(text));
    }

    private static Option<Boolean> toggle(String name, String about, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder().name(Text.literal(name)).description(about(about))
                .binding(def, get, set).controller(TickBoxControllerBuilder::create).build();
    }

    // the config keeps colors as plain rgb numbers
    private static Option<Color> color(String name, String about, int def, Supplier<Integer> get, Consumer<Integer> set) {
        return Option.<Color>createBuilder().name(Text.literal(name)).description(about(about))
                .binding(new Color(def), () -> new Color(get.get()), v -> set.accept(v.getRGB() & 0xFFFFFF))
                .controller(ColorControllerBuilder::create).build();
    }

    private static ListOption<String> tags(String name, String about, Supplier<List<String>> get, Consumer<List<String>> set) {
        return ListOption.<String>createBuilder().name(Text.literal(name)).description(about(about))
                .binding(List.of(), get, v -> set.accept(new ArrayList<>(v)))
                .controller(StringControllerBuilder::create).initial("").build();
    }
}
