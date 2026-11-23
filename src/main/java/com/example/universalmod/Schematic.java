package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Схема (как в Litematica): прямоугольная область блоков. Хранится в папке universal_schematics
 * в файле .uschem (сжатый JSON): размер, палитра состояний блоков и номер палитры для каждой клетки.
 */
public class Schematic {
    public static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("universal_schematics");
    public static final String EXT = ".uschem";
    private static final Gson GSON = new GsonBuilder().create();

    public String name;
    public int sx, sy, sz;
    /** Палитра: индекс 0 всегда воздух. */
    public final List<BlockState> palette = new ArrayList<>();
    /** Номер палитры для клетки (x + z*sx + y*sx*sz). */
    public int[] blocks;

    private static class Data {
        int version = 1;
        String name;
        int sx, sy, sz;
        List<String> palette;
        int[] blocks;
    }

    public int index(int x, int y, int z) {
        return x + z * sx + y * sx * sz;
    }

    public BlockState get(int x, int y, int z) {
        return palette.get(blocks[index(x, y, z)]);
    }

    /** Копия области мира между двумя точками (включительно). */
    public static Schematic capture(World world, BlockPos a, BlockPos b, String name) {
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()), y1 = Math.max(a.getY(), b.getY()), z1 = Math.max(a.getZ(), b.getZ());
        Schematic s = new Schematic();
        s.name = name;
        s.sx = x1 - x0 + 1;
        s.sy = y1 - y0 + 1;
        s.sz = z1 - z0 + 1;
        s.blocks = new int[s.sx * s.sy * s.sz];
        s.palette.add(Blocks.AIR.getDefaultState());
        Map<BlockState, Integer> ids = new HashMap<>();
        ids.put(Blocks.AIR.getDefaultState(), 0);
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = 0; y < s.sy; y++) {
            for (int z = 0; z < s.sz; z++) {
                for (int x = 0; x < s.sx; x++) {
                    m.set(x0 + x, y0 + y, z0 + z);
                    BlockState st = world.getBlockState(m);
                    if (st.isAir()) st = Blocks.AIR.getDefaultState();
                    Integer id = ids.get(st);
                    if (id == null) {
                        id = s.palette.size();
                        s.palette.add(st);
                        ids.put(st, id);
                    }
                    s.blocks[s.index(x, y, z)] = id;
                }
            }
        }
        return s;
    }

    public long volume() {
        return (long) sx * sy * sz;
    }

    // ---------- Файлы ----------

    public Path file() {
        return DIR.resolve(name + EXT);
    }

    public boolean save() {
        try {
            Files.createDirectories(DIR);
            Data d = new Data();
            d.name = name;
            d.sx = sx;
            d.sy = sy;
            d.sz = sz;
            d.palette = new ArrayList<>();
            for (BlockState st : palette) d.palette.add(toString(st));
            d.blocks = blocks;
            try (Writer w = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file())), StandardCharsets.UTF_8)) {
                GSON.toJson(d, w);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Schematic load(Path p) {
        try (Reader r = new InputStreamReader(new GZIPInputStream(Files.newInputStream(p)), StandardCharsets.UTF_8)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null || d.blocks == null || d.palette == null) return null;
            Schematic s = new Schematic();
            String fn = p.getFileName().toString();
            s.name = fn.endsWith(EXT) ? fn.substring(0, fn.length() - EXT.length()) : fn;
            s.sx = d.sx;
            s.sy = d.sy;
            s.sz = d.sz;
            if ((long) s.sx * s.sy * s.sz != d.blocks.length) return null;
            for (String str : d.palette) s.palette.add(fromString(str));
            s.blocks = d.blocks;
            for (int i = 0; i < s.blocks.length; i++) {
                if (s.blocks[i] < 0 || s.blocks[i] >= s.palette.size()) s.blocks[i] = 0;
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    /** Все файлы схем, по имени. */
    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(DIR)) return out;
        try (Stream<Path> st = Files.list(DIR)) {
            st.filter(p -> p.getFileName().toString().endsWith(EXT)).sorted().forEach(out::add);
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---------- Замена блоков ----------

    /** Заменяет во всей схеме блок from на блок to, сохраняя совпадающие свойства (поворот, половина и т.п.). */
    public void replace(Block from, Block to) {
        for (int i = 0; i < palette.size(); i++) {
            BlockState st = palette.get(i);
            if (st.getBlock() != from) continue;
            palette.set(i, copyProps(st, to.getDefaultState()));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copyProps(BlockState src, BlockState dst) {
        for (Property<?> p : src.getProperties()) {
            Property<?> q = dst.getBlock().getStateManager().getProperty(p.getName());
            if (q == null) continue;
            String v = valueName(src, p);
            Optional<?> parsed = q.parse(v);
            if (parsed.isPresent()) dst = dst.with((Property) q, (Comparable) parsed.get());
        }
        return dst;
    }

    // ---------- Состояние блока <-> строка ----------

    public static String toString(BlockState st) {
        StringBuilder sb = new StringBuilder(Registries.BLOCK.getId(st.getBlock()).toString());
        boolean first = true;
        for (Property<?> p : st.getProperties()) {
            sb.append(first ? '[' : ',');
            first = false;
            sb.append(p.getName()).append('=').append(valueName(st, p));
        }
        if (!first) sb.append(']');
        return sb.toString();
    }

    private static <T extends Comparable<T>> String valueName(BlockState st, Property<T> p) {
        return p.name(st.get(p));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState fromString(String s) {
        String id = s;
        String props = "";
        int br = s.indexOf('[');
        if (br >= 0) {
            id = s.substring(0, br);
            props = s.substring(br + 1, s.endsWith("]") ? s.length() - 1 : s.length());
        }
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return Blocks.AIR.getDefaultState();
        Block block = Registries.BLOCK.get(ident);
        BlockState st = block.getDefaultState();
        if (!props.isEmpty()) {
            for (String kv : props.split(",")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                Property<?> p = block.getStateManager().getProperty(kv.substring(0, eq).trim());
                if (p == null) continue;
                Optional<?> v = p.parse(kv.substring(eq + 1).trim());
                if (v.isPresent()) st = st.with((Property) p, (Comparable) v.get());
            }
        }
        return st;
    }
}
