package com.example.universalmod.mixin;

import com.example.universalmod.FreeLook;
import net.minecraft.client.MinecraftClient;
import com.example.universalmod.Zoom;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** Пока включён осмотр, мышь вращает камеру, а не игрока. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$freelook(double dx, double dy, CallbackInfo ci) {
        if (FreeLook.active && (Object) this == MinecraftClient.getInstance().player) {
            FreeLook.turn(dx, dy);
            ci.cancel();
        }
    }

    /** Зум: чувствительность мыши падает вместе с углом обзора. */
    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private double universalmod$zoomSensX(double dx) {
        return (Object) this == MinecraftClient.getInstance().player ? dx * Zoom.sensScale() : dx;
    }

    @ModifyVariable(method = "changeLookDirection", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private double universalmod$zoomSensY(double dy) {
        return (Object) this == MinecraftClient.getInstance().player ? dy * Zoom.sensScale() : dy;
    }
}
