package com.example.universalmod.mixin;

import com.example.universalmod.InvisibleParticles;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
    /** Частицы зелья невидимости у игроков заменяем на выбранные, более заметные. */
    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$invisParticles(ParticleEffect effect, double x, double y, double z,
                                             double vx, double vy, double vz,
                                             CallbackInfoReturnable<Particle> cir) {
        ParticleEffect replacement = InvisibleParticles.replacement(effect, x, y, z);
        if (replacement == null) return;
        ParticleManager self = (ParticleManager) (Object) this;
        Particle last = null;
        for (int i = 0; i < InvisibleParticles.COPIES; i++) {
            last = self.addParticle(replacement, x, y, z,
                    InvisibleParticles.velX(replacement), 0.05, 0.0);
        }
        cir.setReturnValue(last);
    }
}
