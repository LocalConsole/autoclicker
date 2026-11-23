package com.example.universalmod;

/**
 * Как красить один пиксель карты. Когда блок занимает несколько пикселей (крупный масштаб, высокое
 * разрешение), рисуем «как в Xaero»: зернистость внутри блока (4×4, как пиксели текстуры) и ступеньки
 * рельефа по краям блоков — светлая кромка у верхнего края, тень у нижнего.
 */
public final class MapPainter {
    private MapPainter() { }

    public static final int UNKNOWN_BG = 0xFF16191D;

    private static int h(int bx, int bz) {
        int c = MapData.colorAt(bx, bz);
        return c == MapData.UNKNOWN ? Integer.MIN_VALUE : MapData.heightOf(c);
    }

    private static int hash(int x, int z, int s) {
        int v = x * 73856093 ^ z * 19349663 ^ s * 83492791;
        v ^= v >>> 13;
        v *= 0x5bd1e995;
        v ^= v >>> 15;
        return v;
    }

    /**
     * @param texelsPerBlock сколько пикселей картинки приходится на один блок
     * @return ARGB (непрозрачный)
     */
    public static int texel(double wx, double wz, double texelsPerBlock, int stored) {
        if (stored == MapData.UNKNOWN) return UNKNOWN_BG;
        int rgb = stored & 0xFFFFFF;
        if (texelsPerBlock < 2.0) return 0xFF000000 | rgb;
        int bx = (int) Math.floor(wx), bz = (int) Math.floor(wz);
        double fx = wx - bx, fz = wz - bz;
        int my = MapData.heightOf(stored);
        float f = 1.0f;
        // зерно «текстуры»: 4×4 клетки внутри блока, ±6%
        int cell = (int) (fx * 4) + (int) (fz * 4) * 4;
        f += ((hash(bx, bz, cell) & 0xFF) / 255f - 0.5f) * 0.12f;
        // ступеньки рельефа
        double edge = Math.min(0.34, 1.0 / texelsPerBlock);
        if (fx < edge || fz < edge) {
            int hw = fx < edge ? h(bx - 1, bz) : Integer.MIN_VALUE;
            int hn = fz < edge ? h(bx, bz - 1) : Integer.MIN_VALUE;
            int lower = Math.max(hw == Integer.MIN_VALUE ? -9999 : my - hw, hn == Integer.MIN_VALUE ? -9999 : my - hn);
            if (lower > 0) f += 0.16f;          // этот блок выше соседа — освещённая кромка
            else if (lower > -9999 && lower < 0) f -= 0.22f; // ниже — тень от соседа
        }
        return 0xFF000000 | MapData.shade(rgb, Math.max(0.4f, f));
    }
}
