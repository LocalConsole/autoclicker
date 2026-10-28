package com.example.universalmod.mixin;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor<T> {
    /** Запись значения в обход проверки диапазона (нужно для яркости > 100%). */
    @Accessor("value")
    void setRawValue(T value);
}
