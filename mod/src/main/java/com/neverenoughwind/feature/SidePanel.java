package com.neverenoughwind.feature;

import com.neverenoughwind.NeverEnoughWind;
import com.neverenoughwind.adapter.Items;
import com.neverenoughwind.adapter.Loadout;
import com.neverenoughwind.dao.PriceDao;
import com.neverenoughwind.model.PriceTier;
import com.neverenoughwind.parse.Damage;
import com.neverenoughwind.parse.DamageCalc;
import com.neverenoughwind.parse.EssenceEntry;
import com.neverenoughwind.parse.ItemInfo;
import com.neverenoughwind.parse.Roman;
import com.neverenoughwind.parse.Soul;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector2i;
import org.joml.Vector2ic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// our own box next to the vanilla tooltip, so the item's tooltip stays as it is
public final class SidePanel {
    private static final int MAX_WIDTH = 190;
    private static final int GAP = 10;

    // one line of the box. right is pushed to the right edge, null for a normal line
    record Row(Text left, Text right) {
        static Row of(Text left) {
            return new Row(left, null);
        }
    }

    // set by the inventory screen each frame, used up when the tooltip gets drawn
    public static ItemStack hovered;
    private static boolean drawing;

    private SidePanel() {}

    // called right after the vanilla tooltip was drawn, with the same numbers it used
    public static void afterTooltip(DrawContext ctx, TextRenderer tr, List<TooltipComponent> vanilla, int x, int y, TooltipPositioner positioner) {
        if (drawing || hovered == null) return;
        ItemStack stack = hovered;
        hovered = null;
        try {
            List<Row> rows = rows(Items.info(stack), Loadout.damage(stack));
            if (rows.isEmpty()) return;

            int w = 0, h = vanilla.size() == 1 ? -2 : 0;
            for (TooltipComponent c : vanilla) {
                w = Math.max(w, c.getWidth(tr));
                h += c.getHeight(tr);
            }
            int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
            Vector2ic at = positioner.getPosition(sw, sh, x, y, w, h);

            List<TooltipComponent> ours = components(tr, rows);
            int ow = 0, oh = ours.size() == 1 ? -2 : 0;
            for (TooltipComponent c : ours) {
                ow = Math.max(ow, c.getWidth(tr));
                oh += c.getHeight(tr);
            }

            // right of the tooltip if it fits, else left, else whichever side has more room
            int right = at.x() + w + GAP, left = at.x() - GAP - ow;
            int px;
            if (right + ow + 4 <= sw) px = right;
            else if (left >= 4) px = left;
            else px = sw - (at.x() + w) > at.x() ? Math.max(4, sw - ow - 4) : 4;
            int py = Math.max(4, Math.min(at.y(), sh - oh - 4));

            drawing = true;
            ctx.drawTooltipImmediately(tr, ours, px, py, (a, b, cx, cy, cw, ch) -> new Vector2i(cx, cy), null);
        } catch (RuntimeException e) {
            NeverEnoughWind.LOG.error("side panel failed", e);
        } finally {
            drawing = false;
        }
    }

    private static List<TooltipComponent> components(TextRenderer tr, List<Row> rows) {
        // box width first, so the right hand texts can line up
        int width = 0;
        for (Row r : rows) {
            int rw = r.right() == null ? Math.min(tr.getWidth(r.left()), MAX_WIDTH)
                    : tr.getWidth(r.left()) + 8 + tr.getWidth(r.right());
            width = Math.max(width, rw);
        }
        int space = Math.max(1, tr.getWidth(" "));
        List<TooltipComponent> out = new ArrayList<>();
        for (Row r : rows) {
            if (r.right() != null) {
                int pad = Math.max(2, (width - tr.getWidth(r.left()) - tr.getWidth(r.right())) / space);
                MutableText line = Text.empty().append(r.left()).append(" ".repeat(pad)).append(r.right());
                out.add(TooltipComponent.of(line.asOrderedText()));
                continue;
            }
            List<OrderedText> wrapped = tr.wrapLines(r.left(), Math.max(width, MAX_WIDTH));
            if (wrapped.isEmpty()) out.add(TooltipComponent.of(OrderedText.EMPTY));
            for (OrderedText part : wrapped) out.add(TooltipComponent.of(part));
        }
        return out;
    }

    static List<Row> rows(ItemInfo info, Loadout.Result dmg) {
        List<Row> out = new ArrayList<>();
        // plain weapons still get a damage line
        if (info.isEmpty() && dmg == null) return out;
        PriceDao prices = NeverEnoughWind.data().prices();

        int min = 0, max = 0, unpriced = 0;
        for (EssenceEntry e : info.essences()) {
            String title = e.level() > 0 ? e.name() + " " + Roman.of(e.level()) : e.name();
            Text price = null;
            if (e.essence() != null) {
                // essences without levels are priced as level 1
                int level = Math.max(1, e.level());
                Optional<PriceTier> tier = prices.essencePrice(e.essence(), level);
                String raw = e.essence().price().get(String.valueOf(level));
                if (tier.isPresent()) {
                    price = Text.literal(tier.get().letter() + " ").formatted(Formatting.YELLOW)
                            .append(Text.literal(tier.get().text()).formatted(Formatting.WHITE));
                    if (tier.get().hasNumber()) {
                        min += tier.get().minDeggs();
                        max += tier.get().maxDeggs();
                    } else {
                        unpriced++;
                    }
                } else {
                    // not a tier, the chart wrote a note there instead
                    if (raw != null) price = Text.literal(raw).formatted(Formatting.DARK_GRAY);
                    unpriced++;
                }
            } else {
                unpriced++;
            }
            out.add(new Row(Text.literal(title).formatted(Formatting.GOLD), price));
            String text = e.essence() == null ? null : e.essence().description();
            out.add(Row.of(Text.literal(text == null ? "not documented yet" : text).formatted(Formatting.GRAY)));
            if (e.essence() != null) {
                String atLevel = e.essence().levelText().get(String.valueOf(e.level()));
                if (atLevel != null) out.add(Row.of(Text.literal(atLevel).formatted(Formatting.GREEN)));
                else if (e.essence().details() != null) out.add(Row.of(Text.literal(e.essence().details()).formatted(Formatting.DARK_GRAY)));
            }
        }
        if ("gear".equals(info.category()) && !info.essences().isEmpty()) {
            String total = max == 0 ? "no price known" : min == max ? PriceDao.text(min) : PriceDao.text(min) + " - " + PriceDao.text(max);
            if (unpriced > 0 && max > 0) total += " (+" + unpriced + " not priced)";
            out.add(Row.of(Text.empty()));
            out.add(new Row(Text.literal("essences").formatted(Formatting.YELLOW), Text.literal(total).formatted(Formatting.WHITE)));
        }

        if (dmg != null) {
            Damage d = dmg.damage();
            String plus = d.atLeast() ? "+" : "";
            if (!out.isEmpty()) out.add(Row.of(Text.empty()));
            out.add(new Row(Text.literal("damage").formatted(Formatting.RED),
                    Text.literal(DamageCalc.text(d.hit()) + plus).formatted(Formatting.WHITE)
                            .append(Text.literal("   crit ").formatted(Formatting.GRAY))
                            .append(Text.literal(DamageCalc.text(d.crit()) + plus).formatted(Formatting.WHITE))));
            if (dmg.note() != null) out.add(Row.of(Text.literal(dmg.note()).formatted(Formatting.GRAY)));
            if (!d.parts().isEmpty()) out.add(Row.of(Text.literal(String.join(", ", d.parts())).formatted(Formatting.GRAY)));
            if (!d.notCounted().isEmpty()) {
                out.add(Row.of(Text.literal("not counted: " + String.join(", ", d.notCounted())).formatted(Formatting.DARK_GRAY)));
            }
            if (!d.sometimes().isEmpty()) {
                out.add(Row.of(Text.literal("sometimes: " + String.join(", ", d.sometimes())).formatted(Formatting.DARK_GRAY)));
            }
        }
        if (info.isEmpty()) return out;

        if (!out.isEmpty()) out.add(Row.of(Text.empty()));
        out.add(Row.of(Text.literal("debug").formatted(Formatting.DARK_AQUA)));
        debug(out, "type", info.detail() == null || info.detail().isEmpty() ? info.category() : info.category() + " (" + info.detail() + ")");
        if ("gear".equals(info.category())) {
            debug(out, "auction", info.auction() == null ? "no match" : info.auction().name() + ", " + info.auction().status());
            String name = !info.renamed() ? "server style, untouched"
                    : info.restoreStyle() == null ? "renamed, no color to restore"
                    : info.auction() != null && info.auction().style() != null ? "renamed, restored" : "renamed, red fallback";
            debug(out, "name", name);
            debug(out, "flavor", info.flavor().size() + " lines");
            if (info.sharpness() != null) {
                String s = String.valueOf(info.sharpness());
                Integer base = info.auction() == null ? null : info.auction().sharpness();
                if (base != null) {
                    int spent = prices.sharpenCost(base, info.sharpness());
                    s += ", base " + base + (spent > 0 ? ", spent " + PriceDao.text(spent) : "");
                }
                debug(out, "sharpness", s);
            }
        }
        if (!info.souls().isEmpty()) {
            debug(out, "souls", info.souls().stream()
                    .map(s -> (s.consumes() ? "-" : "+") + s.count() + " " + s.type()).collect(Collectors.joining(", ")));
        }
        if (!info.essences().isEmpty()) {
            long unknown = info.essences().stream().filter(e -> e.essence() == null).count();
            debug(out, "essences", info.essences().size() + " parsed, " + unknown + " unknown");
        }
        return out;
    }

    private static void debug(List<Row> out, String label, String value) {
        out.add(Row.of(Text.literal(label + ": ").formatted(Formatting.DARK_GRAY)
                .append(Text.literal(String.valueOf(value)).formatted(Formatting.GRAY))));
    }
}
