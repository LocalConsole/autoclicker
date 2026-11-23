package com.example.universalmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Данные карты: цвет вида сверху для каждой колонки блоков, по чанкам (16×16 цветов на чанк).
 * Чанки снимаются, когда загружаются и пока вы рядом; всё сохраняется на диск по областям 32×32 чанка,
 * так что на карте мира видно всё, где вы уже были, и после перезахода.
 * Папка: .minecraft/universal_map/<сервер или мир>/<измерение>/
 */
public class MapData {
    public static final int UNKNOWN = 0;
    private static final Path ROOT = FabricLoader.getInstance().getGameDir().resolve("universal_map");

    /** Цвета чанков текущего мира и измерения: ключ — ChunkPos.toLong. */
    private static final Map<Long, int[]> chunks = new HashMap<>();
    private static final Set<Long> loadedRegions = new HashSet<>();
    private static final Set<Long> dirtyRegions = new HashSet<>();
    private static final ArrayDeque<Long> queue = new ArrayDeque<>();
    private static final Set<Long> queued = new HashSet<>();
    private static String key = null; // мир + измерение
    private static long ticks = 0;
    /** Меняется при любом обновлении — по нему карты понимают, что пора перерисоваться. */
    public static volatile int version = 0;

    public static void register() {
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> enqueue(chunk.getPos().toLong()));
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> saveAll());
    }

    private static void enqueue(long cp) {
        if (queued.add(cp)) queue.add(cp);
    }

    private static String currentKey(MinecraftClient mc) {
        String w = Waypoints.worldKey(mc).replaceAll("[^a-zA-Z0-9._-]", "_");
        String d = Waypoints.currentDim(mc).replaceAll("[^a-zA-Z0-9._-]", "_");
        return w + "/" + d;
    }

    private static Path dir() {
        return ROOT.resolve(key);
    }

    public static void tick(MinecraftClient mc) {
        ticks++;
        if (mc.player == null || mc.world == null) {
            if (key != null) {
                saveAll();
                clear();
            }
            return;
        }
        String k = currentKey(mc);
        if (!k.equals(key)) {
            if (key != null) saveAll();
            clear();
            key = k;
            // все уже загруженные чанки вокруг — в очередь
            int r = mc.options.getViewDistance().getValue();
            ChunkPos pc = mc.player.getChunkPos();
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) enqueue(ChunkPos.toLong(pc.x + dx, pc.z + dz));
        }
        if (!ClickerConfig.mmShow && !(mc.currentScreen instanceof WorldMapScreen) && !ClickerConfig.mapRecord) return;

        // ближние чанки пересниманием раз в 2 секунды (там что-то строят/ломают)
        if (ticks % 40 == 0) {
            ChunkPos pc = mc.player.getChunkPos();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) enqueue(ChunkPos.toLong(pc.x + dx, pc.z + dz));
        }
        // не больше нескольких чанков за тик — без подвисаний
        for (int i = 0; i < 6 && !queue.isEmpty(); i++) {
            long cp = queue.poll();
            queued.remove(cp);
            scan(mc.world, cp);
        }
        if (ticks % 1200 == 0) saveDirty(); // раз в минуту
    }

    private static void clear() {
        chunks.clear();
        resetReadCache();
        loadedRegions.clear();
        dirtyRegions.clear();
        queue.clear();
        queued.clear();
        key = null;
        version++;
    }

    // ---------- Снимок чанка ----------

    private static void scan(ClientWorld w, long cp) {
        int cx = ChunkPos.getPackedX(cp), cz = ChunkPos.getPackedZ(cp);
        if (!w.getChunkManager().isChunkLoaded(cx, cz)) return;
        WorldChunk ch = w.getChunk(cx, cz);
        if (ch == null) return;
        ensureRegion(cx >> 5, cz >> 5);
        int[] col = new int[256];
        boolean any = false;
        boolean ceiling = w.getDimension().hasCeiling();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                int c;
                if (ceiling) {
                    c = caveColor(w, wx, 100, wz, m); // в Незере крыша — снимаем «пещерный» слой
                } else {
                    int top = ch.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z);
                    c = surfaceColor(w, wx, top, wz, m);
                }
                col[x + z * 16] = c;
                if (c != UNKNOWN) any = true;
            }
        }
        if (!any) return;
        chunks.put(cp, col);
        resetReadCache();
        dirtyRegions.add(regionKey(cx >> 5, cz >> 5));
        version++;
    }

    private static int surfaceColor(ClientWorld w, int x, int top, int z, BlockPos.Mutable m) {
        int bottom = w.getBottomY();
        int y = top;
        BlockState st = null;
        int rgb = 0;
        // сверху вниз до первого блока с цветом на карте (стекло, воздух и т.п. пропускаем)
        for (int i = 0; i < 24 && y >= bottom; i++, y--) {
            m.set(x, y, z);
            st = w.getBlockState(m);
            if (st.isAir()) continue;
            rgb = st.getMapColor(w, m).color;
            if (rgb != 0) break;
        }
        if (rgb == 0 || st == null) return UNKNOWN;
        // настоящий цвет травы/листвы/воды с учётом биома (как в игре), а не плоский цвет карты
        m.set(x, y, z);
        rgb = tinted(w, st, m, rgb);
        // вода: чем глубже, тем темнее
        FluidState fs = st.getFluidState();
        if (fs.isIn(FluidTags.WATER)) {
            int depth = 0;
            int yy = y - 1;
            while (depth < 12 && yy >= bottom) {
                m.set(x, yy, z);
                if (!w.getBlockState(m).getFluidState().isIn(FluidTags.WATER)) break;
                depth++;
                yy--;
            }
            float f = 1.0f - Math.min(0.55f, depth * 0.05f);
            return withHeight(shade(rgb, f), y);
        }
        // рельеф: свет «с северо-запада». Склоны к свету светлее, от света темнее, плавно по высоте
        int north = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1) - 1;
        int west = w.getTopY(Heightmap.Type.WORLD_SURFACE, x - 1, z) - 1;
        float diff = (y - north) + (y - west) * 0.6f;
        float f = 1.0f + Math.max(-0.32f, Math.min(0.22f, diff * 0.07f));
        return withHeight(shade(rgb, f), y);
    }

    /** Пещерный слой: первый пол с воздухом над ним, начиная с fromY вниз. */
    public static int caveColor(ClientWorld w, int x, int fromY, int z, BlockPos.Mutable m) {
        int bottom = w.getBottomY();
        boolean airAbove = false;
        for (int y = fromY, i = 0; i < 40 && y >= bottom; i++, y--) {
            m.set(x, y, z);
            BlockState st = w.getBlockState(m);
            if (st.isAir() || st.getMapColor(w, m).color == 0) {
                airAbove = true;
                continue;
            }
            if (!airAbove) continue;
            int rgb = st.getMapColor(w, m).color;
            float f = Math.max(0.45f, 1.0f - (fromY - y) * 0.03f);
            return withHeight(shade(rgb, f), y);
        }
        return UNKNOWN;
    }

    /** Цвет с оттенком биома: трава, листва, вода, лианы. Снег на траве — без оттенка. */
    private static int tinted(ClientWorld w, BlockState st, BlockPos pos, int mapRgb) {
        try {
            if (st.getProperties().contains(net.minecraft.state.property.Properties.SNOWY)
                    && st.get(net.minecraft.state.property.Properties.SNOWY)) return mapRgb;
            int tint = MinecraftClient.getInstance().getBlockColors().getColor(st, w, pos, 0);
            if (tint == -1 || tint == 0xFFFFFF) {
                if (st.getFluidState().isIn(FluidTags.WATER)) {
                    tint = net.minecraft.client.color.world.BiomeColors.getWaterColor(w, pos);
                } else {
                    return mapRgb;
                }
            }
            tint &= 0xFFFFFF;
            // 75% оттенка биома + 25% цвета карты — сочно, но не кислотно
            int r = (((tint >> 16) & 255) * 3 + ((mapRgb >> 16) & 255)) / 4;
            int g = (((tint >> 8) & 255) * 3 + ((mapRgb >> 8) & 255)) / 4;
            int b = ((tint & 255) * 3 + (mapRgb & 255)) / 4;
            return (r << 16) | (g << 8) | b;
        } catch (Throwable t) {
            return mapRgb;
        }
    }

    /**
     * Цвет + высота в одном числе: в старшем байте — высота (y + 64), ниже — RGB.
     * Высота нужна, чтобы рисовать ступеньки рельефа на крупной карте.
     */
    public static int withHeight(int rgb, int y) {
        int h = Math.max(1, Math.min(255, y + 64));
        return (h << 24) | (rgb & 0xFFFFFF);
    }

    public static int heightOf(int c) {
        return (c >>> 24) - 64;
    }

    public static int shade(int rgb, float f) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
        int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
        int b = Math.min(255, (int) ((rgb & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    // ---------- Чтение ----------

    private static long lastChunk = Long.MIN_VALUE;
    private static int[] lastCols = null;

    /** Цвет колонки (ARGB) или UNKNOWN, если тут ещё не были. */
    public static int colorAt(int x, int z) {
        int cx = x >> 4, cz = z >> 4;
        long cp = ChunkPos.toLong(cx, cz);
        int[] c;
        if (cp == lastChunk) {
            c = lastCols;
        } else {
            ensureRegion(cx >> 5, cz >> 5);
            c = chunks.get(cp);
            lastChunk = cp;
            lastCols = c;
        }
        if (c == null) return UNKNOWN;
        return c[(x & 15) + (z & 15) * 16];
    }

    /** Сбросить быстрый кэш чтения (после обновления данных). */
    public static void resetReadCache() {
        lastChunk = Long.MIN_VALUE;
        lastCols = null;
    }

    // ---------- Файлы по областям 32×32 чанка ----------

    private static long regionKey(int rx, int rz) {
        return ((long) rx << 32) | (rz & 0xFFFFFFFFL);
    }

    private static Path regionFile(int rx, int rz) {
        return dir().resolve("r." + rx + "." + rz + ".umap");
    }

    private static void ensureRegion(int rx, int rz) {
        if (key == null) return;
        long rk = regionKey(rx, rz);
        if (!loadedRegions.add(rk)) return;
        Path f = regionFile(rx, rz);
        if (!Files.exists(f)) return;
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(Files.newInputStream(f)))) {
            int n = in.readInt();
            for (int i = 0; i < n; i++) {
                int idx = in.readShort();
                int[] col = new int[256];
                for (int j = 0; j < 256; j++) col[j] = in.readInt();
                long cp = ChunkPos.toLong((rx << 5) + (idx & 31), (rz << 5) + ((idx >> 5) & 31));
                chunks.putIfAbsent(cp, col);
            }
            resetReadCache();
            version++;
        } catch (Exception ignored) {
        }
    }

    private static void saveRegion(int rx, int rz) {
        if (!ClickerConfig.mapRecord || key == null) return;
        try {
            Files.createDirectories(dir());
            Path f = regionFile(rx, rz);
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            int n = 0;
            for (int i = 0; i < 1024; i++) {
                if (chunks.containsKey(ChunkPos.toLong((rx << 5) + (i & 31), (rz << 5) + (i >> 5)))) n++;
            }
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(tmp)))) {
                out.writeInt(n);
                for (int i = 0; i < 1024; i++) {
                    int[] col = chunks.get(ChunkPos.toLong((rx << 5) + (i & 31), (rz << 5) + (i >> 5)));
                    if (col == null) continue;
                    out.writeShort(i);
                    for (int v : col) out.writeInt(v);
                }
            }
            Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void saveDirty() {
        for (long rk : dirtyRegions) saveRegion((int) (rk >> 32), (int) rk);
        dirtyRegions.clear();
    }

    public static void saveAll() {
        saveDirty();
    }

    /** Сколько чанков уже известно (для подписи на карте мира). */
    public static int knownChunks() {
        return chunks.size();
    }
}
