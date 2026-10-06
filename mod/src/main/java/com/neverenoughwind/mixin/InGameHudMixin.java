package com.neverenoughwind.mixin;

import com.neverenoughwind.adapter.Items;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.feature.NameColor;
import com.neverenoughwind.feature.hud.HudWidget;
import com.neverenoughwind.parse.ItemInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Unique
    private static final String DRAW_NAME = "Lnet/minecraft/client/gui/DrawContext;drawTextWithBackground(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIII)V";

    @Shadow
    private ItemStack currentStack;

    @Shadow
    public abstract TextRenderer getTextRenderer();

    // the item name that fades in above the hotbar: renamed auction gear gets its original look here too
    @Unique
    private Text new$restyled() {
        if (currentStack == null || currentStack.isEmpty() || !Config.get().originalNames) return null;
        ItemInfo info = Items.info(currentStack);
        return info.restoreStyle() == null ? null : NameColor.paint(currentStack.getName().getString(), info.restoreStyle());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void new$trailer(DrawContext ctx, RenderTickCounter tick, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden && Config.get().trailer) HudWidget.renderHidden(ctx);
    }

    @ModifyArg(method = "renderHeldItemTooltip", at = @At(value = "INVOKE", target = DRAW_NAME), index = 1)
    private Text new$heldName(Text original) {
        Text ours = new$restyled();
        return ours == null ? original : ours;
    }

    // bold text is wider, so center and size the background for the new text
    @ModifyArg(method = "renderHeldItemTooltip", at = @At(value = "INVOKE", target = DRAW_NAME), index = 2)
    private int new$heldNameX(int original) {
        Text ours = new$restyled();
        if (ours == null) return original;
        return (MinecraftClient.getInstance().getWindow().getScaledWidth() - getTextRenderer().getWidth(ours)) / 2;
    }

    @ModifyArg(method = "renderHeldItemTooltip", at = @At(value = "INVOKE", target = DRAW_NAME), index = 4)
    private int new$heldNameWidth(int original) {
        Text ours = new$restyled();
        return ours == null ? original : getTextRenderer().getWidth(ours);
    }
}
