package com.neverenoughwind.mixin;

import com.neverenoughwind.feature.chat.Tabs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Shadow
    protected TextFieldWidget chatField;

    // only into an empty box: opening chat with / or with text already in it is left alone
    @Inject(method = "init", at = @At("TAIL"))
    private void new$prefill(CallbackInfo ci) {
        if (chatField.getText().isEmpty()) Tabs.prefill(chatField);
    }

    // before the screen's own widgets, so command suggestions still draw over the tabs
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void new$tabs(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Tabs.renderBar(ctx, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void new$tabClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (Tabs.click(mouseX, mouseY, button, chatField)) cir.setReturnValue(true);
    }

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void new$nothingTyped(String text, boolean addToHistory, CallbackInfo ci) {
        if (Tabs.onlyPrefill(text)) ci.cancel();
    }
}
