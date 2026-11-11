package com.example.universalmod.mixin;

import com.example.universalmod.Freecam;
import com.example.universalmod.SlotTools;
import com.example.universalmod.Zoom;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    /** Пока включена свободная камера, колёсико меняет скорость полёта, а не слот хотбара. */
    @ModifyVariable(method = "onMouseScroll", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private double universalmod$freecamScroll(double vertical) {
        if (Freecam.active) {
            Freecam.scroll(vertical);
            return 0;
        }
        return vertical;
    }

    /** Пока зажат зум, колёсико меняет кратность, а не слот хотбара. */
    @ModifyVariable(method = "onMouseScroll", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private double universalmod$zoomScroll(double vertical) {
        if (Zoom.held) {
            Zoom.scroll(vertical);
            return 0;
        }
        return vertical;
    }

    /** Колёсико над слотом в инвентаре переносит предметы (как Mouse Tweaks / Item Scroller). */
    @ModifyVariable(method = "onMouseScroll", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private double universalmod$slotScroll(double vertical) {
        return SlotTools.scrollHook(vertical) ? 0 : vertical;
    }
}
