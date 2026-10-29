package com.example.universalmod;

import net.minecraft.block.Block;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Какие из 12 рёбер блока видны игроку. Ребро не рисуется, если обе примыкающие к нему грани закрыты
 * (соседний блок того же типа или сплошной непрозрачный блок), а также если оно лежит посреди плоской
 * поверхности скопления одинаковых блоков.
 */
final class EdgeMask {
    static final Map<Long, Integer> FRAMES = new HashMap<>();
    static final Map<Long, Integer> INVIS = new HashMap<>();

    private EdgeMask() {}

    /** Номер ребра: ось * 4 + (знак по первой оси ? 2 : 0) + (знак по второй оси ? 1 : 0). */
    static int compute(ClientWorld w, BlockPos p, Block self) {
        int mask = 0;
        BlockPos.Mutable n1 = new BlockPos.Mutable();
        BlockPos.Mutable n2 = new BlockPos.Mutable();
        BlockPos.Mutable dg = new BlockPos.Mutable();
        for (int axis = 0; axis < 3; axis++) {
            int bAx = (axis + 1) % 3;
            int cAx = (axis + 2) % 3;
            for (int sb = -1; sb <= 1; sb += 2) {
                for (int sc = -1; sc <= 1; sc += 2) {
                    n1.set(p.getX() + (bAx == 0 ? sb : 0), p.getY() + (bAx == 1 ? sb : 0), p.getZ() + (bAx == 2 ? sb : 0));
                    n2.set(p.getX() + (cAx == 0 ? sc : 0), p.getY() + (cAx == 1 ? sc : 0), p.getZ() + (cAx == 2 ? sc : 0));
                    dg.set(n1.getX() + (cAx == 0 ? sc : 0), n1.getY() + (cAx == 1 ? sc : 0), n1.getZ() + (cAx == 2 ? sc : 0));
                    boolean s1 = w.getBlockState(n1).getBlock() == self;
                    boolean s2 = w.getBlockState(n2).getBlock() == self;
                    boolean c1 = s1 || w.getBlockState(n1).isOpaqueFullCube();
                    boolean c2 = s2 || w.getBlockState(n2).isOpaqueFullCube();
                    if (c1 && c2) continue;                                   // обе грани закрыты
                    boolean sd = w.getBlockState(dg).getBlock() == self;
                    if (!sd && s1 != s2) continue;                            // середина плоской поверхности
                    mask |= 1 << (axis * 4 + (sb > 0 ? 2 : 0) + (sc > 0 ? 1 : 0));
                }
            }
        }
        return mask;
    }

    /** Концы ребра с номером e для блока p: {minX,minY,minZ,maxX,maxY,maxZ} с небольшой толщиной. */
    static double[] segment(BlockPos p, int e) {
        int axis = e / 4;
        int sb = (e & 2) != 0 ? 1 : 0;
        int sc = (e & 1) != 0 ? 1 : 0;
        int bAx = (axis + 1) % 3;
        int cAx = (axis + 2) % 3;
        double t = 0.002;
        double[] lo = new double[3];
        double[] hi = new double[3];
        double[] base = {p.getX(), p.getY(), p.getZ()};
        lo[axis] = base[axis];
        hi[axis] = base[axis] + 1.0;
        lo[bAx] = base[bAx] + sb - t;
        hi[bAx] = base[bAx] + sb + t;
        lo[cAx] = base[cAx] + sc - t;
        hi[cAx] = base[cAx] + sc + t;
        return new double[]{lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]};
    }
}
