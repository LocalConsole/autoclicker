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
        EntityHitResult hit = hitboxTarget(mc);
        List<BlockPos> found = ContainerSearch.matchingPositions(mc);
        if (!frames && !invis && hit == null && found.isEmpty()) return;

        MatrixStack matrices = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        if (matrices == null || consumers == null) return;

        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        Vec3d cp = ContainerSearch.cameraPos(mc.gameRenderer.getCamera(), mc);
        float width = Math.max(2.5f, mc.getWindow().getFramebufferWidth() / 1920.0f * 2.5f);

        if (hit != null) {
            Entity e = hit.getEntity();
            float tp = mc.getRenderTickCounter().getTickProgress(false);
            Vec3d shift = e.getLerpedPos(tp).subtract(e.getX(), e.getY(), e.getZ());
            drawBox(matrices, vc, e.getBoundingBox().offset(shift), cp, color(ClickerConfig.hitboxColor), width);
        }
        for (BlockPos p : found) {
            drawBox(matrices, vc, new Box(p), cp, 0xFFFF3030, width);
        }
        if (frames) {
            drawBlocks(matrices, vc, BlockFrames.sand, cp, ClickerConfig.frameSandColor, width, EdgeMask.FRAMES);
            drawBlocks(matrices, vc, BlockFrames.gravel, cp, ClickerConfig.frameGravelColor, width, EdgeMask.FRAMES);
            drawBlocks(matrices, vc, BlockFrames.glow, cp, ClickerConfig.frameGlowColor, width, EdgeMask.FRAMES);
            drawBlocks(matrices, vc, BlockFrames.snow, cp, ClickerConfig.frameSnowColor, width, EdgeMask.FRAMES);
        }
        if (invis) {
            drawBlocks(matrices, vc, InvisibleBlocks.barrier, cp, ClickerConfig.invisBarrierColor, width, EdgeMask.INVIS);
            drawBlocks(matrices, vc, InvisibleBlocks.voidBlocks, cp, ClickerConfig.invisVoidColor, width, EdgeMask.INVIS);
            drawBlocks(matrices, vc, InvisibleBlocks.light, cp, ClickerConfig.invisLightColor, width, EdgeMask.INVIS);
        }
    }

    /** Цель для рамки хитбокса: сущность под прицелом, если удар будет полной силы. */
    private static EntityHitResult hitboxTarget(MinecraftClient mc) {
        if (!ClickerConfig.hitboxHighlight || mc.currentScreen != null) return null;
        HitResult h = mc.crosshairTarget;
        if (!(h instanceof EntityHitResult ehr)) return null;
        if (!ehr.getEntity().isAttackable()) return null;
        if (mc.player.getAttackCooldownProgress(0.0f) < 1.0f) return null;
        return ehr;
    }

    private static int color(int idx) {
        return Waypoints.COLORS[Math.max(0, Math.min(Waypoints.COLORS.length - 1, idx))];
    }

    private static void drawBlocks(MatrixStack m, VertexConsumer vc, List<BlockPos> list, Vec3d cp, int colorIdx, float width,
                                   java.util.Map<Long, Integer> masks) {
        if (list.isEmpty()) return;
        int c = color(colorIdx);
        for (BlockPos p : list) {
            int mask = masks.getOrDefault(p.asLong(), 0xFFF);
            for (int e = 0; e < 12; e++) {
                if ((mask & (1 << e)) == 0) continue;
                double[] s = EdgeMask.segment(p, e);
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
