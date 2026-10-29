package com.example.universalmod;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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

    private static final ArrayDeque<Integer> QUEUE = new ArrayDeque<>();
    private static ScreenHandler taskHandler = null;
    private static final ArrayDeque<Integer> DROPS = new ArrayDeque<>();
    private static ScreenHandler dropHandler = null;
    private static final boolean[] wasDown = new boolean[4];

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!isContainerScreen(screen) || !(screen instanceof HandledScreen<?> hs)) return;
            int rows = (hs.getScreenHandler().slots.size() - 36) / 9;
            if (rows <= 0) return;
            int bh = 114 + rows * 18;
            int left = (w - 176) / 2;
            int top = (h - bh) / 2;
            if (ClickerConfig.sortButtons) {
                addPair(screen, client, left + 176 - 32, top + 4, true);
                addMovePair(screen, client, left + 108, top + 4, true);
                addDropButton(screen, client, left + 90, top + 4);
            }
            if (ClickerConfig.sortInvButtons) {
                addPair(screen, client, left + 176 - 32, top + bh - 97, false);
                addMovePair(screen, client, left + 108, top + bh - 97, false);
            }
        });
    }

    private static void addPair(Screen screen, MinecraftClient client, int x, int y, boolean container) {
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("↓"),
                        b -> start(client, container, false))
                .dimensions(x, y, 12, 12)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Сортировать по вертикали"))))
                .build());
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("→"),
                        b -> start(client, container, true))
                .dimensions(x + 14, y, 12, 12)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Сортировать по горизонтали"))))
                .build());
    }

    /** Две кнопки переноса: «всё» и «только как предмет в руке». fromContainer: из контейнера в инвентарь, иначе наоборот. */
    private static void addMovePair(Screen screen, MinecraftClient client, int x, int y, boolean fromContainer) {
        String arrow = fromContainer ? "▼" : "▲";
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(arrow),
                        b -> startMove(client, fromContainer, false))
                .dimensions(x, y, 16, 12)
                .tooltip(Tooltip.of(Text.literal(I18n.t(fromContainer
                        ? "Забрать всё из контейнера в инвентарь"
                        : "Положить всё из инвентаря в контейнер"))))
                .build());
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(arrow + "="),
                        b -> startMove(client, fromContainer, true))
                .dimensions(x + 18, y, 16, 12)
                .tooltip(Tooltip.of(Text.literal(I18n.t(fromContainer
                        ? "Забрать все такие же предметы, как в руке"
                        : "Положить все такие же предметы, как в руке"))))
                .build());
    }

    /** Выбросить предметы из контейнера на землю. С предметом в руке — только такие же. */
    private static void addDropButton(Screen screen, MinecraftClient client, int x, int y) {
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("\u2715"),
                        b -> startDrop(client))
                .dimensions(x, y, 16, 12)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Выбросить всё из контейнера (с предметом в руке — только такие же)"))))
                .build());
    }

    public static void startDrop(MinecraftClient client) {
        if (client.player == null || !QUEUE.isEmpty() || !DROPS.isEmpty()) return;
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

    private static boolean isContainerScreen(Screen s) {
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
        if (!QUEUE.isEmpty()) {
            if (taskHandler == null || client.player.currentScreenHandler != taskHandler) {
                QUEUE.clear();
            } else {
                for (int i = 0; i < ClickerConfig.sortSpeed && !QUEUE.isEmpty(); i++) {
                    client.interactionManager.clickSlot(taskHandler.syncId, QUEUE.poll(), 0,
                            SlotActionType.PICKUP, client.player);
                }
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

    /** horizontal=true: заполнять строка за строкой; false: столбец за столбцом. */
    public static void start(MinecraftClient client, boolean container, boolean horizontal) {
        if (client.player == null || !QUEUE.isEmpty()) return;
        if (!(client.currentScreen instanceof HandledScreen<?>)) return;
        if (container && !isContainerScreen(client.currentScreen)) return;
        ScreenHandler handler = client.player.currentScreenHandler;
        if (handler == null || !handler.getCursorStack().isEmpty()) return;

        List<Slot> slots = new ArrayList<>();
        for (Slot s : handler.slots) {
            boolean mine = s.inventory == client.player.getInventory();
            if (container && !mine) {
                slots.add(s);
            } else if (!container && mine && s.getIndex() >= 9 && s.getIndex() <= 35) {
                slots.add(s);
            }
        }
        if (slots.isEmpty() || slots.size() % 9 != 0) return;

        List<Integer> plan = plan(slots, horizontal);
        if (plan.isEmpty()) return;
        taskHandler = handler;
        QUEUE.addAll(plan);
    }

    // ---------- Перенос между контейнером и инвентарём ----------

    /**
     * Переносит предметы между контейнером и основным инвентарём (27 слотов).
     * Хотбар и вторая рука не затрагиваются ни как источник, ни как цель.
     * sameAsHeld: переносить только предметы того же вида, что в руке (выбранный слот хотбара).
     */
    public static void startMove(MinecraftClient client, boolean fromContainer, boolean sameAsHeld) {
        if (client.player == null || !QUEUE.isEmpty()) return;
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
                if (sl.getIndex() >= 9 && sl.getIndex() <= 35) invSlots.add(sl);
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

    private static List<Integer> plan(List<Slot> slots, boolean horizontal) {
        int n = slots.size();
        int rows = n / 9;
        ItemStack[] sim = new ItemStack[n];
        for (int i = 0; i < n; i++) sim[i] = slots.get(i).getStack().copy();
        ItemStack[] cursor = {EMPTY};

        int[] ord = new int[n];
        int k = 0;
        if (horizontal) {
            for (int i = 0; i < n; i++) ord[k++] = i;
        } else {
            for (int c = 0; c < 9; c++) for (int r = 0; r < rows; r++) ord[k++] = r * 9 + c;
        }

        List<Integer> clicks = new ArrayList<>();

        // 1) Склеиваем одинаковые стаки
        for (int a = 0; a < n; a++) {
            int i = ord[a];
            if (sim[i].isEmpty() || sim[i].getCount() >= sim[i].getMaxCount()) continue;
            for (int b = a + 1; b < n; b++) {
                int j = ord[b];
                if (sim[j].isEmpty() || !ItemStack.areItemsAndComponentsEqual(sim[i], sim[j])) continue;
                clicks.add(slots.get(j).id);
                click(sim, cursor, j);
                clicks.add(slots.get(i).id);
                click(sim, cursor, i);
                if (!cursor[0].isEmpty()) {
                    clicks.add(slots.get(j).id);
                    click(sim, cursor, j);
                }
                if (sim[i].getCount() >= sim[i].getMaxCount()) break;
            }
        }

        // 2) Нужный порядок
        List<ItemStack> want = new ArrayList<>();
        for (ItemStack s : sim) if (!s.isEmpty()) want.add(s.copy());
        want.sort(Comparator
                .comparing((ItemStack s) -> Registries.ITEM.getId(s.getItem()).toString())
                .thenComparingInt(s -> -s.getCount()));

        for (int p = 0; p < want.size(); p++) {
            int pos = ord[p];
            ItemStack w = want.get(p);
            if (ItemStack.areEqual(sim[pos], w)) continue;
            int src = -1;
            for (int q = p + 1; q < n; q++) {
                if (ItemStack.areEqual(sim[ord[q]], w)) { src = ord[q]; break; }
            }
            if (src < 0) return new ArrayList<>();
            clicks.add(slots.get(src).id);
            click(sim, cursor, src);
            clicks.add(slots.get(pos).id);
            click(sim, cursor, pos);
            if (!cursor[0].isEmpty()) {
                clicks.add(slots.get(src).id);
                click(sim, cursor, src);
            }
            if (clicks.size() > 800) return new ArrayList<>();
        }

        // Проверка: итог должен совпасть с задуманным, иначе ничего не делаем
        if (!cursor[0].isEmpty()) return new ArrayList<>();
        for (int p = 0; p < n; p++) {
            ItemStack expect = p < want.size() ? want.get(p) : EMPTY;
            if (!ItemStack.areEqual(sim[ord[p]], expect)) return new ArrayList<>();
        }
        return clicks;
    }
}
