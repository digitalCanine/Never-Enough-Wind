package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.Clans;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {
    // the game only asks for this color when it is already drawing the glow, so nobody glows because of us
    @Inject(method = "getTeamColorValue", at = @At("RETURN"), cancellable = true)
    private void new$glowColor(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof PlayerEntity player) {
            cir.setReturnValue(Clans.glowColor(player.getNameForScoreboard(), cir.getReturnValue()));
        }
    }
}
