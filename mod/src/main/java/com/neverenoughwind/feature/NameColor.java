package com.neverenoughwind.feature;

import com.neverenoughwind.adapter.Items;
import com.neverenoughwind.model.NameStyle;
import com.neverenoughwind.parse.ItemInfo;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

// gives renamed auction gear its original name color back, tooltip title only
public final class NameColor {
    private NameColor() {}

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (lines.isEmpty()) return;
            ItemInfo info = Items.info(stack);
            if (info.restoreStyle() == null) return;
            lines.set(0, paint(stack.getName().getString(), info.restoreStyle()));
        });
    }

    public static Text paint(String name, NameStyle style) {
        Style base = Style.EMPTY.withBold(style.bold()).withItalic(false);
        if (!style.isGradient()) {
            TextColor color = color(style.color());
            return Text.literal(name).setStyle(color == null ? base : base.withColor(color));
        }
        int from = rgb(style.gradientFrom()), to = rgb(style.gradientTo());
        // fancy unicode letters are two chars each, so walk code points
        int[] points = name.codePoints().toArray();
        MutableText out = Text.empty();
        for (int i = 0; i < points.length; i++) {
            float t = points.length == 1 ? 0 : (float) i / (points.length - 1);
            out.append(Text.literal(new String(Character.toChars(points[i])))
                    .setStyle(base.withColor(TextColor.fromRgb(mix(from, to, t)))));
        }
        return out;
    }

    private static TextColor color(String name) {
        if (name == null) return null;
        if (name.startsWith("#")) return TextColor.fromRgb(rgb(name));
        Formatting f = Formatting.byName(name);
        return f == null ? null : TextColor.fromFormatting(f);
    }

    private static int rgb(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }
}
