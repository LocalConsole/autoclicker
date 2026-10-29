package com.example.universalmod;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;

/**
 * Ловушка из верстаков, калиток, сундуков, стоек для брони и т.п.: правый клик по такому блоку/существу
 * открывает его или взаимодействует с ним, а жемчуг не бросается и хорус не едится. Здесь, если в руке
 * эндер-жемчуг или плод хоруса, клик превращается в обычное использование предмета (как клик в воздух):
 * блок/существо не получает клик, и серверу он не отправляется.
 */
public class TrapUse {
    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!ClickerConfig.trapUse || !world.isClient()) return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager == null || player != mc.player) return ActionResult.PASS;

            ItemStack st = player.getStackInHand(hand);
            if (!st.isOf(Items.ENDER_PEARL) && !st.isOf(Items.CHORUS_FRUIT)) return ActionResult.PASS;

            ActionResult r = mc.interactionManager.interactItem(player, hand);
            // Не получилось (например, жемчуг на перезарядке): всё равно не открываем блок
            return r.isAccepted() ? r : ActionResult.FAIL;
        });

        // Существа: стойка для брони, рамка, житель, лодка и т.п.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!ClickerConfig.trapUse || !world.isClient()) return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.interactionManager == null || player != mc.player) return ActionResult.PASS;

            ItemStack st = player.getStackInHand(hand);
            if (!st.isOf(Items.ENDER_PEARL) && !st.isOf(Items.CHORUS_FRUIT)) return ActionResult.PASS;

            ActionResult r = mc.interactionManager.interactItem(player, hand);
            return r.isAccepted() ? r : ActionResult.FAIL;
        });
    }
}
