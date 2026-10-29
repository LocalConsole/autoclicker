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

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(list, TYPE, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        for (Waypoint w : list) if (dim.equals(w.dim)) n++;
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
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "waypoints"),
                (context, tickCounter) -> render(context));
    }

    private static void render(DrawContext ctx) {
        if (!ClickerConfig.wpShow || list.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        String dim = currentDim(mc);
        int cx = ctx.getScaledWindowWidth() / 2;
        int half = Math.min(150, cx - 20);
        int y = 6;
        ctx.fill(cx - half, y + 9, cx + half, y + 10, 0x66FFFFFF);
        ctx.fill(cx - 1, y + 6, cx + 1, y + 13, 0x88FFFFFF);

        double px = mc.player.getX(), pz = mc.player.getZ();
        float yaw = mc.player.getYaw();
        for (Waypoint w : list) {
            if (!w.enabled || !dim.equals(w.dim)) continue;
            double dx = w.x + 0.5 - px, dz = w.z + 0.5 - pz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            float yawTo = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float diff = MathHelper.wrapDegrees(yawTo - yaw);
            int edge = 0;
            if (diff > 90f) { diff = 90f; edge = 1; }
            if (diff < -90f) { diff = -90f; edge = -1; }
            int sx = cx + (int) (diff / 90.0 * half);
            int col = COLORS[Math.max(0, Math.min(COLORS.length - 1, w.color))];
            ctx.fill(sx - 4, y + 5, sx + 4, y + 14, 0xFF000000);
            ctx.fill(sx - 3, y + 6, sx + 3, y + 13, col);
            if (edge != 0) {
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(edge > 0 ? ">" : "<"),
                        edge > 0 ? sx + 6 : sx - 12, y + 5, col);
            } else if (Math.abs(diff) <= 25f) {
                String label = w.name + " " + (int) dist + I18n.t(" м");
                ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(label), sx, y + 16, col);
            }
        }
    }
}
