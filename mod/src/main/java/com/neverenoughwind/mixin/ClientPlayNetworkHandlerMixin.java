package com.neverenoughwind.mixin;

import com.neverenoughwind.adapter.Worlds;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnPositionS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    // head = before the new world exists, so the first spawn point of the new connection counts
    @Inject(method = "onGameJoin", at = @At("HEAD"))
    private void new$join(GameJoinS2CPacket packet, CallbackInfo ci) {
        Worlds.onJoin();
    }

    // tail = after the game applied it, on the main thread
    @Inject(method = "onPlayerSpawnPosition", at = @At("TAIL"))
    private void new$spawnPoint(PlayerSpawnPositionS2CPacket packet, CallbackInfo ci) {
        Worlds.onSpawnPoint(packet.getPos());
    }
}
