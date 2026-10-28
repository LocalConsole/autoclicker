package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import org.lwjgl.glfw.GLFW;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Автозаполнение: когда предмет в слоте хотбара или во второй руке закончился (израсходован, сломался, брошен
 * в мир), на его место из основного инвентаря ставится такой же предмет. Остальные слоты хотбара не трогаются.
 * Делается одним кликом «обмен» (как при нажатии цифры над слотом), поэтому работает и на серверах.
 */
public class AutoRefill {
    private static final int OFFHAND = 9; // индекс «слота» второй руки в массивах ниже
    private static final int OFFHAND_BUTTON = 40; // кнопка обмена со второй рукой

    private static final ItemStack[] prev = new ItemStack[10];
    private static final int[] prevTotal = new int[10];
    private static final int[] cooldown = new int[10];
    private static boolean prevDrop = false;
    private static boolean ready = false;

    public static void reset() {
        ready = false;
    }

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            reset();
            return;
        }
        boolean enabled = ClickerConfig.refillHotbar || ClickerConfig.refillOffhand;
        boolean drop = client.options.dropKey.isPressed();
        boolean canAct = enabled && ready && client.currentScreen == null && !client.player.isCreative()
                && !client.player.isSpectator() && !drop && !prevDrop
                && client.player.currentScreenHandler == client.player.playerScreenHandler;

        for (int t = 0; t < 10; t++) {
            ItemStack cur = stackAt(client, t);
            ItemStack before = prev[t];
            if (cooldown[t] > 0) cooldown[t]--;
            boolean slotOn = ClickerConfig.refillSlots[t]
                    && (t == OFFHAND ? ClickerConfig.refillOffhand : ClickerConfig.refillHotbar);
            if (canAct && slotOn && before != null && !before.isEmpty() && cur.isEmpty()
                    && count(client, before.getItem()) < prevTotal[t]) {
                if (refill(client, t, before)) cur = stackAt(client, t);
            } else if (canAct && slotOn && !cur.isEmpty() && cur.getMaxCount() > 1
                    && cur.getCount() < ClickerConfig.refillMin && cooldown[t] == 0) {
                // Осталось меньше заданного: доливаем из инвентаря, остаток не уходит в отдельный слот
                cooldown[t] = 10;
                if (topUp(client, t, cur)) cur = stackAt(client, t);
            }
            prev[t] = cur.copy();
            prevTotal[t] = cur.isEmpty() ? 0 : count(client, cur.getItem());
        }
        prevDrop = drop;
        ready = true;
    }

    private static ItemStack stackAt(MinecraftClient client, int t) {
        return t == OFFHAND ? client.player.getOffHandStack() : client.player.getInventory().getStack(t);
    }

    /** Сколько предметов этого вида во всём инвентаре (основной + хотбар + вторая рука). */
    private static int count(MinecraftClient client, Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = client.player.getInventory().getStack(i);
            if (s.getItem() == item) n += s.getCount();
        }
        ItemStack off = client.player.getOffHandStack();
        if (off.getItem() == item) n += off.getCount();
        return n;
    }

    private static boolean refill(MinecraftClient client, int target, ItemStack wanted) {
        ScreenHandler handler = client.player.playerScreenHandler;
        Slot best = null;
        for (Slot s : handler.slots) {
            if (s.inventory != client.player.getInventory()) continue;
            int idx = s.getIndex();
            if (idx < 9 || idx > 35) continue; // только основной инвентарь
            ItemStack st = s.getStack();
            if (st.isEmpty() || st.getItem() != wanted.getItem()) continue;
            if (ItemStack.areItemsAndComponentsEqual(st, wanted)) { best = s; break; }
            if (best == null) best = s;
        }
        if (best == null) return false;
        client.interactionManager.clickSlot(handler.syncId, best.id,
                target == OFFHAND ? OFFHAND_BUTTON : target, SlotActionType.SWAP, client.player);
        return true;
    }

    /** Доложить в слот предметы из основного инвентаря: стаки сливаются, остаток остаётся в инвентаре. */
    private static boolean topUp(MinecraftClient client, int target, ItemStack cur) {
        ScreenHandler handler = client.player.playerScreenHandler;
        int src = -1;
        for (int id = 9; id <= 35; id++) {
            ItemStack st = handler.getSlot(id).getStack();
            if (!st.isEmpty() && ItemStack.areItemsAndComponentsEqual(st, cur)) { src = id; break; }
        }
        if (src < 0) return false;
        int dst = target == OFFHAND ? 45 : 36 + target;
        client.interactionManager.clickSlot(handler.syncId, src, 0, SlotActionType.PICKUP, client.player);
        client.interactionManager.clickSlot(handler.syncId, dst, 0, SlotActionType.PICKUP, client.player);
        if (!handler.getCursorStack().isEmpty()) {
            client.interactionManager.clickSlot(handler.syncId, src, 0, SlotActionType.PICKUP, client.player);
        }
        return true;
    }

    // ---------- Включение по слотам: Ctrl + клик ----------

    /** Индекс слота автозаполнения (0..8 хотбар, 9 вторая рука) для слота в окне, либо -1. */
    private static int targetOf(MinecraftClient client, Slot slot) {
        if (slot == null || client.player == null) return -1;
        if (slot.inventory == client.player.getInventory() && slot.getIndex() >= 0 && slot.getIndex() <= 8) {
            return slot.getIndex();
        }
        if (client.player.currentScreenHandler instanceof PlayerScreenHandler && slot.id == 45) return OFFHAND;
        return -1;
    }

    private static boolean ctrlDown(MinecraftClient client) {
        long w = client.getWindow().getHandle();
        return GLFW.glfwGetKey(w, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(w, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /** Ctrl + клик по слоту хотбара или второй руки включает/выключает для него автозаполнение. true = клик обработан. */
    public static boolean tryToggle(Slot slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ctrlDown(client)) return false;
        int t = targetOf(client, slot);
        if (t < 0) return false;
        ClickerConfig.refillSlots[t] = !ClickerConfig.refillSlots[t];
        ClickerConfig.save();
        String name = t == OFFHAND ? I18n.t("Вторая рука") : I18n.t("Слот ") + (t + 1);
        client.player.sendMessage(Text.literal(I18n.t("Автозаполнение") + ": " + name + " — "
                + (ClickerConfig.refillSlots[t] ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ"))), true);
        return true;
    }

    /** Фиолетовая отметка в левом верхнем углу слота (x, y — левый верхний угол предмета 16x16). */
    public static void drawMark(DrawContext context, int x, int y) {
        context.fill(x - 1, y - 1, x + 6, y + 6, 0xFF1B0030);
        context.fill(x, y, x + 5, y + 5, 0xFFC63CFF);
    }

    /** Отметки на хотбаре в игре. */
    public static void registerHud() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "refill_marks"), (context, tickCounter) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.options.hudHidden) return;
                    int sw = context.getScaledWindowWidth();
                    int sh = context.getScaledWindowHeight();
                    int y = sh - 19;
                    for (int i = 0; i < 9; i++) {
                        if (ClickerConfig.refillSlots[i]) drawMark(context, sw / 2 - 90 + i * 20, y);
                    }
                    if (ClickerConfig.refillSlots[OFFHAND] && !client.player.getOffHandStack().isEmpty()) {
                        boolean left = client.options.getMainArm().getValue() == Arm.LEFT;
                        drawMark(context, left ? sw / 2 + 101 : sw / 2 - 117, y);
                    }
                });
    }

    /** Отметки в открытом окне: x0, y0 — левый верхний угол окна. */
    public static void drawScreenMarks(DrawContext context, ScreenHandler handler, int x0, int y0) {
        MinecraftClient client = MinecraftClient.getInstance();
        for (Slot s : handler.slots) {
            int t = targetOf(client, s);
            if (t >= 0 && ClickerConfig.refillSlots[t]) drawMark(context, x0 + s.x, y0 + s.y);
        }
    }
}
