package com.example.universalmod.mixin;

import com.example.universalmod.UniversalMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Ваниль не даёт делать ЛКМ (копать) и ПКМ (использовать/ставить) одновременно:
 *  1) doItemUse() ничего не делает, пока идёт разрушение блока (isBreakingBlock);
 *  2) handleBlockBreaking() не копает, пока игрок использует предмет (isUsingItem).
 * Когда зажаты обе кнопки сразу (вручную или через «Зажать ЛКМ»/«Зажать ПКМ»/автокликер),
 * обходим обе проверки, и обе кнопки работают одновременно.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    private static boolean universalmod$bothPressed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return false;
        // Учитываем и сами кнопки, и режимы мода: автоклик нажимает кнопку не каждый тик,
        // а «зажать»/автоклик должны считаться «кнопка удерживается» всё время.
        boolean attack = mc.options.attackKey.isPressed()
                || UniversalMod.isLeftHold() || UniversalMod.isLeftOn();
        boolean use = mc.options.useKey.isPressed()
                || UniversalMod.isRightHold() || UniversalMod.isRightOn();
        return attack && use;
    }

    /** ПКМ работает, пока копается блок. */
    @Redirect(method = "doItemUse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"),
            require = 0)
    private boolean universalmod$useWhileBreaking(ClientPlayerInteractionManager manager) {
        return manager.isBreakingBlock() && !universalmod$bothPressed();
    }

    /** Копание продолжается, пока используется предмет (ПКМ зажат). */
    @Redirect(method = "handleBlockBreaking",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"),
            require = 0)
    private boolean universalmod$breakWhileUsing(ClientPlayerEntity player) {
        return player.isUsingItem() && !universalmod$bothPressed();
    }
}
