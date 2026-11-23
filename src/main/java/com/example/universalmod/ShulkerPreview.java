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
        DefaultedList<ItemStack> items = DefaultedList.ofSize(COLS * ROWS, ItemStack.EMPTY);
        c.copyTo(items);
        boolean any = false;
        for (ItemStack s : items) if (!s.isEmpty()) { any = true; break; }

        MinecraftClient mc = MinecraftClient.getInstance();
        var tr = mc.textRenderer;
        int w = COLS * CELL + 8, h = ROWS * CELL + 18;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        // над курсором (текстовая подсказка — справа-ниже от курсора), иначе — слева
        int x = mouseX + 8, y = mouseY - h - 18;
        if (y < 2) {
            x = mouseX - w - 12;
            y = Math.max(2, Math.min(sh - h - 2, mouseY - h / 2));
        }
        x = Math.max(2, Math.min(sw - w - 2, x));

        int border = 0xFF7A5BA8;
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, border);
        ctx.fill(x, y, x + w, y + h, 0xF0140F1C);
        String title = tr.trimToWidth(stack.getName().getString(), w - 8);
        ctx.drawTextWithShadow(tr, Text.literal(title), x + 4, y + 4, 0xFFFFFFFF);
        int gx = x + 4, gy = y + 14;
        for (int i = 0; i < COLS * ROWS; i++) {
            int cx = gx + (i % COLS) * CELL, cy = gy + (i / COLS) * CELL;
            ctx.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF2A2236);
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;
            ctx.drawItem(s, cx, cy);
            ctx.drawStackOverlay(tr, s, cx, cy);
        }
        if (!any) {
            String e = I18n.t("Пусто");
            ctx.drawCenteredTextWithShadow(tr, Text.literal(e), x + w / 2, gy + ROWS * CELL / 2 - 4, 0xFF9AA4AE);
        }
    }
}
