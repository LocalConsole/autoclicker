package com.example.universalmod.mixin;

import com.example.universalmod.ClickerConfig;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {
    /** Цвета контура: светло-серый, красный, зелёный, синий (ARGB). */
    private static final int[] UNIVERSALMOD_COLORS = {0xFFC8C8C8, 0xFFFF3030, 0xFF30FF30, 0xFF3060FF};

    /**
     * Меняем цвет ванильного контура блока, на который навели прицел (первый int-аргумент drawBlockOutline — цвет).
     * Сам контур рисует игра, мод только подставляет цвет.
     */
    @ModifyVariable(method = "drawBlockOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int universalmod$outlineColor(int color) {
        if (!ClickerConfig.targetOutline) return color;
        int i = Math.max(0, Math.min(UNIVERSALMOD_COLORS.length - 1, ClickerConfig.targetOutlineColor));
        return UNIVERSALMOD_COLORS[i];
    }
}
