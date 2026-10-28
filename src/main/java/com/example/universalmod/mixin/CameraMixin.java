package com.example.universalmod.mixin;

import com.example.universalmod.FreeLook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float universalmod$yaw(float yaw) {
        return FreeLook.active ? FreeLook.camYaw : yaw;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private float universalmod$pitch(float pitch) {
        return FreeLook.active ? FreeLook.camPitch : pitch;
    }
}
