package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Миникарта: угол экрана, размер, масштаб, форма (квадрат/круг), поворот вместе с игроком, пещерный режим,
 * биом, координаты, пересчёт Незер/Верхний мир, существа (выше/ниже вас), метки. Всё меняется в настройках
 * и сохраняется. Картинка — одна текстура, перерисовывается только когда что-то изменилось.
 */
public class Minimap {
    /** Блоков на пиксель. */
    public static final double[] ZOOMS = {0.25, 1 / 3.0, 0.5, 0.75, 1, 1.5, 2, 3, 4, 6, 8};
    public static final String[] ZOOM_NAMES = {"4:1 (очень крупно)", "3:1", "2:1", "4:3", "1:1", "1:1.5", "1:2", "1:3", "1:4", "1:6", "1:8 (очень далеко)"};
    public static final String[] CORNERS = {"Слева сверху", "Справа сверху", "Слева снизу", "Справа снизу"};
    public static final String[] SHAPES = {"Квадрат", "Круг"};
    public static final String[] CAVE = {"Авто", "Всегда", "Выключен"};

    private static final MapTexture TEX = new MapTexture("minimap");
    private static double lastX = 1e9, lastZ = 1e9;
    private static float lastYaw = 1e9f;
    private static int lastVersion = -1, lastSize = -1, lastZoom = -1, lastShape = -1;
    private static boolean lastCave = false, lastRotate = false;
    private static long lastBuild = 0;
    private static boolean caveNow = false;

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "minimap"), (ctx, tc) -> render(ctx));
    }

    public static double zoom() {
        return ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, ClickerConfig.mmZoom))];
    }

    public static void cycleZoom() {
        ClickerConfig.mmZoom = (ClickerConfig.mmZoom + 1) % ZOOMS.length;
        ClickerConfig.save();
    }

    /** Ближе (+1) или дальше (-1). */
    public static void zoomStep(int dir) {
        ClickerConfig.mmZoom = Math.max(0, Math.min(ZOOMS.length - 1, ClickerConfig.mmZoom - dir));
        ClickerConfig.save();
    }

    /** Пещерный режим сейчас? */
    private static boolean cave(MinecraftClient mc) {
        if (ClickerConfig.mmCave == 2) return false;
        if (ClickerConfig.mmCave == 1) return true;
        ClientWorld w = mc.world;
        if (w.getDimension().hasCeiling()) return true;
        BlockPos p = mc.player.getBlockPos();
        if (w.isSkyVisible(p.up())) return false;
        int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, p.getX(), p.getZ());
        return p.getY() < top - 6;
    }

    // ---------- Перерисовка картинки ----------

    private static void rebuild(MinecraftClient mc, int size) {
        ClientPlayerEntity p = mc.player;
        ClientWorld w = mc.world;
        // высокое разрешение: картинка в настоящих пикселях экрана, а не в «пикселях интерфейса»
        double gui = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getScaledWidth());
        int res = ClickerConfig.mmDetail ? (int) Math.min(384, Math.max(size, Math.round(size * gui))) : size;
        if (caveNow) res = Math.min(res, size); // пещерный режим дорогой — без лишних пикселей
        TEX.ensure(res, res);
        double k = zoom();
        double per = size / (double) res;               // пикселей интерфейса на пиксель картинки
        double texelsPerBlock = 1.0 / (k * per);
        double px = p.getX(), pz = p.getZ();
        boolean rot = ClickerConfig.mmRotate;
        float yaw = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // вперёд
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // вправо
        boolean circle = ClickerConfig.mmShape == 1;
        double half = size / 2.0, r2 = half * half;
        int fromY = (int) Math.floor(p.getY()) + 1;
        BlockPos.Mutable m = new BlockPos.Mutable();
        java.util.HashMap<Long, Integer> caveCache = caveNow ? new java.util.HashMap<>() : null;
        for (int v = 0; v < res; v++) {
            for (int u = 0; u < res; u++) {
                double du = (u + 0.5) * per - half, dv = (v + 0.5) * per - half;
                int c;
                if (circle && du * du + dv * dv > r2) {
                    c = 0;
                } else {
                    double wx, wz;
                    if (rot) {
                        wx = px + (du * rx - dv * fx) * k;
                        wz = pz + (du * rz - dv * fz) * k;
                    } else {
                        wx = px + du * k;
                        wz = pz + dv * k;
                    }
                    int bx = MathHelper.floor(wx), bz = MathHelper.floor(wz);
                    if (caveNow) {
                        long key = ((long) bx << 32) | (bz & 0xFFFFFFFFL);
                        Integer cc = caveCache.get(key);
                        if (cc == null) {
                            cc = w.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4) ? MapData.caveColor(w, bx, fromY, bz, m) : 0;
                            caveCache.put(key, cc);
                        }
                        c = cc == 0 ? 0xFF101010 : 0xFF000000 | (cc & 0xFFFFFF);
                    } else {
                        c = MapPainter.texel(wx, wz, texelsPerBlock, MapData.colorAt(bx, bz));
                    }
                }
                TEX.set(u, v, c);
            }
        }
        TEX.upload();
    }

    // ---------- Отрисовка ----------

    /** Где сейчас миникарта (для других панелей). */
    public static int boxX, boxY, boxW, boxH;

    private static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boxW = 0;
        if (!ClickerConfig.mmShow || mc.player == null || mc.world == null || mc.options.hudHidden) return;
        // с открытым F3 карта мешает тексту отладки — прячем
        if (ClickerConfig.mmHideF3 && mc.getDebugHud().shouldShowDebugHud()) return;
        ClientPlayerEntity p = mc.player;
        int size = Math.max(48, Math.min(256, ClickerConfig.mmSize));
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        size = Math.min(size, Math.min(sw, sh) / 2);

        // информация под картой
        List<String> info = new ArrayList<>();
        BlockPos bp = p.getBlockPos();
        if (ClickerConfig.mmCoords) info.add(bp.getX() + " " + bp.getY() + " " + bp.getZ());
        if (ClickerConfig.mmConvert) {
            String dim = Waypoints.currentDim(mc);
            if (dim.equals(Waypoints.DIMS[0])) info.add(I18n.t("Незер: ") + Math.floorDiv(bp.getX(), 8) + " " + Math.floorDiv(bp.getZ(), 8));
            else if (dim.equals(Waypoints.DIMS[1])) info.add(I18n.t("Верхний мир: ") + bp.getX() * 8 + " " + bp.getZ() * 8);
        }
        if (ClickerConfig.mmBiome) {
            try {
                var key = mc.world.getBiome(bp).getKey();
                if (key.isPresent()) info.add(Text.translatable(key.get().getValue().toTranslationKey("biome")).getString());
            } catch (Throwable ignored) {
            }
        }
        int infoH = info.size() * 10;

        int corner = Math.max(0, Math.min(3, ClickerConfig.mmCorner));
        boolean right = corner == 1 || corner == 3, bottom = corner >= 2;
        int x = right ? sw - size - ClickerConfig.mmOffX : ClickerConfig.mmOffX;
        int y = bottom ? sh - size - infoH - ClickerConfig.mmOffY : ClickerConfig.mmOffY;
        x = Math.max(0, Math.min(sw - size, x));
        y = Math.max(0, Math.min(sh - size - infoH, y));
        boxX = x;
        boxY = y;
        boxW = size;
        boxH = size + infoH;

        // нужна ли перерисовка
        caveNow = cave(mc);
        double k = zoom();
        long now = System.currentTimeMillis();
        boolean need = size != lastSize || ClickerConfig.mmZoom != lastZoom || ClickerConfig.mmShape != lastShape
                || caveNow != lastCave || ClickerConfig.mmRotate != lastRotate
                || Math.abs(p.getX() - lastX) >= k * 0.5 || Math.abs(p.getZ() - lastZ) >= k * 0.5
                || (ClickerConfig.mmRotate && Math.abs(MathHelper.wrapDegrees(p.getYaw() - lastYaw)) > 1.5f)
                || (MapData.version != lastVersion && now - lastBuild > 250)
                || (caveNow && now - lastBuild > 700);
        // не чаще ~30 раз в секунду, а в пещерном режиме (он дороже) — ~6 раз
        boolean settingsChanged = size != lastSize || ClickerConfig.mmZoom != lastZoom || ClickerConfig.mmShape != lastShape
                || caveNow != lastCave || ClickerConfig.mmRotate != lastRotate;
        if (need && !settingsChanged && now - lastBuild < (caveNow ? 160 : (ClickerConfig.mmDetail ? 50 : 33))) need = false;
        if (need) {
            rebuild(mc, size);
            lastSize = size;
            lastZoom = ClickerConfig.mmZoom;
            lastShape = ClickerConfig.mmShape;
            lastCave = caveNow;
            lastRotate = ClickerConfig.mmRotate;
            lastX = p.getX();
            lastZ = p.getZ();
            lastYaw = p.getYaw();
            lastVersion = MapData.version;
            lastBuild = now;
        }

        boolean circle = ClickerConfig.mmShape == 1;
        int border = caveNow ? 0xFF7A5BA8 : 0xFF3A4552;
        if (circle) {
            ring(ctx, x + size / 2 + 1, y + size / 2 + 2, size / 2 + 2, 0x66000000); // тень
            ring(ctx, x + size / 2, y + size / 2, size / 2 + 2, 0xFF0E1216);
            ring(ctx, x + size / 2, y + size / 2, size / 2 + 1, border);
        } else {
            ctx.fill(x, y + 2, x + size + 3, y + size + 4, 0x66000000); // тень
            ctx.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF0E1216);
            ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
        }
        TEX.draw(ctx, x, y, size, size);

        int cx = x + size / 2, cy = y + size / 2;
        double half = size / 2.0;
        float yawR = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
        double fx = -Math.sin(yawR), fz = Math.cos(yawR), rx = -Math.cos(yawR), rz = -Math.sin(yawR);
        boolean rot = ClickerConfig.mmRotate;

        // метки
        if (ClickerConfig.mmWaypoints) {
            String dim = Waypoints.currentDim(mc);
            for (Waypoint wp : Waypoints.list) {
                if (!wp.enabled || !dim.equals(wp.dim) || !Waypoints.visible(mc, wp)) continue;
                double[] s = toScreen(wp.x + 0.5 - p.getX(), wp.z + 0.5 - p.getZ(), k, rot, fx, fz, rx, rz);
                double d = Math.sqrt(s[0] * s[0] + s[1] * s[1]);
                double lim = half - 4;
                if (circle ? d > lim : (Math.abs(s[0]) > lim || Math.abs(s[1]) > lim)) {
                    double f = circle ? lim / d : lim / Math.max(Math.abs(s[0]), Math.abs(s[1]));
                    s[0] *= f;
                    s[1] *= f;
                }
                int col = Waypoints.COLORS[Math.max(0, Math.min(Waypoints.COLORS.length - 1, wp.color))];
                int wx = cx + (int) s[0], wy = cy + (int) s[1];
                ctx.fill(wx - 3, wy - 1, wx + 4, wy + 2, 0xFF000000);
                ctx.fill(wx - 1, wy - 3, wx + 2, wy + 4, 0xFF000000);
                ctx.fill(wx - 2, wy, wx + 3, wy + 1, col);
                ctx.fill(wx, wy - 2, wx + 1, wy + 3, col);
            }
        }

        // существа
        if (ClickerConfig.mmPlayers || ClickerConfig.mmMobs || ClickerConfig.mmAnimals || ClickerConfig.mmItems) {
            double range = half * k;
            for (Entity e : mc.world.getEntities()) {
                if (e == p) continue;
                int col;
                if (e instanceof PlayerEntity) { if (!ClickerConfig.mmPlayers) continue; col = 0xFFFFFFFF; }
                else if (e instanceof Monster) { if (!ClickerConfig.mmMobs) continue; col = 0xFFFF4040; }
                else if (e instanceof PassiveEntity) { if (!ClickerConfig.mmAnimals) continue; col = 0xFF5BD38A; }
                else if (e instanceof ItemEntity) { if (!ClickerConfig.mmItems) continue; col = 0xFFF2C94C; }
                else continue;
                double ox = e.getX() - p.getX(), oz = e.getZ() - p.getZ();
                if (Math.abs(ox) > range || Math.abs(oz) > range) continue;
                double[] s = toScreen(ox, oz, k, rot, fx, fz, rx, rz);
                if (circle ? s[0] * s[0] + s[1] * s[1] > (half - 2) * (half - 2)
                        : Math.abs(s[0]) > half - 2 || Math.abs(s[1]) > half - 2) continue;
                int ex = cx + (int) s[0], ey = cy + (int) s[1];
                double dy = e.getY() - p.getY();
                int isz = Math.max(4, Math.min(16, ClickerConfig.mmIconSize));
                // значок — лицо игрока, яйцо моба или сам предмет; рядом ▲/▼, если выше/ниже
                // игроки и враги — полный размер, животные и предметы — чуть меньше: главное видно сразу
                if (!(e instanceof PlayerEntity) && !(e instanceof Monster)) isz = Math.max(4, isz * 3 / 4);
                EntityIcons.draw(ctx, e, ex, ey, isz, col, dy);
            }
        }

        // стороны света: N красным, остальные серым
        String[] sides = {"N", "E", "S", "W"};
        double[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            double[] n = rot ? toScreen(dirs[i][0], dirs[i][1], 1, true, fx, fz, rx, rz) : new double[]{dirs[i][0], dirs[i][1]};
            double nl = Math.sqrt(n[0] * n[0] + n[1] * n[1]);
            int nx = cx + (int) (n[0] / nl * (half - 6)), ny = cy + (int) (n[1] / nl * (half - 6));
            ctx.fill(nx - 4, ny - 5, nx + 4, ny + 5, 0x99000000);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(sides[i]), nx, ny - 4, i == 0 ? 0xFFFF5A5A : 0xFFB8C0C8);
        }

        // стрелка игрока
        double ang = rot ? 0 : (p.getYaw() * MathHelper.RADIANS_PER_DEGREE);
        double ax = rot ? 0 : -Math.sin(ang), ay = rot ? -1 : Math.cos(ang);
        if (!rot) { ax = -Math.sin(ang); ay = Math.cos(ang); }
        double tipX = cx + ax * 5, tipY = cy + ay * 5;
        double bx1 = cx - ax * 3 + ay * 3, by1 = cy - ay * 3 - ax * 3;
        double bx2 = cx - ax * 3 - ay * 3, by2 = cy - ay * 3 + ax * 3;
        for (int pass = 0; pass < 2; pass++) {
            int th = pass == 0 ? 3 : 1;
            int col = pass == 0 ? 0xFF000000 : 0xFFFFFFFF;
            ContainerSearch.line(ctx, tipX, tipY, bx1, by1, th, col);
            ContainerSearch.line(ctx, tipX, tipY, bx2, by2, th, col);
            ContainerSearch.line(ctx, bx1, by1, bx2, by2, th, col);
        }

        // подписи
        int ty = y + size + 2;
        for (String s : info) {
            String t = mc.textRenderer.trimToWidth(s, Math.max(size, 110));
            int tw = mc.textRenderer.getWidth(t);
            int tx = right ? x + size - tw : x;
            tx = Math.max(1, Math.min(sw - tw - 1, tx));
            ctx.fill(tx - 2, ty - 1, tx + tw + 2, ty + 9, 0x88000000);
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(t), tx, ty, 0xFFE0E0E0);
            ty += 10;
        }
    }

    /** Смещение в мире → смещение на карте (пиксели). */
    private static double[] toScreen(double ox, double oz, double k, boolean rot, double fx, double fz, double rx, double rz) {
        if (!rot) return new double[]{ox / k, oz / k};
        double du = ox * rx + oz * rz;
        double dv = -(ox * fx + oz * fz);
        return new double[]{du / k, dv / k};
    }

    /** Кольцо для круглой карты. */
    private static void ring(DrawContext ctx, int cx, int cy, int r, int col) {
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.sqrt(Math.max(0, r * r - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
        }
    }
}
