package com.example.universalmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Рамки вокруг особых блоков: подозрительный песок, подозрительный гравий, светокамень, рыхлый снег.
 * Либо сплошной цветной контур (рисуется в WorldOutlines), либо рамка из частиц по рёбрам блока. Блок определяется
 * по настоящему состоянию мира, поэтому рыхлый снег находится, даже если сверху лежит слой снега.
 */
public class BlockFrames {
    private static final int SCAN_EVERY = 6;
    private static final int MAX_BLOCKS_PER_SCAN = 40;
    private static final int MAX_SOLID_PER_TYPE = 60;
    private static final int MAX_COLLECT = 2000;

    private static final Map<Long, Integer> LAST = new HashMap<>();
    private static int now = 0;

    /** Найденные при последней проверке блоки для сплошного контура. */
    static List<BlockPos> sand = Collections.emptyList();
    static List<BlockPos> gravel = Collections.emptyList();
    static List<BlockPos> glow = Collections.emptyList();
    static List<BlockPos> snow = Collections.emptyList();

    private static void clearSolid() {
        sand = Collections.emptyList();
        gravel = Collections.emptyList();
        glow = Collections.emptyList();
        snow = Collections.emptyList();
    }

    public static void reset() {
        LAST.clear();
        clearSolid();
    }

    /** Частица рамки для блока, либо null — блок не нужен (или включён сплошной контур). */
    private static ParticleEffect effectFor(Block b) {
        if (ClickerConfig.frameSolid) return null;
        if (b == Blocks.SUSPICIOUS_SAND) return ClickerConfig.frameSand ? ParticleTypes.WAX_ON : null;
        if (b == Blocks.SUSPICIOUS_GRAVEL) return ClickerConfig.frameGravel ? ParticleTypes.ELECTRIC_SPARK : null;
        if (b == Blocks.GLOWSTONE) return ClickerConfig.frameGlowstone ? ParticleTypes.GLOW : null;
        if (b == Blocks.POWDER_SNOW) return ClickerConfig.framePowderSnow ? ParticleTypes.END_ROD : null;
        return null;
    }

    /** Как часто обновлять частицы (они живут разное время). */
    private static int respawnFor(ParticleEffect e) {
        return e == ParticleTypes.END_ROD ? 40 : 12;
    }

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (!ClickerConfig.showFrames || world == null || client.player == null) {
            if (!LAST.isEmpty()) LAST.clear();
            clearSolid();
            return;
        }
        now++;
        if (now % SCAN_EVERY != 0) return;

        int r = Math.max(4, Math.min(32, ClickerConfig.frameRadius));
        int r2 = r * r;
        BlockPos c = client.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int blocks = 0;
        boolean solid = ClickerConfig.frameSolid;
        List<BlockPos> tSand = new ArrayList<>(), tGravel = new ArrayList<>(), tGlow = new ArrayList<>(), tSnow = new ArrayList<>();

        scan:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = world.getBlockState(pos);
                    if (st.isAir()) continue;
                    Block b = st.getBlock();
                    if (solid) {
                        List<BlockPos> target = null;
                        if (b == Blocks.SUSPICIOUS_SAND && ClickerConfig.frameSand) target = tSand;
                        else if (b == Blocks.SUSPICIOUS_GRAVEL && ClickerConfig.frameGravel) target = tGravel;
                        else if (b == Blocks.GLOWSTONE && ClickerConfig.frameGlowstone) target = tGlow;
                        else if (b == Blocks.POWDER_SNOW && ClickerConfig.framePowderSnow) target = tSnow;
                        if (target != null) {
                            // Только поверхность скопления: внутри кучи рамки не нужны
                            if (target.size() < MAX_COLLECT && exposed(world, pos)) target.add(pos.toImmutable());
                            continue;
                        }
                    }
                    ParticleEffect fx = effectFor(b);
                    if (fx == null) continue;
                    if (!exposed(world, pos)) continue;
                    long key = pos.asLong();
                    Integer last = LAST.get(key);
                    if (last != null && now - last < respawnFor(fx)) continue;
                    LAST.put(key, now);
                    outline(client, fx, pos.getX(), pos.getY(), pos.getZ());
                    if (++blocks >= MAX_BLOCKS_PER_SCAN) break scan;
                }
            }
        }

        sand = nearest(tSand, c);
        gravel = nearest(tGravel, c);
        glow = nearest(tGlow, c);
        snow = nearest(tSnow, c);

        Iterator<Map.Entry<Long, Integer>> it = LAST.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > 200) it.remove();
        }
    }

    /** Блок виден снаружи: хотя бы один сосед — другой блок (не часть сплошной кучи такого же). */
    private static boolean exposed(ClientWorld w, BlockPos p) {
        Block b = w.getBlockState(p).getBlock();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (Direction d : Direction.values()) {
            m.set(p, d);
            if (w.getBlockState(m).getBlock() != b) return true;
        }
        return false;
    }

    /** Оставляем ближайшие к игроку блоки, не больше лимита. */
    private static List<BlockPos> nearest(List<BlockPos> l, BlockPos c) {
        if (l.size() <= MAX_SOLID_PER_TYPE) return l;
        l.sort((a, b) -> Double.compare(a.getSquaredDistance(c), b.getSquaredDistance(c)));
        return new ArrayList<>(l.subList(0, MAX_SOLID_PER_TYPE));
    }

    /** Частицы в 8 углах и в серединах 12 рёбер единичного блока. */
    private static void outline(MinecraftClient client, ParticleEffect fx, int bx, int by, int bz) {
        double[] ends = {0.0, 1.0};
        for (double a : ends) {
            for (double b : ends) {
                // рёбра вдоль X, Y, Z: два угла и середина каждого
                put(client, fx, bx + 0.5, by + a, bz + b);
                put(client, fx, bx + a, by + 0.5, bz + b);
                put(client, fx, bx + a, by + b, bz + 0.5);
                for (double e : ends) put(client, fx, bx + a, by + b, bz + e);
            }
        }
    }

    private static void put(MinecraftClient client, ParticleEffect fx, double x, double y, double z) {
        client.particleManager.addParticle(fx, x, y, z, 0.0, 0.0, 0.0);
    }
}
