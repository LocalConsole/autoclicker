package com.example.universalmod.mixin;

import com.example.universalmod.AccuratePlace;
import com.example.universalmod.Freecam;
import com.example.universalmod.UniversalMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ваниль не даёт делать ЛКМ (копать) и ПКМ (использовать/ставить) одновременно:
 *  1) doItemUse() ничего не делает, пока идёт разрушение блока (isBreakingBlock);
 *  2) handleBlockBreaking() не копает, пока игрок использует предмет (isUsingItem).
 * Когда зажаты обе кнопки сразу (вручную или через «Зажать ЛКМ»/«Зажать ПКМ»/автокликер),
 * обходим обе проверки, и обе кнопки работают одновременно.
 *
 * ВАЖНО про require = 0. Это значит «не нашёл место для вставки — молча пропусти».
 * Из-за этого сломанный обход выглядел как работающий: игра запускалась, ошибок не было,
 * а функции просто не существовало. Теперь каждый обход отмечает себя в UniversalMod, и если
 * обе кнопки зажаты две секунды, а обход ни разу не сработал, мод сам пишет об этом в чат.
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

    /**
     * Пока «Точная установка» ведёт установку сама, ванильную глушим: иначе она ставила бы блоки
     * параллельно, по своему расписанию, и вся точность пропадала бы. Наши собственные вызовы
     * проходят по флагу busy.
     */
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true, require = 0)
    private void universalmod$blockVanillaPlace(CallbackInfo ci) {
        if (Freecam.active || (AccuratePlace.suppressVanilla && !AccuratePlace.busy)) ci.cancel();
    }

    /** ПКМ работает, пока копается блок. */
    @Redirect(method = "doItemUse",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"),
            require = 0)
    private boolean universalmod$useWhileBreaking(ClientPlayerInteractionManager manager) {
        UniversalMod.hookUseAlive = true;
        return manager.isBreakingBlock() && !universalmod$bothPressed();
    }

    /**
     * Копание продолжается, пока используется предмет (ПКМ зажат).
     * isUsingItem() объявлен в LivingEntity, и в байткоде вызов записан по тому классу,
     * через который он вызван. Раньше здесь стоял ClientPlayerEntity — если компилятор
     * записал вызов как LivingEntity, совпадения не было и обход молча не ставился.
     */
    @Redirect(method = "handleBlockBreaking",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/LivingEntity;isUsingItem()Z"),
            require = 0)
    private boolean universalmod$breakWhileUsingLiving(LivingEntity player) {
        UniversalMod.hookBreakAlive = true;
        return player.isUsingItem() && !universalmod$bothPressed();
    }

    /** Тот же обход на случай, если вызов записан по ClientPlayerEntity. Сработает ровно один из двух. */
    @Redirect(method = "handleBlockBreaking",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"),
            require = 0)
    private boolean universalmod$breakWhileUsingClient(net.minecraft.client.network.ClientPlayerEntity player) {
        UniversalMod.hookBreakAlive = true;
        return player.isUsingItem() && !universalmod$bothPressed();
    }
}
