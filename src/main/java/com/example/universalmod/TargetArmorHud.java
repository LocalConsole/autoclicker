package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Прочность брони соперника: игрок под прицелом (и ещё пару секунд после) показывается справа от прицела
 * списком шлем / нагрудник / поножи / ботинки с оставшейся прочностью. Данные берутся из того, что сервер
 * присылает клиенту о надетых вещах, так что если сервер их скрывает, прочности не будет видно.
 */
public class TargetArmorHud {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final long KEEP_MS = 2000;

    private static PlayerEntity last = null;
    private static long lastSeen = 0;

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(UniversalMod.MOD_ID, "target_armor"),
                (context, tickCounter) -> draw(context));
    }

    private static void draw(DrawContext ctx) {
        if (!ClickerConfig.targetArmorHud) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden || mc.currentScreen != null) return;

        long now = System.currentTimeMillis();
        HitResult h = mc.crosshairTarget;
        if (h instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity p && p != mc.player) {
            last = p;
            lastSeen = now;
        }
        if (last == null || now - lastSeen > KEEP_MS || last.isRemoved()) {
            last = null;
            return;
        }

        TextRenderer tr = mc.textRenderer;
        int x = ctx.getScaledWindowWidth() / 2 + 18;
        int y = ctx.getScaledWindowHeight() / 2 - 24;

        ctx.drawTextWithShadow(tr, last.getName().getString(), x, y, 0xFFFFFFFF);
        y += 11;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = last.getEquippedStack(slot);
            if (st.isEmpty() || !st.isDamageable()) continue;
            int max = st.getMaxDamage();
            int left = max - st.getDamage();
            float percent = max <= 0 ? 100f : left * 100f / max;
            ctx.drawItem(st, x, y);
            ctx.drawTextWithShadow(tr, left + " (" + Math.round(percent) + "%)", x + 18, y + 5, color(percent));
            y += 18;
        }
    }

    private static int color(float percent) {
        if (percent > 50f) return 0xFF55FF55;
        if (percent > 25f) return 0xFFFFFF55;
        return 0xFFFF5555;
    }
}
