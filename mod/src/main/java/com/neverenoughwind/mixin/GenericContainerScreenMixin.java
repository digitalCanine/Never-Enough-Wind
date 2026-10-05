package com.neverenoughwind.mixin;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// looking into another player's inventory arrives as a five row chest: armor and off hand in the first row,
// then their 27 slots and their hotbar. this lays the same slots out like a real inventory and leaves your own
// inventory out of the picture. only where things are drawn changes, the slots stay exactly what the server set up
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin extends HandledScreen<GenericContainerScreenHandler> {
    @Unique
    private static final Pattern TITLE = Pattern.compile("^(\\S+)'s inventory$");
    @Unique
    private static final Identifier INVENTORY = Identifier.ofVanilla("textures/gui/container/inventory.png");
    // far off screen, where your own slots go
    @Unique
    private static final int HIDDEN = -10000;

    // the inspected player's name, null on every other chest
    @Unique
    private String new$inspected;

    private GenericContainerScreenMixin(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void new$layout(GenericContainerScreenHandler handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        Matcher m = TITLE.matcher(title.getString());
        if (!m.matches() || handler.getRows() != 5 || handler.slots.size() != 81) return;
        if (!Config.get().inventoryView || !Worlds.onMinewind()) return;
        new$inspected = m.group(1);
        backgroundHeight = 166;
        // the name is drawn by us, the "Inventory" label has no room
        titleY = -10000;
        playerInventoryTitleY = -10000;
        for (int i = 0; i < 81; i++) {
            Slot slot = handler.slots.get(i);
            int x, y;
            if (i < 4) {
                // boots, leggings, chestplate, helmet: helmet goes on top
                x = 8;
                y = 8 + (3 - i) * 18;
            } else if (i == 4) {
                // off hand
                x = 77;
                y = 62;
            } else if (i < 9) {
                // four slots the server leaves empty, they take the crafting squares
                x = 98 + (i - 5) % 2 * 18;
                y = 18 + (i - 5) / 2 * 18;
            } else if (i < 36) {
                x = 8 + (i - 9) % 9 * 18;
                y = 84 + (i - 9) / 9 * 18;
            } else if (i < 45) {
                x = 8 + (i - 36) * 18;
                y = 142;
            } else {
                // your own inventory, not shown
                x = HIDDEN;
                y = HIDDEN;
            }
            ((SlotAccessor) slot).new$setX(x);
            ((SlotAccessor) slot).new$setY(y);
        }
    }

    @Inject(method = "drawBackground", at = @At("HEAD"), cancellable = true)
    private void new$background(DrawContext ctx, float delta, int mouseX, int mouseY, CallbackInfo ci) {
        if (new$inspected == null) return;
        ci.cancel();
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, INVENTORY, x, y, 0f, 0f, backgroundWidth, backgroundHeight, 256, 256);

        // their name where your own inventory says "Crafting", squeezed when its a long one
        int room = backgroundWidth - 97 - 6, w = textRenderer.getWidth(new$inspected);
        float scale = w > room ? (float) room / w : 1f;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x + 97, y + 8);
        matrices.scale(scale, scale);
        ctx.drawText(textRenderer, new$inspected, 0, 0, 0xFF404040, false);
        matrices.popMatrix();

        // and the player themselves, when they are close enough to be loaded
        if (client == null || client.world == null) return;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (!player.getGameProfile().getName().equalsIgnoreCase(new$inspected)) continue;
            InventoryScreen.drawEntity(ctx, x + 26, y + 8, x + 75, y + 78, 30, 0.0625f, mouseX, mouseY, player);
            break;
        }
    }
}
