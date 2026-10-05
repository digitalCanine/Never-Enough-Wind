package com.neverenoughwind.mixin;

import com.neverenoughwind.adapter.Worlds;
import com.neverenoughwind.config.Config;
import com.neverenoughwind.parse.JoinDate;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
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
