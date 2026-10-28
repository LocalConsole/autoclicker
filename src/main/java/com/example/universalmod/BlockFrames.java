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
 * Рамки вокруг особых блоков: подозрительный песок, подозрительный гравий, светокамень, рыхлый снег.
 * Блок определяется по настоящему состоянию мира, поэтому рыхлый снег находится, даже если сверху лежит слой снега.
 * Сами рамки рисует {@link WorldOutlines}.
 */
public class BlockFrames {
    private static final int SCAN_EVERY = 10;
    private static final int MAX_BLOCKS = 600;

    private static volatile List<WorldOutlines.Mark> marks = List.of();
    private static int now = 0;

    public static void reset() {
        marks = List.of();
    }

    public static List<WorldOutlines.Mark> marks() {
        return ClickerConfig.showFrames ? marks : List.of();
    }

    /** Цвет рамки (RGB) либо -1 — блок не нужен. */
    private static int colorFor(Block b) {
        if (b == Blocks.SUSPICIOUS_SAND) return ClickerConfig.frameSand ? 0xFFB020 : -1;
        if (b == Blocks.SUSPICIOUS_GRAVEL) return ClickerConfig.frameGravel ? 0x9A9A9A : -1;
        if (b == Blocks.GLOWSTONE) return ClickerConfig.frameGlowstone ? 0xFFEE40 : -1;
        if (b == Blocks.POWDER_SNOW) return ClickerConfig.framePowderSnow ? 0x40E0FF : -1;
        return -1;
    }

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (!ClickerConfig.showFrames || world == null || client.player == null) {
            marks = List.of();
            return;
        }
        now++;
        if (now % SCAN_EVERY != 0) return;

        int r = Math.max(4, Math.min(32, ClickerConfig.frameRadius));
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
