package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.EggItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Траектория снаряда: лук (по силе натяжения), заряженный арбалет, трезубец, эндер-жемчуг, снежок, яйцо,
 * взрывные и оседающие зелья, пузырёк опыта, заряд ветра. Линия полёта и точка падения; если снаряд попадёт
 * в существо — линия красная и подписано, в кого.
 */
public class Trajectory {
    private static final double NEAR = 0.05;
    private static final int MAX_STEPS = 300;

    private record Kind(double speed, double gravity, double drag, float roll, String name) { }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "trajectory"), (ctx, tc) -> render(ctx));
    }

    /** Что за снаряд в руке и с какой силой полетит. null — нечего показывать. */
    private static Kind kind(ClientPlayerEntity p, ItemStack st, boolean usingThis) {
        if (st.isEmpty()) return null;
        if (st.isOf(Items.BOW)) {
            if (!usingThis) return null;
            float pull = BowItem.getPullProgress(p.getItemUseTime());
            if (pull < 0.1f) return null;
            return new Kind(pull * 3.0, 0.05, 0.99, 0f, I18n.t("Стрела"));
        }
        if (st.isOf(Items.CROSSBOW)) {
            if (!CrossbowItem.isCharged(st)) return null;
            return new Kind(3.15, 0.05, 0.99, 0f, I18n.t("Арбалет"));
        }
        if (st.isOf(Items.TRIDENT)) {
            if (!usingThis) return null;
            return new Kind(2.5, 0.05, 0.99, 0f, I18n.t("Трезубец"));
        }
        if (st.isOf(Items.ENDER_PEARL)) return new Kind(1.5, 0.03, 0.99, 0f, I18n.t("Жемчуг"));
        if (st.isOf(Items.SNOWBALL) || st.getItem() instanceof EggItem) return new Kind(1.5, 0.03, 0.99, 0f, "");
        if (st.isOf(Items.SPLASH_POTION) || st.isOf(Items.LINGERING_POTION)) return new Kind(0.5, 0.05, 0.99, -20f, I18n.t("Зелье"));
        if (st.isOf(Items.EXPERIENCE_BOTTLE)) return new Kind(0.7, 0.07, 0.99, -20f, "");
        if (st.isOf(Items.WIND_CHARGE)) return new Kind(1.5, 0.0, 1.0, 0f, "");
        return null;
    }

    private static void render(DrawContext ctx) {
        if (!ClickerConfig.trajectory) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.options.hudHidden || mc.currentScreen != null) return;
        if (!mc.options.getPerspective().isFirstPerson() && !ClickerConfig.trajectoryThirdPerson) return;

        boolean using = p.isUsingItem();
        Hand active = using ? p.getActiveHand() : null;
        Kind k = kind(p, p.getMainHandStack(), active == Hand.MAIN_HAND);
        if (k == null) k = kind(p, p.getOffHandStack(), active == Hand.OFF_HAND);
        if (k == null) return;

        float tp = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d base = p.getLerpedPos(tp);
        Vec3d pos = new Vec3d(base.x, base.y + (p.getEyeY() - p.getY()) - 0.1, base.z);
        float yaw = p.getYaw(), pitch = p.getPitch();
        double fx = -MathHelper.sin(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        double fy = -MathHelper.sin((pitch + k.roll()) * MathHelper.RADIANS_PER_DEGREE);
        double fz = MathHelper.cos(yaw * MathHelper.RADIANS_PER_DEGREE) * MathHelper.cos(pitch * MathHelper.RADIANS_PER_DEGREE);
        Vec3d vel = new Vec3d(fx, fy, fz).normalize().multiply(k.speed());
        Vec3d pv = p.getVelocity();
        vel = vel.add(pv.x, p.isOnGround() ? 0.0 : pv.y, pv.z);

        // полёт по тикам: сначала путь до блока, потом ОДИН запрос существ на всю траекторию
        // (раньше — запрос на каждый тик полёта, до 300 запросов за кадр)
        List<Vec3d> pts = new ArrayList<>();
        pts.add(pos);
        Vec3d end = null;
        double minX = pos.x, minY = pos.y, minZ = pos.z, maxX = pos.x, maxY = pos.y, maxZ = pos.z;
        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3d next = pos.add(vel);
            BlockHitResult bh = mc.world.raycast(new RaycastContext(pos, next,
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
            boolean hit = bh.getType() != HitResult.Type.MISS;
            if (hit) next = bh.getPos();
            pts.add(next);
            minX = Math.min(minX, next.x); minY = Math.min(minY, next.y); minZ = Math.min(minZ, next.z);
            maxX = Math.max(maxX, next.x); maxY = Math.max(maxY, next.y); maxZ = Math.max(maxZ, next.z);
            if (hit) {
                end = next;
                break;
            }
            pos = next;
            boolean water = !mc.world.getFluidState(net.minecraft.util.math.BlockPos.ofFloored(pos)).isEmpty();
            vel = vel.multiply(water ? 0.6 : k.drag()).add(0, -k.gravity(), 0);
            if (pos.y < mc.world.getBottomY() - 64) break;
        }
        Entity hitEntity = null;
        Box all = new Box(minX, minY, minZ, maxX, maxY, maxZ).expand(1.0);
        List<Entity> cands = mc.world.getOtherEntities(p, all, e -> e.isAlive() && e.canHit() && !e.isSpectator());
        if (!cands.isEmpty()) {
            for (int i = 0; i + 1 < pts.size() && hitEntity == null; i++) {
                Vec3d a = pts.get(i), b = pts.get(i + 1);
                double best = Double.MAX_VALUE;
                for (Entity e : cands) {
                    var r = e.getBoundingBox().expand(0.3).raycast(a, b);
                    if (r.isPresent()) {
                        double d = a.squaredDistanceTo(r.get());
                        if (d < best) {
                            best = d;
                            hitEntity = e;
                            end = r.get();
                        }
                    }
                }
                if (hitEntity != null) {
                    while (pts.size() > i + 1) pts.remove(pts.size() - 1);
                    pts.add(end);
                }
            }
        }

        int col = hitEntity != null ? 0xFFFF4444 : 0xFFFFFFFF;
        drawPath(ctx, mc, pts, col);
        if (end != null) marker(ctx, mc, end, hitEntity);
    }

    // ---------- Проекция на экран (как у линейки) ----------

    private static void drawPath(DrawContext ctx, MinecraftClient mc, List<Vec3d> pts, int col) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = ContainerSearch.cameraPos(cam, mc);
        Quaternionf q = cam.getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Vector3f fwd = new Vector3f(0, 0, -1).rotate(q);
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

        double[] prev = null;
        for (int i = 0; i < pts.size(); i++) {
            double[] c = toCamera(pts.get(i), cp, right, up, fwd);
            if (prev != null) {
                double[] a = prev, b = c;
                if (a[2] < NEAR && b[2] < NEAR) { prev = c; continue; }
                if (a[2] < NEAR) a = cut(a, b);
                else if (b[2] < NEAR) b = cut(b, a);
                double[] sa = project(a, sw, sh, aspect, tanHalf), sb = project(b, sw, sh, aspect, tanHalf);
                double[] seg = ContainerSearch.clip(sa[0], sa[1], sb[0], sb[1], sw, sh);
                if (seg != null) {
                    // ближняя часть линии полупрозрачная, чтобы не мешала прицелу
                    int alpha = i < 3 ? 0x66000000 : 0xFF000000;
                    ContainerSearch.line(ctx, seg[0], seg[1], seg[2], seg[3], 3, 0x88000000 & alpha);
                    ContainerSearch.line(ctx, seg[0], seg[1], seg[2], seg[3], 1, (col & 0x00FFFFFF) | alpha);
                }
            }
            prev = c;
        }
    }

    private static void marker(DrawContext ctx, MinecraftClient mc, Vec3d at, Entity hit) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = ContainerSearch.cameraPos(cam, mc);
        Quaternionf q = cam.getRotation();
        double[] c = toCamera(at, cp, new Vector3f(1, 0, 0).rotate(q), new Vector3f(0, 1, 0).rotate(q),
                new Vector3f(0, 0, -1).rotate(q));
        if (c[2] < NEAR) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double[] s = project(c, sw, sh, aspect, tanHalf);
        int x = (int) s[0], y = (int) s[1];
        int col = hit != null ? 0xFFFF4444 : 0xFF55FF55;
        ctx.fill(x - 5, y - 5, x + 6, y + 6, 0xAA000000);
        ctx.fill(x - 4, y - 4, x + 5, y + 5, col);
        ctx.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000);
        String label = hit != null ? hit.getName().getString()
                : String.format(java.util.Locale.ROOT, "%.1f", mc.player.getEyePos().distanceTo(at)) + I18n.t(" бл.");
        int w = mc.textRenderer.getWidth(label);
        ctx.fill(x - w / 2 - 3, y + 8, x + w / 2 + 3, y + 19, 0xAA000000);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(label), x, y + 10, col);
    }

    private static double[] cut(double[] behind, double[] front) {
        double t = (NEAR - behind[2]) / (front[2] - behind[2]);
        return new double[]{behind[0] + (front[0] - behind[0]) * t, behind[1] + (front[1] - behind[1]) * t, NEAR};
    }

    private static double[] toCamera(Vec3d p, Vec3d cp, Vector3f right, Vector3f up, Vector3f fwd) {
        double wx = p.x - cp.x, wy = p.y - cp.y, wz = p.z - cp.z;
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
