package com.example.universalmod.mixin;

import com.example.universalmod.Freecam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Позиция камеры для свободной камеры. Вынесено в отдельный необязательный конфиг
 * (universal_mod.freecam.mixins.json): если в этой версии игры метод называется иначе,
 * игра всё равно запустится, а свободная камера сама скажет об этом в чате.
 */
@Mixin(Camera.class)
public abstract class CameraFreecamMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    /** После обычного расчёта камеры ставим её в свою точку. Вызывается каждый кадр. */
    @Inject(method = "update", at = @At("RETURN"), require = 0)
    private void universalmod$freecamPos(CallbackInfo ci) {
        if (!Freecam.active) return;
        Freecam.frame(MinecraftClient.getInstance());
        this.setPos(Freecam.x, Freecam.y, Freecam.z);
    }
}
