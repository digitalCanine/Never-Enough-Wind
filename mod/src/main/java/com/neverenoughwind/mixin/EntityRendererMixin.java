package com.neverenoughwind.mixin;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.feature.SeenKills;
import com.neverenoughwind.parse.JoinDate;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Unique
    private static boolean new$extra;

    @Shadow
    protected abstract void renderLabelIfPresent(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light);

    // one more line under a /pf profile, one row lower than its last one
    @Inject(method = "renderLabelIfPresent", at = @At("TAIL"))
    private void new$seenKills(EntityRenderState state, Text text, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo ci) {
        if (new$extra) return;
        Text line = SeenKills.profileLine(state, text);
        if (line == null) return;
        new$extra = true;
        matrices.push();
        try {
            // the profile's armor stands sit this far apart
            matrices.translate(0.0, -0.28, 0.0);
            renderLabelIfPresent(state, line, matrices, consumers, light);
        } finally {
            matrices.pop();
            new$extra = false;
        }
    }

    // the /pf lines are named armor stands. a join date that was lost in the merge says so instead of 20000 days
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void new$joinDate(Entity entity, CallbackInfoReturnable<Text> cir) {
        Text name = cir.getReturnValue();
        if (name == null || !(entity instanceof ArmorStandEntity) || !Config.get().fixJoinDate || !Worlds.onMinewind()) return;
        if (!JoinDate.lost(name.getString(), System.currentTimeMillis() / 86_400_000L)) return;
        // same two colors the server uses for the line
        cir.setReturnValue(Text.literal("First joined: ").formatted(Formatting.AQUA)
                .append(Text.literal("pre-merge").formatted(Formatting.YELLOW)));
    }
}
