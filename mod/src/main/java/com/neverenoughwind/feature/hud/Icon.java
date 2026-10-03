package com.neverenoughwind.feature.hud;

import com.neverenoughwind.NeverEnoughWind;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// a small png drawn at whole multiples of its size, centered on its drawn pixels instead of the canvas
public final class Icon {
    private static final Map<Identifier, Icon> CACHE = new HashMap<>();

    private final Identifier id;
    private final int width, height;
    // twice the distance from the canvas middle to the middle of the drawn pixels
    private final int shiftX2, shiftY2;

    private Icon(Identifier id, int width, int height, int shiftX2, int shiftY2) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.shiftX2 = shiftX2;
        this.shiftY2 = shiftY2;
    }

    // null when the file doesnt exist
    public static Icon of(Identifier id) {
        if (CACHE.containsKey(id)) return CACHE.get(id);
        Icon icon = null;
        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                    int minX = img.getWidth(), minY = img.getHeight(), maxX = -1, maxY = -1;
                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            if ((img.getColorArgb(x, y) >>> 24) == 0) continue;
                            minX = Math.min(minX, x);
                            maxX = Math.max(maxX, x);
                            minY = Math.min(minY, y);
                            maxY = Math.max(maxY, y);
                        }
                    }
                    int sx = maxX < 0 ? 0 : (minX + maxX + 1) - img.getWidth();
                    int sy = maxY < 0 ? 0 : (minY + maxY + 1) - img.getHeight();
                    icon = new Icon(id, img.getWidth(), img.getHeight(), sx, sy);
                }
            }
        } catch (Exception e) {
            NeverEnoughWind.LOG.warn("could not read icon {}: {}", id, e.toString());
        }
        CACHE.put(id, icon);
        return icon;
    }

    public int size(int scale) {
        return Math.max(width, height) * scale;
    }

    // x, y = top left of the box the icon should sit centered in
    public void draw(DrawContext ctx, int x, int y, int scale) {
        int dx = Math.round(shiftX2 * scale / 2f), dy = Math.round(shiftY2 * scale / 2f);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, x - dx, y - dy, 0f, 0f,
                width * scale, height * scale, width, height, width, height);
    }
}
