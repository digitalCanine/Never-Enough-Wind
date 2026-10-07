package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.Highlighter;
import com.neverenoughwind.feature.ItemDump;
import com.neverenoughwind.feature.SidePanel;
import com.neverenoughwind.feature.Worth;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> {
    @Shadow @Final protected T handler;
    @Shadow protected Slot focusedSlot;
    @Shadow protected int x;
    @Shadow protected int y;
    @Shadow protected int backgroundWidth;
    @Shadow protected int backgroundHeight;

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
