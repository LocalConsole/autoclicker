package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.List;

/**
 * Все рамки мода рисуются обычным рендером мира Minecraft (тот же слой линий, что у контура блока под прицелом):
 * хитбокс цели, невидимые блоки (барьер, пустота, свет) и особые блоки (песок, гравий, светокамень, рыхлый снег).
 * Никакой ручной проекции на экран, поэтому не лагает. Как и ванильный контур, рамки скрываются за блоками.
 */
public class WorldOutlines {
    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(WorldOutlines::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        boolean frames = ClickerConfig.showFrames && ClickerConfig.frameSolid;
        boolean invis = ClickerConfig.showInvisible && ClickerConfig.invisOutline;
        boolean hitbox = ClickerConfig.hitboxHighlight && mc.currentScreen == null;
        List<BlockPos> found = ContainerSearch.matchingPositions(mc);
        if (!frames && !invis && !hitbox && found.isEmpty()) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        Vec3d cp = ContainerSearch.cameraPos(mc.gameRenderer.getCamera(), mc);
        float width = Math.max(2.5f, mc.getWindow().getFramebufferWidth() / 1920.0f * 2.5f);

        if (hitbox) drawHitboxes(mc, matrices, vc, cp, width);
        for (BlockPos p : found) {
            drawBox(matrices, vc, new Box(p), cp, 0xFFFF3030, width);
        }
        if (frames) {
            drawBlocks(matrices, vc, BlockFrames.sand, cp, ClickerConfig.frameSandColor, width, EdgeMask.FRAMES);
            drawBlocks(matrices, vc, BlockFrames.gravel, cp, ClickerConfig.frameGravelColor, width, EdgeMask.FRAMES);
            drawBlocks(matrices, vc, BlockFrames.glow, cp, ClickerConfig.frameGlowColor, width, EdgeMask.FRAMES);
            drawBlocks(matrices, vc, BlockFrames.snow, cp, ClickerConfig.frameSnowColor, width, EdgeMask.FRAMES, -0.04);
        }
        if (invis) {
            drawBlocks(matrices, vc, InvisibleBlocks.barrier, cp, ClickerConfig.invisBarrierColor, width, EdgeMask.INVIS);
            drawBlocks(matrices, vc, InvisibleBlocks.voidBlocks, cp, ClickerConfig.invisVoidColor, width, EdgeMask.INVIS);
            drawBlocks(matrices, vc, InvisibleBlocks.light, cp, ClickerConfig.invisLightColor, width, EdgeMask.INVIS);
        }
    }

    /**
     * Хитбоксы всех существ, до которых достаёт удар. Цвет показывает, можно ли бить:
     * перезарядка идёт — от красного к жёлтому, удар полной силы — ваш цвет (по умолчанию зелёный).
     * Под прицелом рамка толще. Рамка чуть больше настоящего хитбокса, чтобы её не перекрывали
     * белые хитбоксы F3+B.
     */
    private static void drawHitboxes(MinecraftClient mc, MatrixStack m, VertexConsumer vc, Vec3d cp, float width) {
        float cd = mc.player.getAttackCooldownProgress(0.0f);
        int ready = color(ClickerConfig.hitboxColor);
        int col;
        if (cd >= 0.999f) {
            col = ready;
        } else {
            // красный → жёлтый по мере перезарядки
            int g = (int) (60 + 170 * cd);
            col = 0xFFFF0000 | (g << 8) | 0x30;
        }
        Entity target = mc.crosshairTarget instanceof EntityHitResult ehr ? ehr.getEntity() : null;
        double reach = mc.player.getEntityInteractionRange();
        Vec3d eye = mc.player.getEyePos();
        float tp = mc.getRenderTickCounter().getTickProgress(false);
        Box area = mc.player.getBoundingBox().expand(reach + 2);
        for (Entity e : mc.world.getOtherEntities(mc.player, area, x -> x.isAlive() && x.canHit() && !x.isSpectator())) {
            if (!ClickerConfig.hitboxAll && e != target) continue;
            Vec3d shift = e.getLerpedPos(tp).subtract(e.getX(), e.getY(), e.getZ());
            Box b = e.getBoundingBox().offset(shift);
            // ближайшая точка хитбокса — в досягаемости удара?
            double cx = Math.max(b.minX, Math.min(eye.x, b.maxX));
            double cy = Math.max(b.minY, Math.min(eye.y, b.maxY));
            double cz = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
            if (eye.squaredDistanceTo(cx, cy, cz) > reach * reach) continue;
            boolean aimed = e == target;
            drawBox(m, vc, b.expand(0.02), cp, aimed ? col : (col & 0x00FFFFFF) | 0xAA000000, aimed ? width * 1.6f : width);
        }
    }

    private static int color(int idx) {
        return Waypoints.COLORS[Math.max(0, Math.min(Waypoints.COLORS.length - 1, idx))];
    }

    private static void drawBlocks(MatrixStack m, VertexConsumer vc, List<BlockPos> list, Vec3d cp, int colorIdx, float width,
                                   java.util.Map<Long, Integer> masks) {
        drawBlocks(m, vc, list, cp, colorIdx, width, masks, 0.0);
    }

    private static void drawBlocks(MatrixStack m, VertexConsumer vc, List<BlockPos> list, Vec3d cp, int colorIdx, float width,
                                   java.util.Map<Long, Integer> masks, double inset) {
        if (list.isEmpty()) return;
        int c = color(colorIdx);
        for (BlockPos p : list) {
            int mask = masks.getOrDefault(p.asLong(), 0xFFF);
            for (int e = 0; e < 12; e++) {
                if ((mask & (1 << e)) == 0) continue;
                double[] s = EdgeMask.segment(p, e, inset);
                drawBox(m, vc, new Box(s[0], s[1], s[2], s[3], s[4], s[5]), cp, c, width);
            }
        }
    }

    /**
     * Единственное место, где вызывается ванильная отрисовка контура (как для блока под прицелом).
     * Если при сборке тут ошибка в аргументах drawOutline, достаточно поправить эту строку.
     */
    private static void drawBox(MatrixStack m, VertexConsumer vc, Box box, Vec3d cp, int argb, float width) {
        VoxelShape shape = VoxelShapes.cuboid(box);
        VertexRendering.drawOutline(m, vc, shape, -cp.x, -cp.y, -cp.z, argb, width);
    }
}
