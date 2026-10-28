package com.example.universalmod.mixin;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientPlayerInteractionManager.class)
public interface InteractionManagerAccessor {
    /** Задержка между разрушением соседних блоков. В ваниле = 5 тиков. */
    @Accessor("blockBreakingCooldown")
    void setBlockBreakingCooldown(int value);
}
