package com.neverenoughwind.feature;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.parse.ItemInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// the player's own colors behind ender chest slots, one set per ender tab.
// the chest is one window: five rows of the open tab, then a row of glass panes that are the tabs
public final class EnderChest {
    public static final int STORAGE = 45, TABS_END = 54, COLUMNS = 9;
    private static final String TITLE = "Ender Chest", LOCKED = "Ender Tab (Locked)";
    // every tab pane carries its place as "row-column", that stays when the tab is renamed
    private static final Pattern ID = Pattern.compile("^\\d+-\\d+$");
    private static final int ALPHA = 0x70000000, WRONG = 0xFFFF5555;
    // what a color can be told to hold: id -> how the editor names it. the ids are the mod's own item types, plus eggs, maps and soulstones
    public static final Map<String, String> KINDS = kinds();

    private static Map<String, String> kinds() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("eggs", "Dragon Eggs");
        m.put("keys", "Keys");
        m.put("essence", "Essence Books");
        m.put("infinite", "Infinity Blocks");
        m.put("gear", "Gear");
        m.put("currency", "Currencies");
        m.put("rarity", "Rarities");
        m.put("magic", "Spellbooks");
        m.put("glamour", "Auras and Hats");
        m.put("maps", "Maps");
        m.put("soulstones", "Soulstones");
        return m;
    }

    private static ScreenHandler seen;
    private static int seenAt = -1;
    private static String open;

    private EnderChest() {}

    // id of the tab that is open in this window, null when it isnt the ender chest
    public static String openTab(Screen screen, ScreenHandler handler) {
        int now = MinecraftClient.getInstance().inGameHud.getTicks();
        if (handler == seen && now == seenAt) return open;
        seen = handler;
        seenAt = now;
        open = null;
        if (!Worlds.onMinewind() || !(handler instanceof GenericContainerScreenHandler chest) || chest.getRows() != 6) return null;
        if (!TITLE.equals(screen.getTitle().getString())) return null;

        Config config = Config.get();
        boolean learned = false;
        for (int i = STORAGE; i < TABS_END && i < handler.slots.size(); i++) {
            ItemStack pane = handler.slots.get(i).getStack();
            String id = id(pane);
            if (id == null) continue;
            // the lime pane is the tab being shown
            if (pane.isOf(Items.LIME_STAINED_GLASS_PANE)) open = id;
            String name = pane.getName().getString();
            if (!LOCKED.equals(name) && !name.equals(config.enderTabs.get(id))) {
                config.enderTabs.put(id, name);
                learned = true;
            }
        }
        if (learned) Config.save();
        return open;
    }

    private static String id(ItemStack pane) {
        LoreComponent lore = pane.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) return null;
        String first = lore.lines().get(0).getString().trim();
        return ID.matcher(first).matches() ? first : null;
    }

    // behind the item, so it reads as the slot's own color
    public static void drawBehind(DrawContext ctx, Screen screen, ScreenHandler handler, Slot slot) {
        if (!Config.get().enderColors || slot.id >= STORAGE || !(handler instanceof GenericContainerScreenHandler)) return;
        String tab = openTab(screen, handler);
        if (tab == null) return;
        Map<String, Integer> colors = Config.get().enderSlots.get(tab);
        Integer rgb = colors == null ? null : colors.get(String.valueOf(slot.id));
        if (rgb != null) ctx.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ALPHA | rgb);
    }

    public static String key(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }

    // the rule of the color a slot of this tab was painted, null when it has no color or the color means nothing
    private static Config.EnderRule rule(String tab, int slot) {
        Map<String, Integer> colors = Config.get().enderSlots.get(tab);
        Integer rgb = colors == null ? null : colors.get(String.valueOf(slot));
        Config.EnderRule rule = rgb == null ? null : Config.get().enderRules.get(key(rgb));
        return rule == null || (blank(rule.kind) && blank(rule.name)) ? null : rule;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isKind(ItemStack stack, String kind) {
        if (stack.isOf(Items.DRAGON_EGG)) return kind.equals("eggs");
        // map art is a filled map like any other, the picture isnt in the item
        if (stack.isOf(Items.FILLED_MAP)) return kind.equals("maps");
        ItemInfo info = com.neverenoughwind.adapter.Items.info(stack);
        String type = info.category();
        // what players call soulstones: aura stones, each bound to the player it came from
        if (kind.equals("soulstones")) return "Aura Stone".equals(info.detail());
        // gear = anything that wears out (weapons, tools, armor, elytra, shields, rods), plain or not,
        // and what minewind makes gear out of other things: spell rods, wand flowers, heads
        if (kind.equals("gear")) return "gear".equals(type) || stack.contains(DataComponentTypes.MAX_DAMAGE);
        return kind.equals(type);
    }

    // a shulker box counts as what it holds: one key inside makes it a box of keys
    public static boolean matches(Config.EnderRule rule, ItemStack stack) {
        if (!blank(rule.kind)) {
            boolean kind = isKind(stack, rule.kind);
            ContainerComponent box = stack.get(DataComponentTypes.CONTAINER);
            if (!kind && box != null) {
                for (ItemStack inside : box.iterateNonEmpty()) {
                    if (isKind(inside, rule.kind)) {
                        kind = true;
                        break;
                    }
                }
            }
            if (!kind) return false;
        }
        if (!blank(rule.name)) {
            String name = stack.getName().getString();
            try {
                return Pattern.compile(rule.name, Pattern.CASE_INSENSITIVE).matcher(name).find();
            } catch (PatternSyntaxException e) {
                // not a pattern, so plain text
                return name.toLowerCase(Locale.ROOT).contains(rule.name.toLowerCase(Locale.ROOT));
            }
        }
        return true;
    }

    // over the item: a small red corner when the slot holds something its color wasnt meant for
    public static void drawOver(DrawContext ctx, Screen screen, ScreenHandler handler, Slot slot) {
        if (!Config.get().enderColors || slot.id >= STORAGE || !slot.hasStack() || !(handler instanceof GenericContainerScreenHandler)) return;
        String tab = openTab(screen, handler);
        if (tab == null) return;
        Config.EnderRule rule = rule(tab, slot.id);
        if (rule == null || matches(rule, slot.getStack())) return;
        for (int i = 0; i < 4; i++) ctx.fill(slot.x + 12 + i, slot.y, slot.x + 16, slot.y + i + 1, WRONG);
    }

    // where an item from the player's inventory may go on the open tab, best first: onto a stack of the same thing,
    // then a free slot of its color, then a free slot without a rule. never a slot whose color is for something else.
    // null = sorting has no say here and the game should do its own thing
    public static List<Integer> slotsFor(Screen screen, ScreenHandler handler, ItemStack stack) {
        if (!Config.get().enderSort || !Config.get().enderColors || stack.isEmpty()) return null;
        String tab = openTab(screen, handler);
        if (tab == null) return null;
        List<Integer> same = new ArrayList<>(), colored = new ArrayList<>(), plain = new ArrayList<>();
        boolean anyRule = false;
        for (int i = 0; i < STORAGE && i < handler.slots.size(); i++) {
            Config.EnderRule rule = rule(tab, i);
            if (rule != null) anyRule = true;
            if (rule != null && !matches(rule, stack)) continue;
            ItemStack there = handler.slots.get(i).getStack();
            if (there.isEmpty()) (rule != null ? colored : plain).add(i);
            else if (there.getCount() < there.getMaxCount() && ItemStack.areItemsAndComponentsEqual(there, stack)) same.add(i);
        }
        // a tab without a single rule is just an ender chest
        if (!anyRule) return null;
        same.addAll(colored);
        same.addAll(plain);
        return same;
    }

    // sorts "1-2" before "1-10" and "2-1"
    public static int order(String id) {
        String[] parts = id.split("-");
        try {
            return Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
        } catch (RuntimeException e) {
            return Integer.MAX_VALUE;
        }
    }
}
