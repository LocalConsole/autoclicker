package com.example.universalmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Предупреждение о низкой прочности своей брони и предмета в руке: надпись над хотбаром и звук.
 * Срабатывает один раз для предмета; снова сработает после ремонта и нового падения ниже порога.
 */
public class DurabilityWarn {
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND
    };
    private static final Item[] warned = new Item[SLOTS.length];
    private static int ticks = 0;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DurabilityWarn::tick);
    }

    private static void tick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (!ClickerConfig.durWarn || p == null) {
            java.util.Arrays.fill(warned, null);
            return;
        }
        if (++ticks % 10 != 0) return;

        int thr = Math.max(1, Math.min(50, ClickerConfig.durWarnPercent));
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack st = p.getEquippedStack(SLOTS[i]);
            if (st.isEmpty() || !st.isDamageable()) {
                warned[i] = null;
                continue;
            }
            int max = st.getMaxDamage();
            int pct = max <= 0 ? 100 : Math.round((max - st.getDamage()) * 100f / max);
            if (pct <= thr) {
                if (warned[i] != st.getItem()) {
                    warned[i] = st.getItem();
                    warn(p, st, pct);
                }
            } else if (pct > thr + 5) {
                warned[i] = null;
            }
        }
    }

    private static void warn(PlayerEntity p, ItemStack st, int pct) {
        p.sendMessage(Text.literal("\u26A0 " + st.getName().getString() + ": " + pct + "%").formatted(Formatting.RED), true);
        beep(p);
    }

    /** Звук предупреждения: если при сборке тут ошибка типа (SoundEvent / RegistryEntry), уберите или добавьте .value(). */
    private static void beep(PlayerEntity p) {
        p.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 0.8f);
    }
}
