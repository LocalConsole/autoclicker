package com.example.universalmod;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сортировка контейнера и инвентаря (в духе Inventory Profiles Next): кнопки в окне контейнера и клавиши.
 * Порядок заполнения: по горизонтали (строка за строкой) или по вертикали (столбец за столбцом).
 * Всё делается обычными кликами по слотам, поэтому работает и на серверах.
 */
public class InventorySort {
    /** Индекс первой клавиши сортировки в UniversalMod.keys(). */
    public static final int KEY_BASE = 19;

    /** Размер окна своего инвентаря (стандартная текстура 176×166). */
    private static final int INV_W = 176;
    private static final int INV_H = 166;

    private static final ArrayDeque<Integer> QUEUE = new ArrayDeque<>();
    private static ScreenHandler taskHandler = null;
    private static final ArrayDeque<Integer> DROPS = new ArrayDeque<>();
    private static ScreenHandler dropHandler = null;
    private static final boolean[] wasDown = new boolean[4];

    public static void register() {
        ContainerButtons.register();
    }

    public static void startDrop(MinecraftClient client) {
        if (client.player == null || busy()) return;
        if (!isContainerScreen(client.currentScreen)) return;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || !handler.getCursorStack().isEmpty()) return;

        ItemStack held = client.player.getMainHandStack();
        Item filter = held.isEmpty() ? null : held.getItem();

        for (Slot sl : handler.slots) {
            if (sl.inventory == client.player.getInventory()) continue;
            ItemStack st = sl.getStack();
            if (st.isEmpty() || (filter != null && st.getItem() != filter)) continue;
            if (!sl.canTakeItems(client.player)) continue;
            DROPS.add(sl.id);
        }
        if (!DROPS.isEmpty()) dropHandler = handler;
    }

    public static boolean isContainerScreen(Screen s) {
        return s instanceof GenericContainerScreen || s instanceof ShulkerBoxScreen;
    }

    // ---------- Тик: выполнение очереди и клавиши ----------

    public static void tick(MinecraftClient client) {
        if (client.player == null || client.interactionManager == null) {
            QUEUE.clear();
            DROPS.clear();
            return;
        }
        if (!DROPS.isEmpty()) {
            if (dropHandler == null || client.player.currentScreenHandler != dropHandler) {
                DROPS.clear();
            } else {
                for (int i = 0; i < ClickerConfig.dropSpeed && !DROPS.isEmpty(); i++) {
                    // кнопка 1 + THROW = выбросить весь стак (как Ctrl+Q)
                    client.interactionManager.clickSlot(dropHandler.syncId, DROPS.poll(), 1,
                            SlotActionType.THROW, client.player);
                }
            }
        }
        if (sortHandler != null) {
            if (client.player.currentScreenHandler != sortHandler) {
                stopSort(client, null);
            } else {
                boolean more = true;
                for (int i = 0; i < Math.max(1, ClickerConfig.sortSpeed / 2) && more; i++) more = sortStep(client);
                if (!more) stopSort(client, I18n.t("Отсортировано"));
            }
        }
        if (!QUEUE.isEmpty()) {
            if (taskHandler == null || client.player.currentScreenHandler != taskHandler) {
                QUEUE.clear();
            } else {
                for (int i = 0; i < ClickerConfig.sortSpeed && !QUEUE.isEmpty(); i++) {
                    client.interactionManager.clickSlot(taskHandler.syncId, QUEUE.poll(), 0,
                            SlotActionType.PICKUP, client.player);
                }
                if (QUEUE.isEmpty()) putBackCursor(client, taskHandler, null);
            }
        }

        boolean inScreen = client.currentScreen instanceof HandledScreen<?>;
        long window = client.getWindow().getHandle();
        KeyBinding[] keys = UniversalMod.keys();
        for (int i = 0; i < 4; i++) {
            boolean d = inScreen && down(keys[KEY_BASE + i], window);
            if (d && !wasDown[i]) start(client, i < 2, i % 2 == 0);
            wasDown[i] = d;
        }
    }

    private static boolean down(KeyBinding kb, long window) {
        if (kb.isUnbound()) return false;
        String tk = kb.getBoundKeyTranslationKey();
        int code = InputUtil.fromTranslationKey(tk).getCode();
        if (tk.startsWith("key.mouse")) return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }

    // ---------- Запуск ----------

    // ---------- Сортировка «по шагам» ----------
    // Раньше весь список кликов считался заранее; если сервер вёл себя чуть иначе (не тот слот, лаг),
    // всё дальше шло вкривь и вкось. Теперь каждый шаг смотрит на реальное содержимое слотов.

    private static ScreenHandler sortHandler = null;
    private static List<Slot> sortSlots = null;
    private static int[] sortOrd = null;
    private static List<ItemStack> sortWant = null;
    private static boolean merging = true;
    private static int sortSteps = 0;

    public static boolean busy() {
        return sortHandler != null || !QUEUE.isEmpty() || !DROPS.isEmpty();
    }

    /** horizontal=true: заполнять строка за строкой; false: столбец за столбцом. */
    public static void start(MinecraftClient client, boolean container, boolean horizontal) {
        if (client.player == null || busy()) return;
        if (!(client.currentScreen instanceof HandledScreen<?>)) return;
        if (container && !isContainerScreen(client.currentScreen)) return;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || !handler.getCursorStack().isEmpty()) return;

        List<Slot> slots = new ArrayList<>();
        for (Slot s : handler.slots) {
            boolean mine = s.inventory == client.player.getInventory();
            if (container && !mine) {
                slots.add(s);
            } else if (!container && mine && s.getIndex() >= (ClickerConfig.sortIncludeHotbar ? 0 : 9) && s.getIndex() <= 35) {
                slots.add(s);
            }
        }
        if (slots.isEmpty() || slots.size() % 9 != 0) return;
        int n = slots.size(), rows = n / 9;
        int[] ord = new int[n];
        int k = 0;
        if (horizontal) {
            for (int i = 0; i < n; i++) ord[k++] = i;
        } else {
            for (int c = 0; c < 9; c++) for (int r = 0; r < rows; r++) ord[k++] = r * 9 + c;
        }
        sortHandler = handler;
        sortSlots = slots;
        sortOrd = ord;
        sortWant = null;
        merging = true;
        sortSteps = 0;
    }

    private static void stopSort(MinecraftClient client, String msg) {
        if (sortHandler != null && client.player != null && client.player.currentScreenHandler == sortHandler) {
            putBackCursor(client, sortHandler, sortSlots);
        }
        sortHandler = null;
        sortSlots = null;
        sortWant = null;
        if (msg != null && client.player != null) client.player.sendMessage(Text.literal(msg), true);
    }

    /** Если в «руке» (курсоре) что-то осталось — кладём в первый пустой слот, чтобы ничего не потерялось. */
    private static void putBackCursor(MinecraftClient client, ScreenHandler h, List<Slot> prefer) {
        if (h.getCursorStack().isEmpty()) return;
        if (prefer != null) {
            for (Slot s : prefer) {
                if (!s.hasStack() && s.canInsert(h.getCursorStack())) {
                    client.interactionManager.clickSlot(h.syncId, s.id, 0, SlotActionType.PICKUP, client.player);
                    if (h.getCursorStack().isEmpty()) return;
                }
            }
        }
        for (Slot s : h.slots) {
            if (s.inventory == client.player.getInventory() && !s.hasStack() && s.getIndex() < 36) {
                client.interactionManager.clickSlot(h.syncId, s.id, 0, SlotActionType.PICKUP, client.player);
                if (h.getCursorStack().isEmpty()) return;
            }
        }
    }

    private static void pick(MinecraftClient client, Slot s) {
        client.interactionManager.clickSlot(sortHandler.syncId, s.id, 0, SlotActionType.PICKUP, client.player);
    }

    /** Один шаг сортировки. false — всё готово (или прервано). */
    private static boolean sortStep(MinecraftClient client) {
        List<Slot> slots = sortSlots;
        int n = slots.size();
        if (!sortHandler.getCursorStack().isEmpty()) {
            putBackCursor(client, sortHandler, slots);
            if (!sortHandler.getCursorStack().isEmpty()) {
                stopSort(client, I18n.t("Сортировка остановлена: некуда положить предмет"));
                return false;
            }
        }
        if (merging) {
            // склеиваем неполные одинаковые стаки
            for (int a = 0; a < n; a++) {
                Slot si = slots.get(sortOrd[a]);
                ItemStack x = si.getStack();
                if (x.isEmpty() || x.getCount() >= x.getMaxCount()) continue;
                for (int b = a + 1; b < n; b++) {
                    Slot sj = slots.get(sortOrd[b]);
                    ItemStack y = sj.getStack();
                    if (y.isEmpty() || !ItemStack.areItemsAndComponentsEqual(x, y)) continue;
                    pick(client, sj);
                    pick(client, si);
                    if (!sortHandler.getCursorStack().isEmpty()) pick(client, sj);
                    return true;
                }
            }
            merging = false;
            sortWant = new ArrayList<>();
            for (Slot s : slots) if (s.hasStack()) sortWant.add(s.getStack().copy());
            sortWant.sort(Comparator
                    .comparing((ItemStack s) -> Registries.ITEM.getId(s.getItem()).toString())
                    .thenComparing(s -> s.getName().getString())
                    .thenComparingInt(s -> -s.getCount()));
        }
        for (int p = 0; p < n; p++) {
            Slot dst = slots.get(sortOrd[p]);
            ItemStack w = p < sortWant.size() ? sortWant.get(p) : ItemStack.EMPTY;
            if (w.isEmpty()) return false; // дальше только пустые — готово
            if (ItemStack.areEqual(dst.getStack(), w)) continue;
            Slot src = null;
            for (int q = p + 1; q < n; q++) {
                Slot c = slots.get(sortOrd[q]);
                if (ItemStack.areEqual(c.getStack(), w)) { src = c; break; }
            }
            if (src == null) {
                // содержимое изменилось (сервер, другой игрок) — пересчитываем порядок
                merging = true;
                return ++sortSteps < 2000;
            }
            pick(client, src);
            pick(client, dst);
            if (!sortHandler.getCursorStack().isEmpty()) pick(client, src);
            return ++sortSteps < 2000;
        }
        return false;
    }

    // ---------- Перенос между контейнером и инвентарём ----------

    /**
     * Переносит предметы между контейнером и основным инвентарём (27 слотов).
     * Хотбар и вторая рука не затрагиваются ни как источник, ни как цель.
     * sameAsHeld: переносить только предметы того же вида, что в руке (выбранный слот хотбара).
     */
    public static void startMove(MinecraftClient client, boolean fromContainer, boolean sameAsHeld) {
        if (client.player == null || busy()) return;
        if (!isContainerScreen(client.currentScreen)) return;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || !handler.getCursorStack().isEmpty()) return;

        Item filter = null;
        if (sameAsHeld) {
            ItemStack held = client.player.getMainHandStack();
            if (held.isEmpty()) {
                client.player.sendMessage(Text.literal(I18n.t("Возьмите предмет в руку")), true);
                return;
            }
            filter = held.getItem();
        }

        List<Slot> containerSlots = new ArrayList<>();
        List<Slot> invSlots = new ArrayList<>();
        for (Slot sl : handler.slots) {
            if (sl.inventory == client.player.getInventory()) {
                if (sl.getIndex() >= (ClickerConfig.sortIncludeHotbar ? 0 : 9) && sl.getIndex() <= 35) invSlots.add(sl);
            } else {
                containerSlots.add(sl);
            }
        }

        List<Integer> plan = planMove(client, fromContainer ? containerSlots : invSlots,
                fromContainer ? invSlots : containerSlots, filter);
        if (plan.isEmpty()) return;
        taskHandler = handler;
        QUEUE.addAll(plan);
    }

    private static List<Integer> planMove(MinecraftClient client, List<Slot> from, List<Slot> to, Item filter) {
        int nf = from.size();
        List<Slot> all = new ArrayList<>(from);
        all.addAll(to);
        ItemStack[] sim = new ItemStack[all.size()];
        for (int i = 0; i < sim.length; i++) sim[i] = all.get(i).getStack().copy();
        ItemStack[] cursor = {EMPTY};
        List<Integer> clicks = new ArrayList<>();

        for (int i = 0; i < nf; i++) {
            ItemStack st = sim[i];
            if (st.isEmpty() || (filter != null && st.getItem() != filter)) continue;
            if (!all.get(i).canTakeItems(client.player)) continue;

            // Куда можно положить: сначала доукомплектовать такие же стаки, потом пустой слот
            List<Integer> merge = new ArrayList<>();
            int empty = -1;
            for (int j = nf; j < all.size(); j++) {
                if (!all.get(j).canInsert(st)) continue;
                if (sim[j].isEmpty()) {
                    if (empty < 0) empty = j;
                } else if (sim[j].getCount() < sim[j].getMaxCount()
                        && ItemStack.areItemsAndComponentsEqual(sim[j], st)) {
                    merge.add(j);
                }
            }
            if (merge.isEmpty() && empty < 0) continue;

            clicks.add(all.get(i).id);
            click(sim, cursor, i);
            for (int j : merge) {
                if (cursor[0].isEmpty()) break;
                clicks.add(all.get(j).id);
                click(sim, cursor, j);
            }
            if (!cursor[0].isEmpty()) {
                int target = -1;
                for (int j = nf; j < all.size(); j++) {
                    if (sim[j].isEmpty() && all.get(j).canInsert(cursor[0])) { target = j; break; }
                }
                // Нет места: остаток возвращаем на прежнее место
                if (target < 0) target = i;
                clicks.add(all.get(target).id);
                click(sim, cursor, target);
            }
            if (clicks.size() > 600) break;
        }

        if (!cursor[0].isEmpty()) return new ArrayList<>();
        return clicks;
    }

    // ---------- Планирование кликов ----------

    private static final ItemStack EMPTY = ItemStack.EMPTY;

    private static void click(ItemStack[] sim, ItemStack[] cursor, int idx) {
        ItemStack cur = cursor[0];
        ItemStack s = sim[idx];
        if (cur.isEmpty()) {
            if (!s.isEmpty()) {
                cursor[0] = s;
                sim[idx] = EMPTY;
            }
        } else if (s.isEmpty()) {
            sim[idx] = cur;
            cursor[0] = EMPTY;
        } else if (ItemStack.areItemsAndComponentsEqual(cur, s)) {
            int move = Math.min(cur.getCount(), s.getMaxCount() - s.getCount());
            if (move > 0) {
                ItemStack n = s.copy();
                n.increment(move);
                sim[idx] = n;
                ItemStack c2 = cur.copy();
                c2.decrement(move);
                cursor[0] = c2.isEmpty() ? EMPTY : c2;
            }
        } else {
            sim[idx] = cur;
            cursor[0] = s;
        }
    }
}
