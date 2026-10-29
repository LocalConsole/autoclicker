package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Locale;

/**
 * Линейка: две точки (блок, на который смотрите, или ваша позиция). Между ними рисуется линия,
 * в середине — расстояние в блоках, на концах — стрелки.
 */
public class Ruler {
    private static BlockPos a, b;
    private static String dim = "";
    private static int count = 0;

    private static final double NEAR = 0.05;

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "ruler"),
                (context, tickCounter) -> render(context));
    }

    // ---------- Точки ----------

    private static BlockPos pick(MinecraftClient mc) {
        if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            return bhr.getBlockPos();
        }
        return mc.player.getBlockPos();
    }

    private static String fmt(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.05) return String.valueOf((long) Math.rint(d));
        return String.format(Locale.ROOT, "%.1f", d);
    }

    private static String xyz(BlockPos p) {
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    /** По клавише: первая точка, вторая точка, затем всё сначала. */
    public static void press(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        BlockPos p = pick(mc);
        String d = Waypoints.currentDim(mc);
        if (count == 0 || count == 2 || !d.equals(dim)) {
            a = p;
            b = null;
            dim = d;
            count = 1;
            mc.player.sendMessage(Text.literal(I18n.t("Линейка: точка A") + " " + xyz(p)), true);
        } else {
            b = p;
            count = 2;
            mc.player.sendMessage(Text.literal(I18n.t("Линейка: точка B") + " " + xyz(p) + "  "
                    + fmt(dist()) + I18n.t(" бл.")), true);
        }
    }

    public static void clear(MinecraftClient mc) {
        a = null;
        b = null;
        count = 0;
        if (mc.player != null) mc.player.sendMessage(Text.literal(I18n.t("Линейка очищена")), true);
    }

    private static double dist() {
        double dx = a.getX() - b.getX(), dy = a.getY() - b.getY(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---------- Отрисовка ----------

    private static void render(DrawContext ctx) {
        if (!ClickerConfig.rulerShow || count == 0 || a == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (!dim.equals(Waypoints.currentDim(mc))) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = ContainerSearch.cameraPos(cam, mc);
        Quaternionf q = cam.getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Vector3f fwd = new Vector3f(0, 0, -1).rotate(q);

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

        int col = Waypoints.COLORS[Math.max(0, Math.min(Waypoints.COLORS.length - 1, ClickerConfig.rulerColor))];

        double[] ca = toCamera(a, cp, right, up, fwd);
        if (count == 1 || b == null) {
            // только первая точка: маленький маркер
            if (ca[2] >= NEAR) {
                double[] s = project(ca, sw, sh, aspect, tanHalf);
                ctx.fill((int) s[0] - 4, (int) s[1] - 4, (int) s[0] + 5, (int) s[1] + 5, 0xFF000000);
                ctx.fill((int) s[0] - 3, (int) s[1] - 3, (int) s[0] + 4, (int) s[1] + 4, col);
            }
            return;
        }
        double[] cb = toCamera(b, cp, right, up, fwd);
        if (ca[2] < NEAR && cb[2] < NEAR) return;

        boolean aVisible = ca[2] >= NEAR, bVisible = cb[2] >= NEAR;
        double[] ac = ca.clone(), bc = cb.clone();
        if (!aVisible) {
            double t = (NEAR - ca[2]) / (cb[2] - ca[2]);
            ac = new double[]{ca[0] + (cb[0] - ca[0]) * t, ca[1] + (cb[1] - ca[1]) * t, NEAR};
        } else if (!bVisible) {
            double t = (NEAR - cb[2]) / (ca[2] - cb[2]);
            bc = new double[]{cb[0] + (ca[0] - cb[0]) * t, cb[1] + (ca[1] - cb[1]) * t, NEAR};
        }
        double[] sa = project(ac, sw, sh, aspect, tanHalf);
        double[] sb = project(bc, sw, sh, aspect, tanHalf);
        double[] seg = ContainerSearch.clip(sa[0], sa[1], sb[0], sb[1], sw, sh);
        if (seg == null) return;

        ContainerSearch.line(ctx, seg[0], seg[1], seg[2], seg[3], 4, 0xAA000000);
        ContainerSearch.line(ctx, seg[0], seg[1], seg[2], seg[3], 2, col);

        // стрелки и поперечные риски на концах (только если конец на экране)
        double ux = sb[0] - sa[0], uy = sb[1] - sa[1];
        double len = Math.sqrt(ux * ux + uy * uy);
        if (len > 1.0) {
            ux /= len;
            uy /= len;
            if (aVisible && inside(sa, sw, sh)) endMark(ctx, sa[0], sa[1], -ux, -uy, col);
            if (bVisible && inside(sb, sw, sh)) endMark(ctx, sb[0], sb[1], ux, uy, col);
        }

        // подпись в середине линии
        double mx = (seg[0] + seg[2]) / 2.0, my = (seg[1] + seg[3]) / 2.0;
        String main = fmt(dist()) + I18n.t(" бл.");
        String sub = "X " + Math.abs(a.getX() - b.getX()) + "  Y " + Math.abs(a.getY() - b.getY())
                + "  Z " + Math.abs(a.getZ() - b.getZ());
        int w = Math.max(mc.textRenderer.getWidth(main), mc.textRenderer.getWidth(sub)) + 8;
        int x1 = (int) Math.max(2, Math.min(sw - w - 2, mx - w / 2.0));
        int y1 = (int) Math.max(2, Math.min(sh - 24, my - 12));
        ctx.fill(x1, y1, x1 + w, y1 + 24, 0xAA000000);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(main), x1 + w / 2, y1 + 3, col);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(sub), x1 + w / 2, y1 + 14, 0xFFAAAAAA);
    }

    private static boolean inside(double[] s, int sw, int sh) {
        return s[0] >= 0 && s[0] <= sw && s[1] >= 0 && s[1] <= sh;
    }

    /**
     * Конец линии: поперечная риска и стрелка. (ox, oy) — направление «наружу» вдоль линии.
     * Обычно «<——>» (наконечник смотрит наружу); при включённой настройке «>——<» (внутрь).
     */
    private static void endMark(DrawContext ctx, double px, double py, double ox, double oy, int col) {
        // риска поперёк линии
        double nx = -oy, ny = ox;
        ContainerSearch.line(ctx, px - nx * 6, py - ny * 6, px + nx * 6, py + ny * 6, 2, col);
        // стрелка: крылья идут от кончика назад
        double wx = ClickerConfig.rulerArrowsIn ? ox : -ox;
        double wy = ClickerConfig.rulerArrowsIn ? oy : -oy;
        double ang = Math.toRadians(30);
        double c = Math.cos(ang), s = Math.sin(ang);
        double w1x = wx * c - wy * s, w1y = wx * s + wy * c;
        double w2x = wx * c + wy * s, w2y = -wx * s + wy * c;
        ContainerSearch.line(ctx, px, py, px + w1x * 10, py + w1y * 10, 2, col);
        ContainerSearch.line(ctx, px, py, px + w2x * 10, py + w2y * 10, 2, col);
    }

    private static double[] toCamera(BlockPos p, Vec3d cp, Vector3f right, Vector3f up, Vector3f fwd) {
        double wx = p.getX() + 0.5 - cp.x, wy = p.getY() + 0.5 - cp.y, wz = p.getZ() + 0.5 - cp.z;
        return new double[]{
                wx * right.x + wy * right.y + wz * right.z,
                wx * up.x + wy * up.y + wz * up.z,
                wx * fwd.x + wy * fwd.y + wz * fwd.z
        };
    }

    private static double[] project(double[] c, int sw, int sh, double aspect, double tanHalf) {
        double sx = (c[0] / (c[2] * tanHalf * aspect) * 0.5 + 0.5) * sw;
        double sy = (0.5 - c[1] / (c[2] * tanHalf) * 0.5) * sh;
        return new double[]{sx, sy};
    }
}
