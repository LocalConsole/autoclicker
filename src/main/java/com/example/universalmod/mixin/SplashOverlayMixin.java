package com.example.universalmod.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    /** Прячем стандартный экран загрузки: обрезаем всё рисование пустой областью. */
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void universalmod$hide(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        ctx.enableScissor(0, 0, 0, 0);
    }

    /** Рисуем маленькую красную полоску чуть выше прицела. */
    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void universalmod$indicator(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        ctx.disableScissor();

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int trackW = 60, segW = 20;
        int x = w / 2 - trackW / 2;
        int y = h / 2 - 28;

        ctx.fill(x, y, x + trackW, y + 2, 0x80400000);
        long t = System.currentTimeMillis() % 1000L;
        int pos = (int) ((trackW - segW) * (0.5 - 0.5 * Math.cos(t / 1000.0 * Math.PI * 2)));
        ctx.fill(x + pos, y, x + pos + segW, y + 2, 0xFFFF2020);
    }
}
