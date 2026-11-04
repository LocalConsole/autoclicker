package com.example.universalmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrappedChestBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ESP сундуков: рамки вокруг всех контейнеров вокруг игрока — сквозь стены и на любом расстоянии.
 *
 * Почему сквозь стены получается. Рамки блоков в этом моде (WorldOutlines) рисуются обычным
 * рендером мира, тем же слоем линий, что и ванильный контур блока под прицелом, — а он
 * проверяет глубину, то есть прячется за блоками. Здесь рамки рисуются не в мире, а поверх него,
 * на HUD: позиция угла куба переводится в точку экрана вручную (та же математика, что у поиска
 * по контейнерам, метод ContainerSearch.drawBox). У HUD никакой глубины нет, поэтому рамка видна
 * сквозь камень, и дальность ограничена только тем, сколько чанков прогрузил клиент.
 *
 * Откуда берётся список. Сундук, бочка, шалкер, печь и прочее — это блок-сущности, и чанк хранит
 * их готовым списком. Поэтому обход идёт по этому списку, а не по всем блокам подряд: при радиусе
 * 96 перебор блоков — это миллионы проверок каждую секунду, а список блок-сущностей — несколько
 * десятков записей на чанк. Имя метода чанка, который отдаёт этот список, в разных версиях
 * маппингов называется по-разному, поэтому он ищется по подписи (без аргументов, возвращает Map
 * с ключами BlockPos) и проверяется по содержимому. Если найти не удалось, включается запасной
 * путь: обычный перебор блоков, но радиусом не больше 48, иначе будет просадка.
 */
public class ChestEsp {
    /** Как часто пересобирать список (в тиках). Рамки рисуются каждый кадр по готовому списку. */
    private static final int SCAN_EVERY = 10;
    /** Больше рамок за раз рисовать нет смысла: экран превращается в кашу, а fill() становится дорогим. */
    private static final int MAX_BOXES = 150;
    /** У скольких ближайших рамок писать подпись. */
    private static final int MAX_LABELS = 60;
    /** Радиус запасного пути (перебор блоков), когда список блок-сущностей недоступен. */
    private static final int FALLBACK_RADIUS = 48;

    private static List<BlockPos> found = Collections.emptyList();
    private static int tick = 0;

    // Поиск метода чанка, отдающего блок-сущности
    private static List<Method> candidates = null;
    private static Method beMethod = null;
    private static boolean fallback = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChestEsp::tick);
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.FOOD_BAR,
                Identifier.of(UniversalMod.MOD_ID, "chest_esp"),
                (context, tc) -> render(context));
    }

    public static int count() {
        return found.size();
    }

    /** true — список берётся перебором блоков, радиус урезан. */
    public static boolean isFallback() {
        return fallback;
    }

    // ---------- Сбор ----------

    private static void tick(MinecraftClient mc) {
        if (!ClickerConfig.chestEsp || mc.player == null || mc.world == null) {
            if (!found.isEmpty()) found = Collections.emptyList();
            return;
        }
        if (++tick % SCAN_EVERY != 0) return;

        int r = Math.max(16, Math.min(256, ClickerConfig.chestEspRadius));
        Vec3d eye = mc.player.getEyePos();
        List<BlockPos> out = new ArrayList<>();

        if (!fallback) {
            collectFromBlockEntities(mc, r, eye, out);
            // Метод так и не нашёлся — переходим на перебор блоков (радиус урезаем)
            if (beMethod == null && candidates != null && candidates.isEmpty()) fallback = true;
        }
        if (fallback) {
            out.clear();
            collectByScan(mc.world, mc.player.getBlockPos(), Math.min(r, FALLBACK_RADIUS), out);
        }

        if (out.size() > MAX_BOXES) {
            out.sort((a, b) -> Double.compare(dist2(a, eye), dist2(b, eye)));
            out = new ArrayList<>(out.subList(0, MAX_BOXES));
        }
        found = out;
    }

    private static double dist2(BlockPos p, Vec3d eye) {
        double dx = p.getX() + 0.5 - eye.x, dy = p.getY() + 0.5 - eye.y, dz = p.getZ() + 0.5 - eye.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void collectFromBlockEntities(MinecraftClient mc, int r, Vec3d eye, List<BlockPos> out) {
        ClientWorld world = mc.world;
        double r2 = (double) r * r;
        int cr = (r >> 4) + 1;
        int pcx = mc.player.getBlockPos().getX() >> 4;
        int pcz = mc.player.getBlockPos().getZ() >> 4;

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                BlockPos probe = new BlockPos((cx << 4) + 8, mc.player.getBlockY(), (cz << 4) + 8);
                if (!world.isChunkLoaded(probe)) continue;
                WorldChunk chunk;
                try {
                    chunk = world.getChunk(cx, cz);
                } catch (Throwable t) {
                    continue;
                }
                if (chunk == null) continue;
                Map<?, ?> map = blockEntities(chunk);
                if (map == null) continue;
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!(e.getKey() instanceof BlockPos p)) continue;
                    if (dist2(p, eye) > r2) continue;
                    BlockState st = (e.getValue() instanceof BlockEntity be) ? be.getCachedState() : world.getBlockState(p);
                    if (wanted(st)) out.add(p.toImmutable());
                }
            }
        }
    }

    /**
     * Список блок-сущностей чанка. Метод ищется по подписи и проверяется по содержимому:
     * у чанка есть и другие методы без аргументов, возвращающие Map с ключами BlockPos
     * (например, несериализованные NBT), и взять не тот — значит ничего не найти.
     */
    private static Map<?, ?> blockEntities(WorldChunk chunk) {
        if (beMethod != null) {
            try {
                Object v = beMethod.invoke(chunk);
                return v instanceof Map<?, ?> m ? m : null;
            } catch (Throwable t) {
                beMethod = null;
            }
        }
        if (candidates == null) {
            candidates = new ArrayList<>();
            for (Method m : chunk.getClass().getMethods()) {
                if (m.getParameterCount() != 0) continue;
                if (!Map.class.isAssignableFrom(m.getReturnType())) continue;
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                m.setAccessible(true);
                candidates.add(m);
            }
        }
        for (int i = 0; i < candidates.size(); i++) {
            Method m = candidates.get(i);
            Map<?, ?> map;
            try {
                Object v = m.invoke(chunk);
                if (!(v instanceof Map<?, ?> mm)) { candidates.remove(i--); continue; }
                map = mm;
            } catch (Throwable t) {
                candidates.remove(i--);
                continue;
            }
            if (map.isEmpty()) continue; // пустой чанк ничего не доказывает — проверим на следующем
            Map.Entry<?, ?> any = map.entrySet().iterator().next();
            if (any.getKey() instanceof BlockPos && any.getValue() instanceof BlockEntity) {
                beMethod = m;         // нашли и проверили
                candidates.clear();
                return map;
            }
            candidates.remove(i--);   // не тот метод
        }
        return null;
    }

    /** Запасной путь: обычный перебор блоков небольшим радиусом. */
    private static void collectByScan(ClientWorld world, BlockPos c, int r, List<BlockPos> out) {
        int r2 = r * r;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    pos.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = world.getBlockState(pos);
                    if (st.isAir()) continue;
                    if (wanted(st)) out.add(pos.toImmutable());
                }
            }
        }
    }

    private static boolean wanted(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof AbstractChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock) return true;
        if (!ClickerConfig.chestEspAll) return false;
        return b instanceof AbstractFurnaceBlock || b instanceof HopperBlock || b instanceof DispenserBlock
                || b instanceof BrewingStandBlock || b == Blocks.CRAFTER || b == Blocks.DECORATED_POT;
    }

    // ---------- Отрисовка ----------

    private static int colorOf(Block b) {
        if (b instanceof TrappedChestBlock) return 0xFFFF4040;   // ловушка — красный
        if (b instanceof EnderChestBlock) return 0xFF40E0D0;     // эндер — бирюзовый
        if (b instanceof ShulkerBoxBlock) return 0xFFC060FF;     // шалкер — фиолетовый
        if (b instanceof BarrelBlock) return 0xFFC08040;         // бочка — коричневый
        if (b instanceof AbstractChestBlock) return 0xFFFFC020;  // сундук — жёлтый
        return 0xFF90A0B0;                                       // всё остальное — серо-синий
    }

    private static void render(DrawContext ctx) {
        if (!ClickerConfig.chestEsp || found.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = ContainerSearch.cameraPos(cam, mc);
        Quaternionf q = cam.getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Vector3f fwd = new Vector3f(0, 0, -1).rotate(q);

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        Vec3d eye = mc.player.getEyePos();

        // Ближние — первыми: у них будут подписи, и они рисуются поверх дальних
        List<BlockPos> list = new ArrayList<>(found);
        list.sort((a, b) -> Double.compare(dist2(a, eye), dist2(b, eye)));

        int labels = 0;
        for (BlockPos p : list) {
            BlockState st = mc.world.getBlockState(p);
            if (!wanted(st)) continue;  // блок уже сломали — ждём следующего обхода
            int core = colorOf(st.getBlock());
            int glow = (core & 0x00FFFFFF) | 0x50000000;
            ContainerSearch.drawBox(ctx, p, cp, right, up, fwd, sw, sh, aspect, tanHalf, glow, core);

            if (ClickerConfig.chestEspText && labels < MAX_LABELS) {
                labels++;
                drawLabel(ctx, mc, p, st, cp, right, up, fwd, sw, sh, aspect, tanHalf, eye, core);
            }
        }
    }

    /** Подпись над рамкой: название блока и расстояние до него. */
    private static void drawLabel(DrawContext ctx, MinecraftClient mc, BlockPos p, BlockState st, Vec3d cp,
                                  Vector3f right, Vector3f up, Vector3f fwd, int sw, int sh,
                                  double aspect, double tanHalf, Vec3d eye, int color) {
        double wx = p.getX() + 0.5 - cp.x, wy = p.getY() + 1.15 - cp.y, wz = p.getZ() + 0.5 - cp.z;
        double x = wx * right.x + wy * right.y + wz * right.z;
        double y = wx * up.x + wy * up.y + wz * up.z;
        double z = wx * fwd.x + wy * fwd.y + wz * fwd.z;
        if (z < 0.05) return;  // за спиной

        int sx = (int) Math.round((x / (z * tanHalf * aspect) * 0.5 + 0.5) * sw);
        int sy = (int) Math.round((0.5 - y / (z * tanHalf) * 0.5) * sh);
        if (sx < -200 || sx > sw + 200 || sy < -50 || sy > sh + 50) return;

        String name = st.getBlock().getName().getString();
        int d = (int) Math.round(Math.sqrt(dist2(p, eye)));
        String text = name + " " + d + "м";
        int w = mc.textRenderer.getWidth(text);
        ctx.fill(sx - w / 2 - 2, sy - 10, sx + w / 2 + 2, sy + 1, 0x80000000);
        ctx.drawTextWithShadow(mc.textRenderer, text, sx - w / 2, sy - 9, color);
    }
}
