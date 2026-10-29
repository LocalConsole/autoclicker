package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Свои координаты и пересчёт в соседнее измерение: Верхний мир ↔ Незер (делится или умножается на 8). */
public class CoordsHud {
    public static void register() {
        HudElementRegistry.addLast(Identifier.of(UniversalMod.MOD_ID, "coords_hud"),
                (context, tickCounter) -> draw(context));
    }

    private static void draw(DrawContext ctx) {
        if (!ClickerConfig.coordsHud) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden) return;

        int x = MathHelper.floor(p.getX());
        int y = MathHelper.floor(p.getY());
        int z = MathHelper.floor(p.getZ());
        String l1 = "XYZ: " + x + " " + y + " " + z;
        String l2 = null;
        String dim = Waypoints.currentDim(mc);
        if (dim.equals(Waypoints.DIMS[0])) {
            l2 = I18n.t("Незер: ") + Math.floorDiv(x, 8) + " " + Math.floorDiv(z, 8);
        } else if (dim.equals(Waypoints.DIMS[1])) {
            l2 = I18n.t("Верхний мир: ") + (x * 8) + " " + (z * 8);
        }

        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int w = tr.getWidth(l1);
        if (l2 != null) w = Math.max(w, tr.getWidth(l2));
        int h = l2 != null ? 20 : 10;

        int pos = Math.max(0, Math.min(3, ClickerConfig.coordsPos));
        boolean right = pos == 1 || pos == 3;
        boolean bottom = pos == 2 || pos == 3;
        int px = right ? sw - 4 - w : 4;
        int py = bottom ? sh - 4 - h : 4;

        ctx.drawTextWithShadow(tr, l1, right ? px + (w - tr.getWidth(l1)) : px, py, 0xFFFFFFFF);
        if (l2 != null) {
            ctx.drawTextWithShadow(tr, l2, right ? px + (w - tr.getWidth(l2)) : px, py + 10, 0xFFFF9A2E);
        }
    }
}
