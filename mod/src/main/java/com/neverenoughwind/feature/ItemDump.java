package com.neverenoughwind.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Items;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.parse.EssenceEntry;
import com.neverenoughwind.parse.ItemInfo;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// for testers: one key saves what an item looks like, so items the data doesnt know can be sent in.
// only the item itself, only with debug info on, and nothing leaves the computer on its own
public final class ItemDump {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    // the item under the mouse in an open inventory, kept up to date by the screen mixin
    public static ItemStack hovered;
    private static KeyBinding key;

    private ItemDump() {}

    public static void register() {
        // no key until the tester picks one
        key = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.neverenoughwind.dump_item",
                InputUtil.Type.KEYSYM, InputUtil.UNKNOWN_KEY.getCode(), "key.categories.neverenoughwind"));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.wasPressed()) {
                if (mc.currentScreen == null && mc.player != null) dump(mc.player.getMainHandStack());
            }
        });
        // keybinds dont fire while an inventory is open, so listen on the screen itself
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen<?>)) return;
            ScreenKeyboardEvents.afterKeyPress(screen).register((s, code, scancode, modifiers) -> {
                if (key.matchesKey(code, scancode)) dump(hovered);
            });
        });
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("neverenoughwind").resolve("item_dumps.jsonl");
    }

    private static void dump(ItemStack stack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Config.get().debug || mc.player == null || stack == null || stack.isEmpty()) return;
        try {
            String line = GSON.toJson(describe(stack));
            Path file = file();
            Files.createDirectories(file.getParent());
            Files.write(file, (line + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            mc.keyboard.setClipboard(line);
            mc.player.sendMessage(Text.literal("Saved and copied: ").formatted(Formatting.GRAY)
                    .append(stack.getName())
                    .append(Text.literal(" (open file)").formatted(Formatting.DARK_GRAY)
                            .styled(s -> s.withClickEvent(new ClickEvent.OpenFile(file.toAbsolutePath().toString())))), false);
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not save the item: {}", e.toString());
            mc.player.sendMessage(Text.literal("Could not save that item.").formatted(Formatting.RED), false);
        }
    }

    private static JsonObject describe(ItemStack stack) {
        JsonObject o = new JsonObject();
        o.addProperty("time", LocalDateTime.now().format(TIME));
        o.addProperty("mod", FabricLoader.getInstance().getModContainer("neverenoughwind")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"));
        o.addProperty("item", Registries.ITEM.getId(stack.getItem()).toString());
        o.addProperty("count", stack.getCount());
        o.add("name", text(stack.getName()));
        o.addProperty("custom_name", stack.get(DataComponentTypes.CUSTOM_NAME) != null);
        JsonArray lore = new JsonArray();
        LoreComponent lines = stack.get(DataComponentTypes.LORE);
        if (lines != null) {
            for (Text line : lines.lines()) {
                // the soulbound line names a player, that is nobody's business
                if (line.getString().trim().startsWith("Soulbound")) continue;
                lore.add(text(line));
            }
        }
        o.add("lore", lore);
        JsonObject enchants = new JsonObject();
        stack.getEnchantments().getEnchantmentEntries().forEach(e -> enchants.addProperty(e.getKey().getIdAsString(), e.getIntValue()));
        o.add("enchants", enchants);

        // what the mod made of it, so a wrong reading is visible right away
        ItemInfo info = Items.info(stack);
        JsonObject read = new JsonObject();
        read.addProperty("category", info.category());
        read.addProperty("detail", info.detail());
        read.addProperty("auction", info.auction() == null ? null : info.auction().name());
        read.addProperty("renamed", info.renamed());
        JsonArray unknown = new JsonArray();
        for (EssenceEntry e : info.essences()) {
            if (e.essence() == null) unknown.add(e.name());
        }
        read.addProperty("essences", info.essences().size());
        read.add("unknown_essences", unknown);
        o.add("read", read);
        return o;
    }

    // plain for reading, json for the colors
    private static JsonObject text(Text text) {
        JsonObject o = new JsonObject();
        o.addProperty("plain", text.getString());
        JsonElement json = null;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            var ops = mc.getNetworkHandler() == null ? JsonOps.INSTANCE : mc.getNetworkHandler().getRegistryManager().getOps(JsonOps.INSTANCE);
            json = TextCodecs.CODEC.encodeStart(ops, text).getOrThrow();
        } catch (RuntimeException e) {
            // plain is still there
        }
        o.add("json", json);
        return o;
    }
}
