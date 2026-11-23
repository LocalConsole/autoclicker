package com.example.universalmod.mixin;

import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Положение и размер окна инвентаря/сундука — чтобы панель кнопок всегда стояла ровно сбоку от него. */
@Mixin(HandledScreen.class)
public interface HandledScreenAccessor {
    @Accessor("x")
    int universalmod$getX();

    @Accessor("y")
    int universalmod$getY();

    @Accessor("backgroundWidth")
    int universalmod$getBgWidth();

    @Accessor("backgroundHeight")
    int universalmod$getBgHeight();
}
