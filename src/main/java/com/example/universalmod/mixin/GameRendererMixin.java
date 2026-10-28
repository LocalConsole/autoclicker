package com.example.universalmod.mixin;

import com.example.universalmod.Zoom;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Делим угол обзора на кратность зума. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void universalmod$zoom(CallbackInfoReturnable<Float> cir) {
        Zoom.hookHits++;
        double f = Zoom.factor();
        if (f > 1.0005) {
            cir.setReturnValue((float) (cir.getReturnValue() / f));
        }
    }
}
