package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.Highlighter;
import com.neverenoughwind.feature.SidePanel;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    // every item the game or another mod draws in a menu or on the hud ends up here: slots, the hotbar,
    // bundle and shulker previews. before the item, so the item is drawn on top of the line
    @Inject(method = "drawItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;III)V",
            at = @At("HEAD"))
    private void new$border(LivingEntity entity, World world, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        Highlighter.draw((DrawContext) (Object) this, stack, x, y);
    }

    // an item tooltip asked for by anything other than an inventory slot, like the items inside a shulker preview:
    // remember the item so the side panel shows up next to that tooltip too
    @Inject(method = "drawItemTooltip", at = @At("HEAD"))
    private void new$rememberItem(TextRenderer textRenderer, ItemStack stack, int x, int y, CallbackInfo ci) {
        SidePanel.hovered = stack;
    }

    @Inject(method = "drawTooltipImmediately", at = @At("TAIL"))
    private void new$sidePanel(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
                               TooltipPositioner positioner, Identifier texture, CallbackInfo ci) {
        SidePanel.afterTooltip((DrawContext) (Object) this, textRenderer, components, x, y, positioner);
    }
}
