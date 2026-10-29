package com.example.universalmod.mixin;

import com.example.universalmod.AutoRefill;
import com.example.universalmod.SlotTools;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Shadow protected Slot focusedSlot;
    @Shadow protected int x;
    @Shadow protected int y;

    /** Ctrl + клик по слоту хотбара / второй руки — включить или выключить для него автозаполнение. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$toggleRefill(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (SlotTools.clickAll(this.focusedSlot, click.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (AutoRefill.tryToggle(this.focusedSlot)) {
            cir.setReturnValue(true);
        }
    }

    /** Фиолетовые отметки на слотах с включённым автозаполнением. */
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void universalmod$refillMarks(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        SlotTools.frame(this.focusedSlot);
        AutoRefill.drawScreenMarks(context, ((HandledScreen<?>) (Object) this).getScreenHandler(), this.x, this.y);
    }
}
