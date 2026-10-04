package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.Highlighter;
import com.neverenoughwind.feature.ItemDump;
import com.neverenoughwind.feature.SidePanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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

    // same check vanilla does before it shows an item tooltip
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"))
    private void new$rememberHovered(DrawContext context, int x, int y, CallbackInfo ci) {
        boolean showing = handler.getCursorStack().isEmpty() && focusedSlot != null && focusedSlot.hasStack();
        SidePanel.hovered = showing ? focusedSlot.getStack() : null;
        ItemDump.hovered = SidePanel.hovered;
    }

    // before the item, so the item is drawn on top of the line
    @Inject(method = "drawSlot", at = @At("HEAD"))
    private void new$border(DrawContext context, Slot slot, CallbackInfo ci) {
        Highlighter.draw(context, slot.getStack(), slot.x, slot.y);
    }
}
