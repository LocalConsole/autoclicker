package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractChestBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.Camera;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Поиск предмета по сундукам, бочкам и шалкерам: контейнеры с нужным предметом обводятся красной рамкой
 * сквозь стены. Клиент знает содержимое только тех контейнеров, которые вы уже открывали, поэтому
 * мод запоминает содержимое при открытии.
 */
public class ContainerSearch {
    /** Текст запроса (в памяти, до выхода из игры). */
    public static String query = "";

    private static final Map<BlockPos, String> CACHE = new HashMap<>();
    private static ClientWorld cacheWorld = null;

    private static BlockPos pendingPos = null;
    private static int pendingTick = 0;
    private static int tickCounter = 0;
    private static BlockPos[] activePos = null;
    private static ScreenHandler activeHandler = null;

    // Одиночная игра: содержимое читается прямо из встроенного сервера, запоминание не нужно
    private static volatile Map<BlockPos, String> spResult = new HashMap<>();
    private static boolean scanQueued = false;

    // Сервер: запоминаем открытое и храним на диске отдельно для каждого сервера и измерения
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod_containers.json");
    private static final Gson GSON = new Gson();
    private static Map<String, Map<String, String>> disk = null;
    private static String cacheKey = null;
    private static boolean dirty = false;

    private static Method camPosMethod;
    private static boolean camPosResolved = false;

    public static void register() {
        query = ClickerConfig.searchQuery == null ? "" : ClickerConfig.searchQuery;
        // Запоминаем, какой контейнер игрок только что открыл
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState st = world.getBlockState(pos);
            if (isContainer(st)) {
                pendingPos = pos.toImmutable();
                pendingTick = tickCounter;
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(ContainerSearch::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> saveIfDirty());

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.FOOD_BAR,
                Identifier.of(UniversalMod.MOD_ID, "container_search"),
                (context, tc) -> render(context));
    }

    private static Map<BlockPos, String> active() {
        MinecraftClient c = MinecraftClient.getInstance();
        return (c != null && c.getServer() != null) ? spResult : CACHE;
    }

    public static int cachedCount() { return active().size(); }

    public static int matchCount() {
        String[] tokens = tokens();
        if (tokens.length == 0) return 0;
        int n = 0;
        for (String s : active().values()) if (matches(s, tokens)) n++;
        return n;
    }

    // ---------- Запоминание содержимого ----------

    private static boolean isContainer(BlockState st) {
        Block b = st.getBlock();
        if (b instanceof EnderChestBlock) return false;
        return b instanceof AbstractChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock;
    }

    private static BlockPos[] positionsFor(ClientWorld world, BlockPos pos) {
        BlockState st = world.getBlockState(pos);
        if (st.getBlock() instanceof ChestBlock && st.contains(ChestBlock.CHEST_TYPE)
                && st.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.offset(ChestBlock.getFacing(st));
            return new BlockPos[]{pos, other.toImmutable()};
        }
        return new BlockPos[]{pos};
    }

    private static String currentKey(MinecraftClient client) {
        ServerInfo si = client.getCurrentServerEntry();
        String srv = si != null ? si.address : "other";
        return srv + "|" + client.world.getRegistryKey().getValue();
    }

    private static void tick(MinecraftClient client) {
        tickCounter++;
        if (client.world == null || client.player == null) {
            saveIfDirty();
            CACHE.clear();
            spResult = new HashMap<>();
            cacheWorld = null;
            cacheKey = null;
            activePos = null;
            activeHandler = null;
            return;
        }
        if (cacheWorld != client.world) {
            saveIfDirty();
            CACHE.clear();
            spResult = new HashMap<>();
            cacheWorld = client.world;
            activePos = null;
            activeHandler = null;
            cacheKey = client.getServer() != null ? null : currentKey(client);
            loadFromDisk();
        }

        // Одиночная игра: читаем сервер напрямую
        if (client.getServer() != null) {
            if (tickCounter % 10 == 0) scanIntegrated(client);
            return;
        }
        if (dirty && tickCounter % 200 == 0) saveIfDirty();

        if (!(client.currentScreen instanceof HandledScreen<?> hs)) {
            activePos = null;
            activeHandler = null;
            return;
        }
        ScreenHandler handler = hs.getScreenHandler();
        if (!(handler instanceof GenericContainerScreenHandler || handler instanceof ShulkerBoxScreenHandler)) {
            activePos = null;
            activeHandler = null;
            return;
        }

        // Новый экран контейнера: привязываем к последнему кликнутому блоку
        if (handler != activeHandler) {
            activeHandler = handler;
            activePos = null;
            if (pendingPos != null && tickCounter - pendingTick <= 100) {
                activePos = positionsFor(client.world, pendingPos);
            }
            pendingPos = null;
        }
        if (activePos == null) return;

        StringBuilder sb = new StringBuilder();
        for (Slot slot : handler.slots) {
            if (slot.inventory == client.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            appendStack(sb, stack);
            ContainerComponent inner = stack.get(DataComponentTypes.CONTAINER);
            if (inner != null) {
                for (ItemStack s : inner.iterateNonEmpty()) appendStack(sb, s);
            }
        }
        String text = sb.toString();
        for (BlockPos p : activePos) {
            if (!text.equals(CACHE.put(p, text))) dirty = true;
        }
    }

    private static void appendStack(StringBuilder sb, ItemStack stack) {
        sb.append(stack.getName().getString().toLowerCase(Locale.ROOT)).append('\n');
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        sb.append(id.replace('_', ' ')).append('\n');
    }

    // ---------- Одиночная игра ----------

    private static void scanIntegrated(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null || scanQueued) return;
        ServerWorld sw = server.getWorld(client.world.getRegistryKey());
        if (sw == null) return;
        Vec3d eye = client.player.getEyePos();
        double r = ClickerConfig.searchRadius + 1;
        int cx0 = (int) Math.floor(eye.x - r) >> 4, cx1 = (int) Math.floor(eye.x + r) >> 4;
        int cz0 = (int) Math.floor(eye.z - r) >> 4, cz1 = (int) Math.floor(eye.z + r) >> 4;
        scanQueued = true;
        server.execute(() -> {
            try {
                Map<BlockPos, String> res = new HashMap<>();
                for (int cx = cx0; cx <= cx1; cx++) {
                    for (int cz = cz0; cz <= cz1; cz++) {
                        WorldChunk ch = sw.getChunkManager().getWorldChunk(cx, cz);
                        if (ch == null) continue;
                        for (BlockEntity be : ch.getBlockEntities().values()) {
                            if (!(be instanceof Inventory inv) || !isContainer(be.getCachedState())) continue;
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < inv.size(); i++) {
                                ItemStack st = inv.getStack(i);
                                if (st.isEmpty()) continue;
                                appendStack(sb, st);
                                ContainerComponent inner = st.get(DataComponentTypes.CONTAINER);
                                if (inner != null) for (ItemStack x : inner.iterateNonEmpty()) appendStack(sb, x);
                            }
                            res.put(be.getPos().toImmutable(), sb.toString());
                        }
                    }
                }
                spResult = res;
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                scanQueued = false;
            }
        });
    }

    // ---------- Хранение на диске (серверы) ----------

    private static Map<String, Map<String, String>> disk() {
        if (disk == null) {
            disk = new HashMap<>();
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE)) {
                    Map<String, Map<String, String>> m = GSON.fromJson(r,
                            new TypeToken<Map<String, Map<String, String>>>() {}.getType());
                    if (m != null) disk = m;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return disk;
    }

    private static void loadFromDisk() {
        if (cacheKey == null) return;
        Map<String, String> m = disk().get(cacheKey);
        if (m == null) return;
        for (Map.Entry<String, String> e : m.entrySet()) {
            String[] xyz = e.getKey().split(",");
            if (xyz.length != 3) continue;
            try {
                CACHE.put(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])), e.getValue());
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void saveIfDirty() {
        if (!dirty || cacheKey == null) return;
        dirty = false;
        Map<String, String> m = new HashMap<>();
        for (Map.Entry<BlockPos, String> e : CACHE.entrySet()) {
            BlockPos p = e.getKey();
            m.put(p.getX() + "," + p.getY() + "," + p.getZ(), e.getValue());
        }
        disk().put(cacheKey, m);
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(disk(), w);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- Поиск ----------

    private static String[] tokens() {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return new String[0];
        return q.split("\\s+");
    }

    private static boolean matches(String text, String[] tokens) {
        for (String t : tokens) if (!text.contains(t)) return false;
        return true;
    }

    /** Контейнеры с искомым предметом в радиусе поиска (для рамки в мире). */
    public static java.util.List<BlockPos> matchingPositions(MinecraftClient mc) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        if (!ClickerConfig.containerSearch || mc.player == null || mc.world == null) return out;
        String[] tokens = tokens();
        if (tokens.length == 0) return out;
        double r2 = (double) ClickerConfig.searchRadius * ClickerConfig.searchRadius;
        Vec3d eye = mc.player.getEyePos();
        for (Map.Entry<BlockPos, String> e : active().entrySet()) {
            BlockPos p = e.getKey();
            double dx = p.getX() + 0.5 - eye.x, dy = p.getY() + 0.5 - eye.y, dz = p.getZ() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            if (!mc.world.isChunkLoaded(p) || !isContainer(mc.world.getBlockState(p))) continue;
            if (matches(e.getValue(), tokens)) out.add(p);
        }
        return out;
    }

    // ---------- Подсветка слотов в открытом окне ----------

    /**
     * В открытом инвентаре/контейнере обводит слоты с искомым предметом. Если предмет лежит внутри шалкера
     * (или другого предмета-контейнера), обводится сам шалкер и в его углу ставится метка «▣».
     */
    public static void drawSlotMarks(DrawContext ctx, ScreenHandler handler, int ox, int oy) {
        if (!ClickerConfig.containerSearch || handler == null) return;
        String[] tokens = tokens();
        if (tokens.length == 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        var tr = mc.textRenderer;
        for (Slot slot : handler.slots) {
            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            StringBuilder own = new StringBuilder();
            appendStack(own, st);
            boolean direct = matches(own.toString(), tokens);
            boolean inside = false;
            ContainerComponent inner = st.get(DataComponentTypes.CONTAINER);
            if (inner != null) {
                StringBuilder in = new StringBuilder();
                for (ItemStack s : inner.iterateNonEmpty()) appendStack(in, s);
                inside = matches(in.toString(), tokens);
            }
            if (!direct && !inside) continue;
            int x = ox + slot.x, y = oy + slot.y;
            int col = inside && !direct ? 0xFFFFA020 : 0xFFFF3030;
            ctx.fill(x, y, x + 16, y + 16, (col & 0x00FFFFFF) | 0x40000000);
            ctx.fill(x - 1, y - 1, x + 17, y, col);
            ctx.fill(x - 1, y + 16, x + 17, y + 17, col);
            ctx.fill(x - 1, y, x, y + 16, col);
            ctx.fill(x + 16, y, x + 17, y + 16, col);
            if (inside) ctx.drawTextWithShadow(tr, "\u25A3", x + 10, y, 0xFFFFFFFF);
        }
    }

    // ---------- Отрисовка ----------

    static Vec3d cameraPos(Camera cam, MinecraftClient client) {
        if (!camPosResolved) {
            camPosResolved = true;
            for (Method m : Camera.class.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == Vec3d.class
                        && !java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    camPosMethod = m;
                    break;
                }
            }
        }
        try {
            if (camPosMethod != null) return (Vec3d) camPosMethod.invoke(cam);
        } catch (Throwable ignored) {
        }
        return client.player.getEyePos();
    }

    private static void render(DrawContext ctx) {
        Map<BlockPos, String> data = active();
        if (!ClickerConfig.containerSearch || data.isEmpty()) return;
        String[] tokens = tokens();
        if (tokens.length == 0) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cameraPos(cam, mc);
        Quaternionf q = cam.getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(q);
        Vector3f up = new Vector3f(0, 1, 0).rotate(q);
        Vector3f fwd = new Vector3f(0, 0, -1).rotate(q);

        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

        double radius = ClickerConfig.searchRadius;
        double r2 = radius * radius;
        Vec3d eye = mc.player.getEyePos();

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 250.0);
        int glow = ((int) (0x30 + 0x50 * pulse) << 24) | 0xFF2020;
        int core = 0xFFFF3030;

        java.util.List<BlockPos> stale = null;
        for (Map.Entry<BlockPos, String> e : data.entrySet()) {
            BlockPos p = e.getKey();
            double dx = p.getX() + 0.5 - eye.x, dy = p.getY() + 0.5 - eye.y, dz = p.getZ() + 0.5 - eye.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            if (!mc.world.isChunkLoaded(p)) continue; // чанк не загружен — не забываем
            if (!isContainer(mc.world.getBlockState(p))) {
                if (stale == null) stale = new java.util.ArrayList<>();
                stale.add(p);
                continue;
            }
            if (!matches(e.getValue(), tokens)) continue;
            // Рамка рисуется в мире (WorldOutlines), как контур рыхлого снега; здесь только чистим устаревшее
        }
        if (stale != null) {
            for (BlockPos p : stale) {
                if (CACHE.remove(p) != null) dirty = true;
            }
        }
    }

    private static final int[][] EDGES = {
            {0, 1}, {1, 3}, {3, 2}, {2, 0},
            {4, 5}, {5, 7}, {7, 6}, {6, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };

    static void drawBox(DrawContext ctx, BlockPos p, Vec3d cp, Vector3f right, Vector3f up, Vector3f fwd,
                                int sw, int sh, double aspect, double tanHalf, int glow, int core) {
        drawBox(ctx, new net.minecraft.util.math.Box(p), cp, right, up, fwd, sw, sh, aspect, tanHalf, glow, core);
    }

    /** Каркас произвольной рамки (например, хитбокса сущности). */
    static void drawBox(DrawContext ctx, net.minecraft.util.math.Box box, Vec3d cp, Vector3f right, Vector3f up, Vector3f fwd,
                                int sw, int sh, double aspect, double tanHalf, int glow, int core) {
        double[][] c = new double[8][3]; // x (вправо), y (вверх), z (вперёд) в системе камеры
        for (int i = 0; i < 8; i++) {
            double wx = ((i & 1) == 0 ? box.minX : box.maxX) - cp.x;
            double wy = (((i >> 1) & 1) == 0 ? box.minY : box.maxY) - cp.y;
            double wz = (((i >> 2) & 1) == 0 ? box.minZ : box.maxZ) - cp.z;
            c[i][0] = wx * right.x + wy * right.y + wz * right.z;
            c[i][1] = wx * up.x + wy * up.y + wz * up.z;
            c[i][2] = wx * fwd.x + wy * fwd.y + wz * fwd.z;
        }
        final double near = 0.05;
        double[][] seg = new double[EDGES.length][];
        int n = 0;
        for (int[] ed : EDGES) {
            double[] a = c[ed[0]].clone();
            double[] b = c[ed[1]].clone();
            if (a[2] < near && b[2] < near) continue;
            if (a[2] < near) {
                double t = (near - a[2]) / (b[2] - a[2]);
                a = new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, near};
            } else if (b[2] < near) {
                double t = (near - b[2]) / (a[2] - b[2]);
                b = new double[]{b[0] + (a[0] - b[0]) * t, b[1] + (a[1] - b[1]) * t, near};
            }
            double ax = (a[0] / (a[2] * tanHalf * aspect) * 0.5 + 0.5) * sw;
            double ay = (0.5 - a[1] / (a[2] * tanHalf) * 0.5) * sh;
            double bx = (b[0] / (b[2] * tanHalf * aspect) * 0.5 + 0.5) * sw;
            double by = (0.5 - b[1] / (b[2] * tanHalf) * 0.5) * sh;
            double[] clipped = clip(ax, ay, bx, by, sw, sh);
            if (clipped != null) seg[n++] = clipped;
        }
        for (int i = 0; i < n; i++) line(ctx, seg[i][0], seg[i][1], seg[i][2], seg[i][3], 6, glow);
        for (int i = 0; i < n; i++) line(ctx, seg[i][0], seg[i][1], seg[i][2], seg[i][3], 2, core);
    }

    /** Обрезка отрезка по экрану (Лян–Барски). */
    static double[] clip(double x1, double y1, double x2, double y2, double w, double h) {
        if (Double.isNaN(x1 + y1 + x2 + y2) || Double.isInfinite(x1 + y1 + x2 + y2)) return null;
        double t0 = 0, t1 = 1, dx = x2 - x1, dy = y2 - y1;
        double[] p = {-dx, dx, -dy, dy};
        double[] qv = {x1, w - x1, y1, h - y1};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0) {
                if (qv[i] < 0) return null;
            } else {
                double t = qv[i] / p[i];
                if (p[i] < 0) { if (t > t1) return null; if (t > t0) t0 = t; }
                else { if (t < t0) return null; if (t < t1) t1 = t; }
            }
        }
        return new double[]{x1 + t0 * dx, y1 + t0 * dy, x1 + t1 * dx, y1 + t1 * dy};
    }

    /** Линия толщиной th из прямоугольников-«пробегов» (мало вызовов fill). */
    static void line(DrawContext c, double x1, double y1, double x2, double y2, int th, int color) {
        boolean steep = Math.abs(y2 - y1) > Math.abs(x2 - x1);
        if (steep) {
            double t = x1; x1 = y1; y1 = t;
            t = x2; x2 = y2; y2 = t;
        }
        if (x1 > x2) {
            double t = x1; x1 = x2; x2 = t;
            t = y1; y1 = y2; y2 = t;
        }
        double dx = x2 - x1, dy = y2 - y1;
        int off = th / 2;
        int ya = (int) Math.round(y1), yb = (int) Math.round(y2);
        int step = yb >= ya ? 1 : -1;
        for (int yy = ya; ; yy += step) {
            double lo, hi;
            if (ya == yb || dy == 0) {
                lo = x1;
                hi = x2;
            } else {
                double xa = x1 + (yy - 0.5 - y1) * dx / dy;
                double xb = x1 + (yy + 0.5 - y1) * dx / dy;
                lo = Math.max(Math.min(xa, xb), x1);
                hi = Math.min(Math.max(xa, xb), x2);
            }
            int rx1 = (int) Math.floor(lo) - off;
            int rx2 = Math.max((int) Math.ceil(hi), (int) Math.floor(lo) + 1) + (th - off);
            if (steep) c.fill(yy - off, rx1, yy - off + th, rx2, color);
            else c.fill(rx1, yy - off, rx2, yy - off + th, color);
            if (yy == yb) break;
        }
    }
}
