package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.EnderChest;
import com.neverenoughwind.feature.Highlighter;
import com.neverenoughwind.feature.ItemDump;
import com.neverenoughwind.feature.SidePanel;
import com.neverenoughwind.feature.Worth;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {
    @Shadow @Final protected T handler;
    @Shadow protected Slot focusedSlot;
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void new$slotColor(DrawContext context, Slot slot, CallbackInfo ci) {
        EnderChest.drawBehind(context, (Screen) (Object) this, handler, slot);
    }

    @Inject(method = "drawSlot", at = @At("RETURN"))
    private void new$slotMark(DrawContext context, Slot slot, CallbackInfo ci) {
        EnderChest.drawOver(context, (Screen) (Object) this, handler, slot);
    }

    // ender chest sorting, only when the player turned it on: their shift click on one of their own items becomes
    // plain clicks, pick it up and put it down where its color says. slots kept for something else are never used:
    // with nowhere allowed to go, the item stays where it is
    @Inject(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V", at = @At("HEAD"), cancellable = true)
    private void new$sortIntoColor(Slot slot, int slotId, int button, SlotActionType action, CallbackInfo ci) {
        if (action != SlotActionType.QUICK_MOVE || slot == null || !slot.hasStack() || !(slot.inventory instanceof PlayerInventory)) return;
        if (!handler.getCursorStack().isEmpty()) return;
        List<Integer> targets = EnderChest.slotsFor((Screen) (Object) this, handler, slot.getStack());
        MinecraftClient mc = MinecraftClient.getInstance();
        if (targets == null || mc.interactionManager == null || mc.player == null) return;
        ci.cancel();
        if (targets.isEmpty()) return;
        mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
        // a full stack can spill over several slots
        for (int target : targets) {
            if (handler.getCursorStack().isEmpty()) break;
            mc.interactionManager.clickSlot(handler.syncId, target, 0, SlotActionType.PICKUP, mc.player);
        }
        // whatever found no room goes back where it came from
        if (!handler.getCursorStack().isEmpty()) mc.interactionManager.clickSlot(handler.syncId, slot.id, 0, SlotActionType.PICKUP, mc.player);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void new$worth(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Worth.draw(context, handler, x + backgroundWidth, y + backgroundHeight);
    }

    // same check vanilla does before it shows an item tooltip
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"))
    private void new$rememberHovered(DrawContext context, int x, int y, CallbackInfo ci) {
        boolean showing = handler.getCursorStack().isEmpty() && focusedSlot != null && focusedSlot.hasStack();
        SidePanel.hovered = showing ? focusedSlot.getStack() : null;
        ItemDump.hovered = SidePanel.hovered;
    }

    // the item stuck to the cursor has no slot around it, so it gets no border
    @Inject(method = "drawItem", at = @At("HEAD"))
    private void new$cursorItemStart(DrawContext context, ItemStack stack, int x, int y, String amountText, CallbackInfo ci) {
        Highlighter.paused = true;
    }

    @Inject(method = "drawItem", at = @At("RETURN"))
    private void new$cursorItemEnd(DrawContext context, ItemStack stack, int x, int y, String amountText, CallbackInfo ci) {
        Highlighter.paused = false;
    }
}
