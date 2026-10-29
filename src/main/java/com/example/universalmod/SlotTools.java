package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Перемещение предметов мышью в стиле Mouse Tweaks и Item Scroller:
 * колёсико над слотом переносит предметы по одному, Shift + колёсико переносит весь стак,
 * Shift + зажатая ЛКМ или ПКМ и проведение по слотам быстро переносит их, Ctrl + Shift + клик переносит все такие же предметы.
 */
public class SlotTools {
    /** Слот под курсором: обновляется каждый кадр из HandledScreenMixin. */
    public static Slot hovered = null;

    private static boolean dragPrev = false;
    private static int lastSlot = -1;

    // ---------- вспомогательное ----------

    private static boolean keyDown(MinecraftClient mc, int key) {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
    }

    private static boolean shiftDown(MinecraftClient mc) {
        return keyDown(mc, GLFW.GLFW_KEY_LEFT_SHIFT) || keyDown(mc, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean ctrlDown(MinecraftClient mc) {
        return keyDown(mc, GLFW.GLFW_KEY_LEFT_CONTROL) || keyDown(mc, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    /** 0 — контейнер (всё, что не инвентарь игрока), 1 — основной инвентарь, 2 — хотбар, -1 — броня и вторая рука. */
    private static int groupOf(MinecraftClient mc, Slot s) {
        if (s.inventory == mc.player.getInventory()) {
            int i = s.getIndex();
            if (i >= 9 && i <= 35) return 1;
            if (i >= 0 && i <= 8) return 2;
            return -1;
        }
        return 0;
    }

    private static boolean hasGroup(MinecraftClient mc, ScreenHandler h, int g) {
        for (Slot s : h.slots) if (groupOf(mc, s) == g) return true;
        return false;
    }

    private static void click(MinecraftClient mc, ScreenHandler h, int slotId, int button, SlotActionType type) {
        mc.interactionManager.clickSlot(h.syncId, slotId, button, type, mc.player);
    }

    /** Можно ли положить в слот ещё один предмет такого типа. */
    private static boolean acceptsOne(Slot s, ItemStack stack) {
        if (!s.canInsert(stack)) return false;
        ItemStack in = s.getStack();
        if (in.isEmpty()) return true;
        return ItemStack.areItemsAndComponentsEqual(in, stack) && in.getCount() < in.getMaxCount();
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.areItemsAndComponentsEqual(a, b);
    }

    /** Группы, куда переносим предметы из группы g (в порядке приоритета). */
    private static int[] otherGroups(MinecraftClient mc, ScreenHandler h, int g) {
        boolean container = hasGroup(mc, h, 0);
        if (g == 0) return new int[]{1, 2};
        if (g == 1) return container ? new int[]{0} : new int[]{2};
        return container ? new int[]{0} : new int[]{1};
    }

    /** Подходящий слот в группах: сначала с такими же предметами, потом пустой. */
    private static Slot findTarget(MinecraftClient mc, ScreenHandler h, int[] groups, ItemStack stack, Slot skip) {
        for (int pass = 0; pass < 2; pass++) {
            for (int g : groups) {
                for (Slot s : h.slots) {
                    if (s == skip || groupOf(mc, s) != g) continue;
                    if (!acceptsOne(s, stack)) continue;
                    boolean empty = s.getStack().isEmpty();
                    if (pass == 0 && !empty) return s;
                    if (pass == 1 && empty) return s;
                }
            }
        }
        return null;
    }

    /** Перенести один предмет из from в to: взять стак, положить один ПКМ, остаток вернуть. */
    private static void moveOne(MinecraftClient mc, ScreenHandler h, Slot from, Slot to) {
        click(mc, h, from.id, 0, SlotActionType.PICKUP);
        click(mc, h, to.id, 1, SlotActionType.PICKUP);
        if (!h.getCursorStack().isEmpty()) click(mc, h, from.id, 0, SlotActionType.PICKUP);
    }

    // ---------- колёсико ----------

    /** Вызывается из MouseMixin. true — прокрутка использована. */
    public static boolean scrollHook(double vertical) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!ClickerConfig.slotWheel || vertical == 0 || mc.player == null || mc.interactionManager == null) return false;
            if (!(mc.currentScreen instanceof HandledScreen<?>) || mc.currentScreen instanceof CreativeInventoryScreen) return false;
            ScreenHandler h = mc.player.currentScreenHandler;
            Slot s = hovered;
            if (h == null || s == null || s.id < 0 || s.id >= h.slots.size() || h.slots.get(s.id) != s) return false;
            if (!h.getCursorStack().isEmpty()) return false;

            int g = groupOf(mc, s);
            if (g < 0) return false;
            boolean up = vertical > 0;

            if (shiftDown(mc)) {
                if (up && s.hasStack() && s.canTakeItems(mc.player)) {
                    click(mc, h, s.id, 0, SlotActionType.QUICK_MOVE);
                    return true;
                }
                return false;
            }

            if (up) {
                // из слота под курсором — один предмет в другой инвентарь
                if (!s.hasStack() || !s.canTakeItems(mc.player)) return false;
                Slot to = findTarget(mc, h, otherGroups(mc, h, g), s.getStack(), s);
                if (to == null) return false;
                moveOne(mc, h, s, to);
                return true;
            }

            // вниз: из другого инвентаря — один такой же предмет в этот
            if (!s.hasStack()) return false;
            ItemStack want = s.getStack().copy();
            Slot dest = acceptsOne(s, want) ? s : findTarget(mc, h, new int[]{g}, want, s);
            if (dest == null) return false;
            for (int og : otherGroups(mc, h, g)) {
                for (Slot src : h.slots) {
                    if (src == s || src == dest || groupOf(mc, src) != og) continue;
                    if (!sameItem(src.getStack(), want) || !src.canTakeItems(mc.player)) continue;
                    moveOne(mc, h, src, dest);
                    return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------- Shift + зажатая ЛКМ: провести по слотам ----------

    /** Каждый кадр из HandledScreen.render. */
    public static void frame(Slot focused) {
        hovered = focused;
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!ClickerConfig.slotDrag || mc.player == null || mc.interactionManager == null) {
                dragPrev = false;
                return;
            }
            long win = mc.getWindow().getHandle();
            boolean lmb = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
            if (!lmb || !shiftDown(mc) || ctrlDown(mc)) {
                dragPrev = false;
                lastSlot = -1;
                return;
            }
            ScreenHandler h = mc.player.currentScreenHandler;
            if (h == null || !h.getCursorStack().isEmpty()) return;
            int id = focused == null ? -1 : focused.id;
            if (!dragPrev) {
                // первый слот переносит сама игра (Shift + клик)
                dragPrev = true;
                lastSlot = id;
                return;
            }
            if (focused == null || id == lastSlot) return;
            lastSlot = id;
            if (!focused.hasStack() || !focused.canTakeItems(mc.player)) return;
            click(mc, h, id, 0, SlotActionType.QUICK_MOVE);
        } catch (Throwable ignored) {
        }
    }

    // ---------- Ctrl + Shift + клик: все такие же предметы ----------

    /** Вызывается из HandledScreenMixin при клике. true — клик обработан. */
    public static boolean clickAll(Slot s, int button) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!ClickerConfig.slotMatchAll || button != 0 || s == null || mc.player == null || mc.interactionManager == null) return false;
            if (!shiftDown(mc) || !ctrlDown(mc)) return false;
            if (mc.currentScreen instanceof CreativeInventoryScreen) return false;
            ScreenHandler h = mc.player.currentScreenHandler;
            if (h == null || !h.getCursorStack().isEmpty() || !s.hasStack()) return false;
            int g = groupOf(mc, s);
            if (g < 0) return false;
            ItemStack want = s.getStack().copy();
            List<Slot> same = new ArrayList<>();
            for (Slot o : h.slots) {
                if (groupOf(mc, o) == g && sameItem(o.getStack(), want) && o.canTakeItems(mc.player)) same.add(o);
            }
            for (Slot o : same) click(mc, h, o.id, 0, SlotActionType.QUICK_MOVE);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
