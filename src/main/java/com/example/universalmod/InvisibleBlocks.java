package com.example.universalmod;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Показ невидимых блоков (барьер, структурная пустота, свет): вокруг игрока ищем такие блоки и рисуем на них
 * маркер-частицу, как это делает игра, когда в руке барьер. Работает в любом режиме игры, без предмета в руке.
 */
public class InvisibleBlocks {
    private static final int SCAN_EVERY = 10;      // тиков между проверками
    private static final int RESPAWN_AFTER = 30;   // маркер живёт ~4 секунды, обновляем заранее
    private static final int MAX_PER_SCAN = 500;

    private static final Map<Long, Integer> LAST = new HashMap<>();
    private static int now = 0;
    private static final int MAX_OUTLINES_PER_TYPE = 1500;

    /** Найденные при последней проверке блоки для сплошного контура. */
    static List<BlockPos> barrier = Collections.emptyList();
    static List<BlockPos> voidBlocks = Collections.emptyList();
    static List<BlockPos> light = Collections.emptyList();

    private static void clearOutlines() {
        barrier = Collections.emptyList();
        voidBlocks = Collections.emptyList();
        light = Collections.emptyList();
        EdgeMask.INVIS.clear();
    }

    public static void reset() {
        LAST.clear();
        clearOutlines();
    }

    private static boolean wanted(Block b) {
        if (b == Blocks.BARRIER) return ClickerConfig.invisBarrier;
        if (b == Blocks.STRUCTURE_VOID) return ClickerConfig.invisVoid;
        if (b == Blocks.LIGHT) return ClickerConfig.invisLight;
        return false;
    }

    public static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (!ClickerConfig.showInvisible || world == null || client.player == null) {
            if (!LAST.isEmpty()) LAST.clear();
            clearOutlines();
            return;
        }
        now++;
        if (now % SCAN_EVERY != 0) return;

        int r = Math.max(4, Math.min(32, ClickerConfig.invisRadius));
        int r2 = r * r;
        BlockPos c = client.player.getBlockPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int spawned = 0;
        Map<Long, Integer> tm = new HashMap<>();
        List<BlockPos> tBarrier = new ArrayList<>(), tVoid = new ArrayList<>(), tLight = new ArrayList<>();

        scan:
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = world.getBlockState(pos);
                    if (st.isAir() || !wanted(st.getBlock())) continue;
                    Block blk = st.getBlock();
                    List<BlockPos> bucket = blk == Blocks.BARRIER ? tBarrier : blk == Blocks.STRUCTURE_VOID ? tVoid : tLight;
                    if (bucket.size() < MAX_OUTLINES_PER_TYPE) {
                        int mk = EdgeMask.compute(world, pos, blk);
                        if (mk != 0) {
                            bucket.add(pos.toImmutable());
                            tm.put(pos.asLong(), mk);
                        }
                    }
                    long key = pos.asLong();
                    Integer last = LAST.get(key);
                    if (last != null && now - last < RESPAWN_AFTER) continue;
                    LAST.put(key, now);
                    client.particleManager.addParticle(
                            new BlockStateParticleEffect(ParticleTypes.BLOCK_MARKER, st),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
                    if (++spawned >= MAX_PER_SCAN) break scan;
                }
            }
        }

        barrier = tBarrier;
        voidBlocks = tVoid;
        light = tLight;
        EdgeMask.INVIS.clear();
        EdgeMask.INVIS.putAll(tm);

        // Забываем блоки, которых давно не видели (убрали или ушли далеко)
        Iterator<Map.Entry<Long, Integer>> it = LAST.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > 200) it.remove();
        }
    }
}
