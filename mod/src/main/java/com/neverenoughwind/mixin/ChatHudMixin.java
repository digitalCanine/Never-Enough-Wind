package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.chat.Tabs;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"))
    private void new$incoming(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        Tabs.onLine(message);
    }

    // the game keeps every line, this is only what the chat box shows. it runs again for all of them when a tab is switched
    @Inject(method = "addVisibleMessage", at = @At("HEAD"), cancellable = true)
    private void new$filter(ChatHudLine line, CallbackInfo ci) {
        if (!Tabs.shows(line.content())) ci.cancel();
    }

    // five tabs share the list the game keeps, 100 lines would empty the quiet tabs
    @ModifyConstant(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", constant = @Constant(intValue = 100))
    private int new$keepMore(int max) {
        return Tabs.on() ? 1000 : max;
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void new$cleared(boolean clearHistory, CallbackInfo ci) {
        Tabs.clear();
    }
}
