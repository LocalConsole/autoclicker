package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

/**
 * Наглядный просмотр шалкера: при наведении рядом с подсказкой рисуется сетка 9×3 с самими предметами,
 * как в открытом шалкере. Сетка ставится так, чтобы не налезать на текст подсказки.
 */
public class ShulkerPreview {
    private static final int COLS = 9, ROWS = 3, CELL = 18;

    public static void draw(DrawContext ctx, Slot focused, int mouseX, int mouseY) {
        if (!ClickerConfig.shulkerPreview || !ClickerConfig.shulkerVisual) return;
        if (focused == null || !focused.hasStack()) return;
        ItemStack stack = focused.getStack();
        ContainerComponent c = stack.get(DataComponentTypes.CONTAINER);
        if (c == null) return;
        DefaultedList<ItemStack> all = DefaultedList.ofSize(COLS * ROWS, ItemStack.EMPTY);
        c.copyTo(all);
        // только занятые слоты — без пустых клеток
        java.util.List<ItemStack> items = new java.util.ArrayList<>();
        for (ItemStack s : all) if (!s.isEmpty()) items.add(s);

        MinecraftClient mc = MinecraftClient.getInstance();
        var tr = mc.textRenderer;
        int n = items.size();
        int cols = Math.max(1, Math.min(COLS, n)), rows = Math.max(1, (n + COLS - 1) / COLS);
        String title = stack.getName().getString();
        int w = Math.max(cols * CELL + 8, Math.min(COLS * CELL + 8, tr.getWidth(title) + 8));
        int h = (n == 0 ? 10 : rows * CELL) + 18;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        // над курсором (текстовая подсказка — справа-ниже от курсора), иначе — слева
        int x = mouseX + 8, y = mouseY - h - 18;
        if (y < 2) {
            x = mouseX - w - 12;
            y = Math.max(2, Math.min(sh - h - 2, mouseY - h / 2));
        }
        x = Math.max(2, Math.min(sw - w - 2, x));

        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF7A5BA8);
        ctx.fill(x, y, x + w, y + h, 0xF0140F1C);
        ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(title, w - 8)), x + 4, y + 4, 0xFFFFFFFF);
        int gx = x + 4, gy = y + 14;
        if (n == 0) {
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Пусто")), gx, gy + 1, 0xFF9AA4AE);
            return;
        }
        for (int i = 0; i < n; i++) {
            int cx = gx + (i % COLS) * CELL, cy = gy + (i / COLS) * CELL;
            ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF2A2236);
            ItemStack s = items.get(i);
            ctx.drawItem(s, cx, cy);
            ctx.drawStackOverlay(tr, s, cx, cy);
        }
    }
}
