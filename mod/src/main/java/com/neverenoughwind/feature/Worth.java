package com.neverenoughwind.feature;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Items;
import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.dao.PriceDao;
import com.neverenoughwind.model.PriceTier;
import com.neverenoughwind.parse.EssenceEntry;
import com.neverenoughwind.parse.ItemInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Optional;

// what an open container holds, in dragon eggs: eggs, embers, keys, essence books, infinity blocks and the essences on gear.
// things without a known price count as nothing
public final class Worth {
    private static final int EVERY = 10;

    private static ScreenHandler counted;
    private static int countedAt;
    private static double min, max;

    private Worth() {}

    // right, bottom = that corner of the container's window
    public static void draw(DrawContext ctx, ScreenHandler handler, int right, int bottom) {
        if (!Config.get().worthTotal || NeverEnoughWind.data() == null || !Worlds.onMinewind()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int now = mc.inGameHud.getTicks();
        // shulkers make this a lot of items, a couple of times a second is plenty
        if (handler != counted || now - countedAt >= EVERY) {
            counted = handler;
            countedAt = now;
            min = max = 0;
            for (Slot slot : handler.slots) {
                if (slot.inventory instanceof PlayerInventory || !slot.hasStack()) continue;
                add(slot.getStack(), 1, 0);
            }
        }
        if (max < 1) return;
        int lo = (int) Math.round(min), hi = (int) Math.round(max);
        // two amounts can round to the same text ("1.2sh - 1.2sh"), then one is enough
        String low = PriceDao.text(lo), high = PriceDao.text(hi);
        String amount = lo < 1 || low.equals(high) ? high : low + " - " + high;
        Text line = Text.literal("Worth ").formatted(Formatting.GRAY).append(Text.literal(amount).formatted(Formatting.WHITE));
        TextRenderer tr = mc.textRenderer;
        ctx.drawTextWithShadow(tr, line, right - tr.getWidth(line), bottom + 2, 0xFFFFFFFF);
    }

    private static void add(ItemStack stack, int times, int depth) {
        if (stack.isEmpty()) return;
        int count = stack.getCount() * times;
        if (stack.isOf(net.minecraft.item.Items.DRAGON_EGG)) {
            min += count;
            max += count;
            return;
        }
        ContainerComponent box = stack.get(DataComponentTypes.CONTAINER);
        if (box != null && depth < 2) {
            for (ItemStack inside : box.iterateNonEmpty()) add(inside, count, depth + 1);
            return;
        }
        ItemInfo info = Items.info(stack);
        if (info.isEmpty()) return;
        PriceDao prices = NeverEnoughWind.data().prices();
        switch (info.category()) {
            case "currency" -> {
                // four embers trade for one egg. the other currencies have no going rate in eggs
                if ("Ember".equals(info.detail())) add(0.25, 0.25, count);
            }
            case "keys" -> prices.key(info.detail()).ifPresent(k -> {
                if (k.minDeggs() != null && k.maxDeggs() != null) add(k.minDeggs(), k.maxDeggs(), count);
            });
            case "infinite" -> prices.infinityDeggs(Registries.ITEM.getId(stack.getItem()).toString())
                    .ifPresent(range -> add(range[0], range[1], count));
            case "essence", "gear" -> {
                for (EssenceEntry e : info.essences()) {
                    if (e.essence() == null) continue;
                    Optional<PriceTier> tier = prices.essencePrice(e.essence(), Math.max(1, e.level()));
                    if (tier.isPresent() && tier.get().hasNumber()) add(tier.get().minDeggs(), tier.get().maxDeggs(), count);
                }
            }
            default -> {
            }
        }
    }

    private static void add(double lo, double hi, int count) {
        min += lo * count;
        max += hi * count;
    }
}
