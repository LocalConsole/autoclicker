package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Авто-копалка: следит за прочностью инструмента, пока включено «Зажать ЛКМ». */
public class AutoDig {
    public static final String[] LOW_ACTIONS = {"Выключить зажатие", "Следующий слот", "Следующий слот и выключить"};
    public static final String[] BREAK_ACTIONS = {"Остановиться", "Следующий слот"};

    private static int lastSlot = -1;
    private static boolean lastWasTool = false;
    private static int lastRemaining = 0;
    private static int cooldown = 0;

    public static void reset() {
        lastSlot = -1;
        lastWasTool = false;
        cooldown = 0;
    }

    private static int percent(ItemStack s) {
        int max = s.getMaxDamage();
        if (max <= 0) return 100;
        return (max - s.getDamage()) * 100 / max;
    }

    private static boolean healthy(ItemStack s) {
        return !s.isEmpty() && s.isDamageable() && percent(s) > ClickerConfig.digLowPercent;
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null) return;
        if (!ClickerConfig.digEnabled || !UniversalMod.isLeftHold()) {
            reset();
            return;
        }
        PlayerInventory inv = client.player.getInventory();
        int slot = inv.getSelectedSlot();
        ItemStack stack = inv.getStack(slot);
        if (cooldown > 0) cooldown--;

        // Инструмент сломался: слот тот же, а предмета уже нет
        if (slot == lastSlot && lastWasTool && stack.isEmpty() && lastRemaining <= 2) {
            lastWasTool = false;
            if (ClickerConfig.digBreakAction == 1) {
                if (!switchNext(client, inv, slot)) stop(client, "Инструмент сломан, замены нет");
            } else {
                stop(client, "Инструмент сломан");
            }
            return;
        }

        // Прочность упала ниже порога
        if (cooldown == 0 && !stack.isEmpty() && stack.isDamageable()
                && percent(stack) <= ClickerConfig.digLowPercent) {
            int act = ClickerConfig.digLowAction;
            if (act == 0) {
                stop(client, "Прочность ниже порога");
            } else if (switchNext(client, inv, slot)) {
                if (act == 2) stop(client, "Слот сменён, зажатие выключено");
            } else {
                stop(client, "Нет целого инструмента");
            }
            return;
        }

        lastSlot = inv.getSelectedSlot();
        ItemStack cur = inv.getStack(lastSlot);
        lastWasTool = !cur.isEmpty() && cur.isDamageable();
        lastRemaining = lastWasTool ? cur.getMaxDamage() - cur.getDamage() : 0;
    }

    /** Ищет вперёд по хотбару слот с целым инструментом. */
    private static boolean switchNext(MinecraftClient client, PlayerInventory inv, int from) {
        for (int i = 1; i < 9; i++) {
            int s = (from + i) % 9;
            if (healthy(inv.getStack(s))) {
                inv.setSelectedSlot(s);
                lastSlot = s;
                lastWasTool = true;
                lastRemaining = inv.getStack(s).getMaxDamage() - inv.getStack(s).getDamage();
                cooldown = 5;
                return true;
            }
        }
        return false;
    }

    private static void stop(MinecraftClient client, String reason) {
        UniversalMod.setLeftHold(false);
        reset();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(I18n.t("Авто-копалка: ") + I18n.t(reason)), true);
        }
    }
}
