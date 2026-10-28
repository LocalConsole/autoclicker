package com.example.universalmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Показ невидимых блоков (барьер, структурная пустота, свет): вокруг игрока ищем такие блоки и обводим их
 * рамкой. Работает в любом режиме игры, без предмета в руке. Рамки рисует {@link WorldOutlines}.
 */
public class InvisibleBlocks {
    private static final int SCAN_EVERY = 10;
    private static final int MAX_BLOCKS = 800;

    private static volatile List<WorldOutlines.Mark> marks = List.of();
    private static int now = 0;

    public static void reset() {
        marks = List.of();
    }

    public static List<WorldOutlines.Mark> marks() {
        return ClickerConfig.showInvisible ? marks : List.of();
    }

    /** Цвет рамки (RGB) либо -1 — блок не нужен. */
    private static int colorFor(Block b) {
        if (b == Blocks.BARRIER) return ClickerConfig.invisBarrier ? 0xFF3030 : -1;
        if (b == Blocks.STRUCTURE_VOID) return ClickerConfig.invisVoid ? 0x30FF60 : -1;
        if (b == Blocks.LIGHT) return ClickerConfig.invisLight ? 0xFFE040 : -1;
        return -1;
    }

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (!ClickerConfig.showInvisible || world == null || client.player == null) {
            marks = List.of();
            return;
        }
        now++;
        if (now % SCAN_EVERY != 0) return;

        int r = Math.max(4, Math.min(32, ClickerConfig.invisRadius));
        int r2 = r * r;
        BlockPos c = client.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        List<WorldOutlines.Mark> found = new ArrayList<>();

        scan:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = world.getBlockState(pos);
                    if (st.isAir()) continue;
                    int rgb = colorFor(st.getBlock());
                    if (rgb < 0) continue;
                    found.add(WorldOutlines.Mark.of(pos, rgb));
                    if (found.size() >= MAX_BLOCKS) break scan;
                }
            }
        }
        marks = found;
    }
}
