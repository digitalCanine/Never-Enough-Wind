package com.neverenoughwind.adapter;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.parse.ItemInfo;
import com.neverenoughwind.parse.ItemSnapshot;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// the only place that reads minecraft item objects
public final class Items {
    // keyed by the stack object itself. damage looks at several items per frame
    private static final Map<ItemStack, ItemInfo> cache = new IdentityHashMap<>();

    private Items() {}

    // tooltips ask for the same stacks every frame, so keep the answers
    public static ItemInfo info(ItemStack stack) {
        if (stack == null || stack.isEmpty() || NeverEnoughWind.parser() == null) return ItemInfo.NONE;
        ItemInfo known = cache.get(stack);
        if (known != null) return known;
        if (cache.size() > 256) cache.clear();
        ItemInfo info = NeverEnoughWind.parser().parse(snapshot(stack));
        cache.put(stack, info);
        return info;
    }

    public static ItemSnapshot snapshot(ItemStack stack) {
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
        List<String> lore = new ArrayList<>();
        LoreComponent loreComponent = stack.get(DataComponentTypes.LORE);
        if (loreComponent != null) {
            for (Text line : loreComponent.lines()) lore.add(line.getString());
        }
        Map<String, Integer> enchants = new HashMap<>();
        stack.getEnchantments().getEnchantmentEntries()
                .forEach(e -> enchants.put(e.getKey().getIdAsString(), e.getIntValue()));
        return new ItemSnapshot(id, stack.getName().getString(), custom != null && hasColor(custom), lore, enchants);
    }

    // server names always carry a color, anvil renames never do
    private static boolean hasColor(Text text) {
        return text.visit((style, part) -> style.getColor() != null ? Optional.of(true) : Optional.empty(), Style.EMPTY).isPresent();
    }
}
