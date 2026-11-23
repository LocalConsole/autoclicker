package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

/**
 * Авто-снос щита: если игрок в прицеле закрылся щитом, а в хотбаре есть топор — в этом же тике берём топор,
 * бьём (топор выбивает щит на 5 секунд) и возвращаем прежний слот.
 */
public class ShieldBreak {
    private static int cooldown = 0;

    public static void tick(MinecraftClient mc) {
        if (cooldown > 0) cooldown--;
        if (!ClickerConfig.shieldBreak || cooldown > 0 || AutoBucket.busy()) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null || p.isSpectator()) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult ehr)) return;
        Entity e = ehr.getEntity();
        if (!(e instanceof LivingEntity le) || !le.isBlocking()) return;
        if (p.getEyePos().squaredDistanceTo(ehr.getPos()) > Math.pow(p.getEntityInteractionRange(), 2)) return;

        PlayerInventory inv = p.getInventory();
        int axe = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isIn(ItemTags.AXES)) {
                axe = i;
                break;
            }
        }
        if (axe < 0) return;
        int prev = inv.getSelectedSlot();
        if (axe != prev) inv.setSelectedSlot(axe);
        mc.interactionManager.attackEntity(p, e);
        p.swingHand(Hand.MAIN_HAND);
        if (axe != prev && ClickerConfig.shieldBreakReturn) inv.setSelectedSlot(prev);
        cooldown = 10;
    }
}
