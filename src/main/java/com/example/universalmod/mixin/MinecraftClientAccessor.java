package com.example.universalmod.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    /** Задержка между использованиями предмета (ПКМ). В ваниле после установки блока = 4 тика. */
    @Accessor("itemUseCooldown")
    void setItemUseCooldown(int value);

    /** Задержка между ударами ЛКМ (после промаха в ваниле 10 тиков): пока она больше нуля, doAttack ничего не делает. */
    @Accessor("attackCooldown")
    void setAttackCooldown(int value);

    /** Один клик ПКМ (использование предмета / установка блока). */
    @Invoker("doItemUse")
    void invokeDoItemUse();

    /** Один клик ЛКМ (удар / начало разрушения). */
    @Invoker("doAttack")
    boolean invokeDoAttack();

    /** Подмена сессии (ник). */
    @Mutable
    @Accessor("session")
    void setSession(Session session);
}
