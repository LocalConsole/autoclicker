package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Инвентарь на экране. Строки: 1–3 — основной инвентарь, 4 — хотбар (по желанию). Каждую строку можно
 * включить/выключить, показывать горизонтально (9×1) или вертикально (1×9), всем блоком или каждую
 * строку отдельно в своём месте; размер и прозрачность настраиваются.
 */
public class InventoryHud {
    public static boolean held = false;
    public static final int ROWS = 4;
    private static final int CELL = 18;

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "inventory_hud"), (ctx, tc) -> draw(ctx, false));
    }

    /** Где сейчас панель (весь блок) и каждая строка — для окна перемещения. */
    public static int boundsX, boundsY, boundsW, boundsH;
    public static final int[] rowX = new int[ROWS], rowY = new int[ROWS], rowW = new int[ROWS], rowH = new int[ROWS];
    public static final boolean[] rowShown = new boolean[ROWS];

    /** Индекс слота инвентаря для строки r и клетки i (строка 3 — хотбар). */
    private static int slot(int r, int i) {
        return r == 3 ? i : 9 + r * 9 + i;
    }

    private static List<Integer> rows() {
        List<Integer> l = new ArrayList<>();
        for (int r = 0; r < ROWS; r++) if (ClickerConfig.invHudRows[r]) l.add(r);
        return l;
    }

    /** @param force рисовать в любом случае (окно перемещения) */
    public static void draw(DrawContext ctx, boolean force) {
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int r = 0; r < ROWS; r++) rowShown[r] = false;
        boundsW = 0;
        if (mc.player == null) return;
        if (!force) {
            if (mc.options.hudHidden || mc.player.isSpectator()) return;
            if (!ClickerConfig.invHud) return;
            if (ClickerConfig.invHudHold && !held) return;
        }
        PlayerInventory inv = mc.player.getInventory();
        List<Integer> rows = rows();
        if (rows.isEmpty()) return;
        boolean any = false;
        for (int r : rows) for (int i = 0; i < 9; i++) if (!inv.getStack(slot(r, i)).isEmpty()) any = true;
        if (!force && !any && ClickerConfig.invHudHideEmpty) return;

        float k = Math.max(50, Math.min(200, ClickerConfig.invHudScale)) / 100f;
        boolean vert = ClickerConfig.invHudVertical;
        int a = Math.max(20, Math.min(255, ClickerConfig.invHudOpacity * 255 / 100));
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        // одна строка: 9×1 или 1×9
        int lw0 = vert ? CELL + 2 : 9 * CELL + 2, lh0 = vert ? 9 * CELL + 2 : CELL + 2;

        if (ClickerConfig.invHudSeparate) {
            // каждая строка — в своём месте
            for (int r : rows) {
                int w = Math.round(lw0 * k), h = Math.round(lh0 * k);
                int x = Math.round(ClickerConfig.invHudRowX[r] / 1000f * Math.max(1, sw - w));
                int y = Math.round(ClickerConfig.invHudRowY[r] / 1000f * Math.max(1, sh - h));
                rowX[r] = x;
                rowY[r] = y;
                rowW[r] = w;
                rowH[r] = h;
                rowShown[r] = true;
                panel(ctx, mc, inv, List.of(r), x, y, k, vert, a);
            }
            return;
        }

        // всем блоком: строки друг под другом (или колонки рядом при вертикальном виде)
        int n = rows.size();
        int w0 = vert ? n * CELL + 2 : 9 * CELL + 2, h0 = vert ? 9 * CELL + 2 : n * CELL + 2;
        int w = Math.round(w0 * k), h = Math.round(h0 * k);
        int x, y;
        if (ClickerConfig.invHudFree) {
            x = Math.round(ClickerConfig.invHudX / 1000f * Math.max(1, sw - w));
            y = Math.round(ClickerConfig.invHudY / 1000f * Math.max(1, sh - h));
        } else {
            // над хотбаром, сердечками и едой: 22 хотбар + ~27 полоски + отступ
            x = sw / 2 - w / 2;
            y = sh - 22 - 30 - h - ClickerConfig.invHudOffset;
        }
        boundsX = x;
        boundsY = y;
        boundsW = w;
        boundsH = h;
        panel(ctx, mc, inv, rows, x, y, k, vert, a);
    }

    private static void panel(DrawContext ctx, MinecraftClient mc, PlayerInventory inv, List<Integer> rows,
                              int x, int y, float k, boolean vert, int a) {
        int n = rows.size();
        int w0 = vert ? n * CELL + 2 : 9 * CELL + 2, h0 = vert ? 9 * CELL + 2 : n * CELL + 2;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(k, k);
        ctx.fill(-1, -1, w0 + 1, h0 + 1, (a << 24) | 0x0E1216);
        ctx.fill(0, 0, w0, h0, ((a * 3 / 4) << 24) | 0x2A313A);
        for (int ri = 0; ri < n; ri++) {
            int r = rows.get(ri);
            for (int i = 0; i < 9; i++) {
                int sx = 1 + (vert ? ri : i) * CELL, sy = 1 + (vert ? i : ri) * CELL;
                boolean hot = r == 3;
                ctx.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, ((a / 2) << 24) | (hot ? 0x1A2A1A : 0x000000));
                if (hot && i == mc.player.getInventory().getSelectedSlot()) {
                    // выбранный слот хотбара — рамка
                    ctx.fill(sx, sy, sx + CELL - 1, sy + 1, 0xFFFFFFFF);
                    ctx.fill(sx, sy + CELL - 2, sx + CELL - 1, sy + CELL - 1, 0xFFFFFFFF);
                    ctx.fill(sx, sy, sx + 1, sy + CELL - 1, 0xFFFFFFFF);
                    ctx.fill(sx + CELL - 2, sy, sx + CELL - 1, sy + CELL - 1, 0xFFFFFFFF);
                }
                ItemStack st = inv.getStack(slot(r, i));
                if (st.isEmpty()) continue;
                ctx.drawItem(st, sx + 1, sy + 1);
                ctx.drawStackOverlay(mc.textRenderer, st, sx + 1, sy + 1);
            }
        }
        m.popMatrix();
    }
}
