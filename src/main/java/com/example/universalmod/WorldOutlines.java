package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import java.util.ArrayList;
import java.util.List;

/**
 * Ванильная отрисовка рамок прямо в мире (те же линии, что у стандартной рамки блока под прицелом).
 * Заменяет частицы: рамки не мерцают, не зависят от настроек частиц и рисуются каждый кадр.
 * Всё, что нужно отметить, собирают BlockFrames, InvisibleBlocks, ContainerSearch и TargetOutline.
 * Весь код, зависящий от API рендера Minecraft, находится только в этом файле.
 */
public class WorldOutlines {
    /** Блок, который нужно обвести, и цвет рамки (0..1). */
    public record Mark(BlockPos pos, float r, float g, float b) {
        public static Mark of(BlockPos pos, int rgb) {
            return new Mark(pos.toImmutable(),
                    ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        }
    }

    public static void register() {
        WorldRenderEvents.END_MAIN.register(context -> render());
    }

    private static void render() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.options.hudHidden) return;

        List<Mark> marks = new ArrayList<>();
        marks.addAll(BlockFrames.marks());
        marks.addAll(InvisibleBlocks.marks());
        marks.addAll(ContainerSearch.worldMarks(mc));
        Mark target = TargetOutline.mark(mc);
        if (target != null) marks.add(target);
        if (marks.isEmpty()) return;

        Vec3d cam = ContainerSearch.cameraPos(mc.gameRenderer.getCamera(), mc);
        VertexConsumerProvider.Immediate buffers = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer lines = buffers.getBuffer(RenderLayers.lines());
        MatrixStack matrices = new MatrixStack();

        for (Mark m : marks) {
            BlockPos p = m.pos();
            // Контур по настоящей форме блока (сундук, ступенька, забор...), как у ванильной рамки под прицелом.
            // Если у блока нет формы (например, свет), рисуем целый куб.
            BlockState st = mc.world.getBlockState(p);
            VoxelShape shape = st.getOutlineShape(mc.world, p);
            if (shape.isEmpty()) shape = VoxelShapes.fullCube();
            int color = 0xFF000000
                    | (Math.round(m.r() * 255) << 16) | (Math.round(m.g() * 255) << 8) | Math.round(m.b() * 255);
            VertexRendering.drawOutline(matrices, lines, shape,
                    p.getX() - cam.x, p.getY() - cam.y, p.getZ() - cam.z, color, 2.0f);
        }
        buffers.draw(RenderLayers.lines());
    }
}
