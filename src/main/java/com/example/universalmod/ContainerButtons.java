package com.example.universalmod;

import com.example.universalmod.mixin.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Панель кнопок сбоку от окна сундука/инвентаря (раньше кнопки сидели в шапке окна и налезали на название).
 * Две группы: «Сундук» (сверху) и «Инвентарь» (напротив инвентаря). У каждой кнопки — значок и подсказка.
 * Панель сама двигается вместе с окном (например, когда открывается книга рецептов).
 */
public class ContainerButtons {
    private static final int B = 18, GAP = 2;

    private record Btn(ButtonWidget w, int group, int index, ItemStack icon, String glyph) { }

    private static final List<Btn> buttons = new ArrayList<>();
    private static Screen owner = null;
    private static final java.util.Set<Screen> HOOKED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!(screen instanceof HandledScreen<?> hs)) return;
            boolean container = InventorySort.isContainerScreen(screen);
            boolean inv = screen instanceof InventoryScreen;
            if (!container && !inv) return;
            buttons.clear();
            owner = screen;

            if (container && ClickerConfig.sortButtons) {
                add(screen, 0, new ItemStack(Items.HOPPER), "", I18n.t("Сортировать сундук по столбцам"),
                        () -> InventorySort.start(client, true, false));
                add(screen, 0, new ItemStack(Items.COMPARATOR), "", I18n.t("Сортировать сундук по строкам"),
                        () -> InventorySort.start(client, true, true));
                add(screen, 0, ItemStack.EMPTY, "⬇", I18n.t("Забрать всё из сундука в инвентарь"),
                        () -> InventorySort.startMove(client, true, false));
                add(screen, 0, ItemStack.EMPTY, "⬇=", I18n.t("Забрать из сундука всё такое же, как в руке"),
                        () -> InventorySort.startMove(client, true, true));
                add(screen, 0, new ItemStack(Items.LAVA_BUCKET), "", I18n.t("Выбросить всё из сундука (с предметом в руке — только такие же). Осторожно!"),
                        () -> InventorySort.startDrop(client));
            }
            if (container || inv) {
                add(screen, 0, new ItemStack(Items.NETHER_STAR), "", I18n.t("Забрать ценное в его слоты (система ценности)"),
                        () -> ValueSystem.start(client, true));
            }
            if (ClickerConfig.sortInvButtons) {
                add(screen, 1, new ItemStack(Items.HOPPER), "", I18n.t("Сортировать инвентарь по столбцам"),
                        () -> InventorySort.start(client, false, false));
                add(screen, 1, new ItemStack(Items.COMPARATOR), "", I18n.t("Сортировать инвентарь по строкам"),
                        () -> InventorySort.start(client, false, true));
                if (container) {
                    add(screen, 1, ItemStack.EMPTY, "⬆", I18n.t("Положить всё из инвентаря в сундук"),
                            () -> InventorySort.startMove(client, false, false));
                    add(screen, 1, ItemStack.EMPTY, "⬆=", I18n.t("Положить в сундук всё такое же, как в руке"),
                            () -> InventorySort.startMove(client, false, true));
                }
            }
            place(hs, w);

            // окно может сдвинуться (книга рецептов) — двигаем панель каждый кадр.
            // Слушатели вешаем на окно один раз (init вызывается и при смене размера окна).
            if (!HOOKED.add(screen)) return;
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, d) -> {
                if (s == owner) place((HandledScreen<?>) s, s.width);
            });
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, d) -> {
                if (s == owner) drawIcons(ctx);
            });
        });
    }

    private static void add(Screen screen, int group, ItemStack icon, String glyph, String tip, Runnable action) {
        int index = 0;
        for (Btn b : buttons) if (b.group() == group) index++;
        ButtonWidget w = ButtonWidget.builder(Text.literal(""), b -> action.run())
                .dimensions(0, 0, B, B).tooltip(Tooltip.of(Text.literal(tip))).build();
        Screens.getButtons(screen).add(w);
        buttons.add(new Btn(w, group, index, icon, glyph));
    }

    /** Поставить кнопки сбоку от окна: справа, а если не помещается — слева. */
    private static void place(HandledScreen<?> hs, int screenW) {
        HandledScreenAccessor a = (HandledScreenAccessor) hs;
        int gx = a.universalmod$getX(), gy = a.universalmod$getY();
        int gw = a.universalmod$getBgWidth(), gh = a.universalmod$getBgHeight();
        int x = gx + gw + 3;
        if (x + B > screenW - 2) x = gx - B - 3;
        int invTop = gy + gh - 83; // верх основного инвентаря (3 ряда + хотбар)
        int n0 = 0;
        for (Btn b : buttons) if (b.group() == 0) n0++;
        invTop = Math.max(invTop, gy + 4 + n0 * (B + GAP) + 8); // группы не налезают друг на друга
        for (Btn b : buttons) {
            int y = b.group() == 0 ? gy + 4 + b.index() * (B + GAP) : invTop + b.index() * (B + GAP);
            b.w().setX(x);
            b.w().setY(y);
        }
    }

    private static void drawIcons(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (Btn b : buttons) {
            ButtonWidget w = b.w();
            if (!w.visible) continue;
            if (!b.icon().isEmpty()) {
                ctx.drawItem(b.icon(), w.getX() + 1, w.getY() + 1);
            } else {
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(b.glyph()), w.getX() + B / 2, w.getY() + 5, 0xFFFFFFFF);
            }
        }
    }
}
