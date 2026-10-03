package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.Highlighter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    // hotbar and off hand slots both go through here
    @Inject(method = "renderHotbarItem", at = @At("HEAD"))
    private void new$border(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player,
                            ItemStack stack, int seed, CallbackInfo ci) {
        Highlighter.draw(context, stack, x, y);
    }
}
