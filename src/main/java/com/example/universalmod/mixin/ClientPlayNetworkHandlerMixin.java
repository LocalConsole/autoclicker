package com.example.universalmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Сервер может сам закрыть открытое окно (например, при получении урона).
 * Пока открыто окно мода, такая команда игнорируется — окно закроется только вами.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onCloseScreen", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$keepModScreen(CloseScreenS2CPacket packet, CallbackInfo ci) {
        Screen s = MinecraftClient.getInstance().currentScreen;
        if (s != null && s.getClass().getName().startsWith("com.example.universalmod.")) {
            ci.cancel();
        }
    }
}
