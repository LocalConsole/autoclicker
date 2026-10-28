package com.example.universalmod.mixin;

import com.example.universalmod.ChatTools;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$copyLine(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ChatTools.tryCopy(click)) {
            cir.setReturnValue(true);
        }
    }
}
