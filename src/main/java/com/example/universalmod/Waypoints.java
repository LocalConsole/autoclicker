package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Метки с координатами. Показываются на экране полоской-компасом вверху: работают и в незагруженных чанках,
 * и для мест, где вы ещё не были. Для каждой метки можно узнать, загружен ли её чанк.
 */
public class Waypoints {
    public static final String[] COLOR_NAMES = {"Красный", "Оранжевый", "Жёлтый", "Зелёный", "Голубой", "Синий", "Фиолетовый", "Белый"};
    public static final int[] COLORS = {0xFFFF4040, 0xFFFF9A2E, 0xFFFFE74A, 0xFF4CFF5C, 0xFF46E6FF, 0xFF4A6CFF, 0xFFB45CFF, 0xFFFFFFFF};
    public static final String[] DIMS = {"minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"};
    public static final String[] DIM_NAMES = {"Верхний мир", "Незер", "Энд"};

    public static List<Waypoint> list = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod_waypoints.json");
    private static final Type TYPE = new TypeToken<List<Waypoint>>() {}.getType();

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            List<Waypoint> l = GSON.fromJson(r, TYPE);
            if (l != null) {
                for (Waypoint w : l) {
                    if (w.name == null) w.name = "";
                    if (w.dim == null) w.dim = DIMS[0];
                    w.color = Math.max(0, Math.min(COLORS.length - 1, w.color));
                }
                list = l;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Надёжная запись: сначала во временный файл, потом подмена. Так файл не обнулится при вылете. */
    public static void save() {
        try {
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(list, TYPE, w);
            }
            try {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Ключ мира: адрес сервера или имя одиночного мира. У каждого — свои метки. */
    public static String worldKey(MinecraftClient mc) {
        if (mc == null) return "?";
        try {
            var se = mc.getCurrentServerEntry();
            if (se != null && se.address != null) return "mp:" + se.address.toLowerCase(java.util.Locale.ROOT);
            if (mc.getServer() != null) return "sp:" + mc.getServer().getSaveProperties().getLevelName();
        } catch (Throwable ignored) {
        }
        return "?";
    }

    /** Метка этого мира (сервера) и этого измерения? */
    public static boolean visible(MinecraftClient mc, Waypoint w) {
        return w.world != null && w.world.equals(worldKey(mc));
    }

    /** Метки текущего мира (все измерения). */
    public static List<Waypoint> current(MinecraftClient mc) {
        String k = worldKey(mc);
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : list) if (k.equals(w.world)) out.add(w);
        return out;
    }

    private static String lastWorld = null;

    /** При заходе в мир: старые метки без мира привязываются к нему (чтобы не пропали). */
    private static void tick(MinecraftClient mc) {
        if (mc.player == null) {
            if (lastWorld != null) save();
            lastWorld = null;
            return;
        }
        String k = worldKey(mc);
        if (k.equals(lastWorld)) return;
        lastWorld = k;
        boolean changed = false;
        for (Waypoint w : list) {
            if (w.world == null) {
                w.world = k;
                changed = true;
            }
        }
        if (changed) save();
    }

    public static String currentDim(MinecraftClient mc) {
        if (mc == null || mc.world == null) return DIMS[0];
        return mc.world.getRegistryKey().getValue().toString();
    }

    public static int dimIndex(String dim) {
        for (int i = 0; i < DIMS.length; i++) if (DIMS[i].equals(dim)) return i;
        return 0;
    }

    /** Поставить метку в точке, где стоит игрок (по клавише). */
    public static void addHere(MinecraftClient mc) {
        if (mc.player == null) return;
        String dim = currentDim(mc);
        BlockPos p = mc.player.getBlockPos();
        int n = 1;
        for (Waypoint w : current(mc)) if (dim.equals(w.dim)) n++;
        String name = I18n.t("Метка ") + n;
        int color = Math.max(0, Math.min(COLORS.length - 1, ClickerConfig.wpNewColor));
        list.add(new Waypoint(name, p.getX(), p.getY(), p.getZ(), color, dim));
        save();
        mc.player.sendMessage(Text.literal(name + ": " + p.getX() + ", " + p.getY() + ", " + p.getZ()), true);
    }

    /**
     * Что будет с чанком метки: 0 — не загружен рядом, 1 — загружен сейчас (без игрока выгрузится),
     * 2 — попадает в чанки спавна, которые остаются загруженными без игрока.
     * Принудительную загрузку (/forceload) и чанклоадеры клиент определить не может.
     */
    public static int statusOf(MinecraftClient mc, Waypoint w) {
        int cx = w.x >> 4, cz = w.z >> 4;
        if (ClickerConfig.spawnKnown && DIMS[0].equals(w.dim)) {
            int sx = ClickerConfig.spawnX >> 4, sz = ClickerConfig.spawnZ >> 4;
            if (Math.max(Math.abs(cx - sx), Math.abs(cz - sz)) <= ClickerConfig.spawnRadius) return 2;
        }
        if (mc.world != null && w.dim.equals(currentDim(mc)) && mc.world.getChunkManager().getWorldChunk(cx, cz) != null) {
            return 1;
        }
        return 0;
    }

    // ---------- Показ на экране ----------

    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(Waypoints::tick);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(c -> save());
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "waypoints"),
                (context, tickCounter) -> render(context));
    }

    /** Высота полоски-компаса сверху (чтобы другие панели не налезали на неё). */
    public static int hudHeight() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!ClickerConfig.wpShow || mc.player == null) return 0;
        String dim = currentDim(mc);
        for (Waypoint w : list) if (w.enabled && dim.equals(w.dim) && visible(mc, w)) return 30;
        return 0;
    }

    /** Стрелка и разница по высоте: ▲ +12 — метка выше, ▼ -5 — ниже. */
    private static String height(int dy) {
        if (dy > 2) return "\u25B2+" + dy;
        if (dy < -2) return "\u25BC" + dy;
        return "";
    }

    private static void render(DrawContext ctx) {
        if (!ClickerConfig.wpShow || list.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        String dim = currentDim(mc);
        List<Waypoint> shown = new ArrayList<>();
        for (Waypoint w : list) if (w.enabled && dim.equals(w.dim) && visible(mc, w)) shown.add(w);
        if (shown.isEmpty()) return;
        var tr = mc.textRenderer;
        int cx = ctx.getScaledWindowWidth() / 2;
        int half = Math.min(150, cx - 20);
        int y = 4;
        // полоса компаса
        ctx.fill(cx - half - 2, y + 4, cx + half + 2, y + 15, 0x66000000);
        ctx.fill(cx - half, y + 9, cx + half, y + 10, 0x55FFFFFF);
        ctx.fill(cx, y + 5, cx + 1, y + 14, 0xAAFFFFFF);

        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        float yaw = mc.player.getYaw();
        // сначала ближние к центру — их подпись важнее, остальные подписи не налезают
        shown.sort((a, b) -> Float.compare(Math.abs(angle(a, px, pz, yaw)), Math.abs(angle(b, px, pz, yaw))));
        List<int[]> used = new ArrayList<>();
        for (Waypoint w : shown) {
            double dx = w.x + 0.5 - px, dz = w.z + 0.5 - pz;
            double dist = Math.sqrt(dx * dx + dz * dz + (w.y - py) * (w.y - py));
            float diff = angle(w, px, pz, yaw);
            int edge = 0;
            if (diff > 90f) { diff = 90f; edge = 1; }
            if (diff < -90f) { diff = -90f; edge = -1; }
            int sx = cx + (int) (diff / 90.0 * half);
            int col = COLORS[Math.max(0, Math.min(COLORS.length - 1, w.color))];
            // ромбик
            ctx.fill(sx - 1, y + 4, sx + 2, y + 15, 0xFF000000);
            ctx.fill(sx - 3, y + 6, sx + 4, y + 13, 0xFF000000);
            ctx.fill(sx - 2, y + 7, sx + 3, y + 12, col);
            ctx.fill(sx, y + 5, sx + 1, y + 14, col);
            if (edge != 0) {
                String a = edge > 0 ? "\u25B6" : "\u25C0";
                ctx.drawTextWithShadow(tr, Text.literal(a), edge > 0 ? sx + 5 : sx - 11, y + 5, col);
                continue;
            }
            String label = w.name + "  " + (int) dist + I18n.t(" м");
            String h = height((int) Math.round(w.y - py));
            if (!h.isEmpty()) label += "  " + h;
            int lw = tr.getWidth(label);
            int lx = sx - lw / 2;
            lx = Math.max(2, Math.min(ctx.getScaledWindowWidth() - lw - 2, lx));
            boolean free = true;
            for (int[] u : used) if (lx < u[1] + 4 && lx + lw + 4 > u[0]) free = false;
            if (!free) continue;
            used.add(new int[]{lx, lx + lw});
            ctx.fill(lx - 2, y + 16, lx + lw + 2, y + 27, 0x99000000);
            ctx.drawTextWithShadow(tr, Text.literal(label), lx, y + 18, col);
        }
        renderWorldMarkers(ctx, mc, shown);
    }

    private static float angle(Waypoint w, double px, double pz, float yaw) {
        double dx = w.x + 0.5 - px, dz = w.z + 0.5 - pz;
        float yawTo = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return MathHelper.wrapDegrees(yawTo - yaw);
    }

    /** Значок метки прямо в мире (поверх всего), с расстоянием и стрелкой вверх/вниз. */
    private static void renderWorldMarkers(DrawContext ctx, MinecraftClient mc, List<Waypoint> shown) {
        if (!ClickerConfig.wpWorld) return;
        var cam = mc.gameRenderer.getCamera();
        var cp = ContainerSearch.cameraPos(cam, mc);
        org.joml.Quaternionf q = cam.getRotation();
        org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(q);
        org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0).rotate(q);
        org.joml.Vector3f fwd = new org.joml.Vector3f(0, 0, -1).rotate(q);
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        var tr = mc.textRenderer;
        List<int[]> used = new ArrayList<>();
        for (Waypoint w : shown) {
            double wx = w.x + 0.5 - cp.x, wy = w.y + 0.5 - cp.y, wz = w.z + 0.5 - cp.z;
            double cxv = wx * right.x + wy * right.y + wz * right.z;
            double cyv = wx * up.x + wy * up.y + wz * up.z;
            double czv = wx * fwd.x + wy * fwd.y + wz * fwd.z;
            if (czv < 0.2) continue;
            int sx = (int) ((cxv / (czv * tanHalf * aspect) * 0.5 + 0.5) * sw);
            int sy = (int) ((0.5 - cyv / (czv * tanHalf) * 0.5) * sh);
            if (sx < 0 || sx > sw || sy < 40 || sy > sh) continue;
            int col = COLORS[Math.max(0, Math.min(COLORS.length - 1, w.color))];
            double dist = Math.sqrt(wx * wx + wy * wy + wz * wz);
            // ромб
            for (int i = 0; i <= 5; i++) {
                ctx.fill(sx - i, sy - 5 + i, sx + i + 1, sy - 4 + i, 0xFF000000);
                ctx.fill(sx - i, sy + 5 - i, sx + i + 1, sy + 6 - i, 0xFF000000);
            }
            for (int i = 0; i <= 3; i++) {
                ctx.fill(sx - i, sy - 3 + i, sx + i + 1, sy - 2 + i, col);
                ctx.fill(sx - i, sy + 3 - i, sx + i + 1, sy + 4 - i, col);
            }
            // подпись — только если не налезает на другую и метка ближе к прицелу или рядом
            boolean nearCross = Math.abs(sx - sw / 2) < 80 && Math.abs(sy - sh / 2) < 80;
            if (!nearCross && dist > 64) continue;
            String l1 = w.name;
            String l2 = (int) dist + I18n.t(" м");
            String h = height((int) Math.round(w.y - mc.player.getY()));
            if (!h.isEmpty()) l2 += "  " + h;
            int lw = Math.max(tr.getWidth(l1), tr.getWidth(l2));
            int lx = sx - lw / 2, ly = sy + 9;
            boolean free = true;
            for (int[] u : used) if (lx < u[2] && lx + lw > u[0] && ly < u[3] && ly + 22 > u[1]) free = false;
            if (!free) continue;
            used.add(new int[]{lx - 3, ly - 2, lx + lw + 3, ly + 22});
            ctx.fill(lx - 3, ly - 2, lx + lw + 3, ly + 20, 0x99000000);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(l1), sx, ly, col);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(l2), sx, ly + 10, 0xFFDDDDDD);
        }
    }
}
