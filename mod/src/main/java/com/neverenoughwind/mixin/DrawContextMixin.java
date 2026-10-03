package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.SidePanel;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Inject(method = "drawTooltipImmediately", at = @At("TAIL"))
    private void new$sidePanel(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y,
                               TooltipPositioner positioner, Identifier texture, CallbackInfo ci) {
        SidePanel.afterTooltip((DrawContext) (Object) this, textRenderer, components, x, y, positioner);
    }
}
