package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.Clans;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Unique
    private static boolean new$drawing;

    @Shadow
    protected abstract void renderLabelIfPresent(PlayerEntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light);

    // the name itself takes the clan's color when that setting is on
    @ModifyVariable(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"), argsOnly = true)
    private Text new$nameColor(Text text, PlayerEntityRenderState state) {
        return new$drawing || text == null ? text : Clans.nametagName(state.name, text);
    }

    // the game only gets here when it is already showing this player's nametag, so hidden names stay hidden.
    // right before it finishes, draw one more label a line higher with the clan
    @Inject(method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;Lnet/minecraft/text/Text;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void new$clanLine(PlayerEntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (new$drawing) return;
        Text line = Clans.nametagLine(state.name);
        if (line == null) return;
        // the vanilla method also draws the score line when playerName is set, hide it for our extra pass
        Text score = state.playerName;
        state.playerName = null;
        new$drawing = true;
        try {
            matrices.translate(0.0F, 9.0F * 1.15F * 0.025F, 0.0F);
            renderLabelIfPresent(state, line, matrices, consumers, light);
        } finally {
            new$drawing = false;
            state.playerName = score;
        }
    }
}
