package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Карта мира: вид сверху всего, где вы уже были (запоминается и после перезахода).
 * Перетаскивание — ЛКМ, масштаб — колёсико, ПКМ — поставить метку. Метки показываются полностью:
 * ромбик, название, при наведении — координаты и расстояние.
 */
public class WorldMapScreen extends Screen {
    private static final MapTexture TEX = new MapTexture("worldmap");
    private static double camX = Double.NaN, camZ;
    private static double scale = 2.0; // блоков на один пиксель экрана
    private final Screen parent;
    private double lastMx = -1, lastMy = -1;
    private boolean dragging = false;
    private double builtX = 1e9, builtZ = 1e9, builtScale = -1;
    private int builtVersion = -1, builtW = -1, builtH = -1;
    private long lastBuild = 0;
    private static final int TOP = 24, BOTTOM = 22;

    public WorldMapScreen(Screen parent) {
        super(Text.literal(I18n.t("Карта мира")));
        this.parent = parent;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null && (Double.isNaN(camX) || ClickerConfig.mapCenterOnOpen)) {
            camX = mc.player.getX();
            camZ = mc.player.getZ();
        }
    }

    @Override
    protected void init() {
        int bw = 96;
        int y = this.height - BOTTOM + 1;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("К себе")), b -> center())
                .dimensions(4, y, bw, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Вернуть карту к игроку")))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Метки...")),
                        b -> this.client.setScreen(new WaypointScreen(this)))
                .dimensions(4 + bw + 4, y, bw, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> close())
                .dimensions(this.width - bw - 4, y, bw, 20).build());
    }

    private void center() {
        if (this.client != null && this.client.player != null) {
            camX = this.client.player.getX();
            camZ = this.client.player.getZ();
        }
    }

    private int mapH() {
        return this.height - TOP - BOTTOM;
    }

    /** Координаты блока под точкой экрана. */
    private double worldX(double sx) {
        return camX + (sx - this.width / 2.0) * scale;
    }

    private double worldZ(double sy) {
        return camZ + (sy - (TOP + mapH() / 2.0)) * scale;
    }

    private double screenX(double wx) {
        return this.width / 2.0 + (wx - camX) / scale;
    }

    private double screenY(double wz) {
        return TOP + mapH() / 2.0 + (wz - camZ) / scale;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY < TOP || mouseY > TOP + mapH()) return false;
        // масштаб к точке под курсором
        double wx = worldX(mouseX), wz = worldZ(mouseY);
        double ns = verticalAmount > 0 ? scale / 1.5 : scale * 1.5;
        scale = MathHelper.clamp(ns, 0.125, 64.0);
        camX = wx - (mouseX - this.width / 2.0) * scale;
        camZ = wz - (mouseY - (TOP + mapH() / 2.0)) * scale;
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        if (click.y() < TOP || click.y() > TOP + mapH()) return false;
        if (click.button() == 1 && this.client != null && this.client.player != null) {
            // ПКМ — метка в этой точке
            int x = MathHelper.floor(worldX(click.x())), z = MathHelper.floor(worldZ(click.y()));
            int y = this.client.world == null ? 64
                    : this.client.world.getTopY(net.minecraft.world.Heightmap.Type.WORLD_SURFACE, x, z);
            if (y <= (this.client.world == null ? 0 : this.client.world.getBottomY())) y = (int) this.client.player.getY();
            String dim = Waypoints.currentDim(this.client);
            int n = 1;
            for (Waypoint w : Waypoints.current(this.client)) if (dim.equals(w.dim)) n++;
            Waypoints.list.add(new Waypoint(I18n.t("Метка ") + n, x, y, z,
                    Math.max(0, Math.min(Waypoints.COLORS.length - 1, ClickerConfig.wpNewColor)), dim));
            Waypoints.save();
            return true;
        }
        return false;
    }

    // ---------- Картинка ----------

    private void rebuild(int w, int h) {
        TEX.ensure(w, h);
        double half = w / 2.0, halfH = h / 2.0;
        for (int v = 0; v < h; v++) {
            double wz = camZ + (v + 0.5 - halfH) * scale;
            int bz = MathHelper.floor(wz);
            for (int u = 0; u < w; u++) {
                int bx = MathHelper.floor(camX + (u + 0.5 - half) * scale);
                int c = MapData.colorAt(bx, bz);
                if (c == MapData.UNKNOWN) {
                    // неизвестное — тёмная клетка с лёгкой сеткой по чанкам
                    c = ((bx & 15) == 0 || (bz & 15) == 0) && scale <= 2 ? 0xFF1C2026 : 0xFF14171B;
                }
                TEX.set(u, v, c);
            }
        }
        TEX.upload();
        builtX = camX;
        builtZ = camZ;
        builtScale = scale;
        builtVersion = MapData.version;
        builtW = w;
        builtH = h;
        lastBuild = System.currentTimeMillis();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // перетаскивание ЛКМ
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && (dragging || (mouseY >= TOP && mouseY <= TOP + mapH()))) {
            if (dragging) {
                camX -= (mouseX - lastMx) * scale;
                camZ -= (mouseY - lastMy) * scale;
            }
            dragging = true;
        } else {
            dragging = false;
        }
        lastMx = mouseX;
        lastMy = mouseY;

        int w = this.width, h = mapH();
        boolean moved = Math.abs(camX - builtX) >= scale * 0.5 || Math.abs(camZ - builtZ) >= scale * 0.5 || scale != builtScale;
        boolean newData = MapData.version != builtVersion && System.currentTimeMillis() - lastBuild > 1000;
        long now = System.currentTimeMillis();
        // при перетаскивании пересобираем не чаще 25 раз в секунду, а между — просто сдвигаем готовую картинку
        boolean throttle = dragging && scale == builtScale && now - lastBuild < 40;
        if ((moved && !throttle) || newData || w != builtW || h != builtH) rebuild(w, h);

        ctx.fill(0, 0, this.width, this.height, 0xFF0E1114);
        int offX = (int) Math.round((builtX - camX) / scale), offY = (int) Math.round((builtZ - camZ) / scale);
        ctx.enableScissor(0, TOP, w, TOP + h);
        TEX.draw(ctx, offX, TOP + offY, w, h);
        ctx.disableScissor();

        // метки
        ClientPlayerEntity p = mc.player;
        String dim = Waypoints.currentDim(mc);
        Waypoint hover = null;
        List<int[]> used = new ArrayList<>();
        for (Waypoint wp : Waypoints.list) {
            if (!wp.enabled || !dim.equals(wp.dim) || !Waypoints.visible(mc, wp)) continue;
            int sx = (int) screenX(wp.x + 0.5), sy = (int) screenY(wp.z + 0.5);
            if (sx < -20 || sx > w + 20 || sy < TOP - 20 || sy > TOP + h + 20) continue;
            int col = Waypoints.COLORS[Math.max(0, Math.min(Waypoints.COLORS.length - 1, wp.color))];
            for (int i = 0; i <= 5; i++) {
                ctx.fill(sx - i, sy - 5 + i, sx + i + 1, sy - 4 + i, 0xFF000000);
                ctx.fill(sx - i, sy + 5 - i, sx + i + 1, sy + 6 - i, 0xFF000000);
            }
            for (int i = 0; i <= 3; i++) {
                ctx.fill(sx - i, sy - 3 + i, sx + i + 1, sy - 2 + i, col);
                ctx.fill(sx - i, sy + 3 - i, sx + i + 1, sy + 4 - i, col);
            }
            int lw = this.textRenderer.getWidth(wp.name);
            int lx = sx - lw / 2, ly = sy + 8;
            boolean free = true;
            for (int[] u : used) if (lx < u[2] && lx + lw > u[0] && ly < u[3] && ly + 10 > u[1]) free = false;
            if (free) {
                used.add(new int[]{lx - 2, ly - 1, lx + lw + 2, ly + 10});
                ctx.fill(lx - 2, ly - 1, lx + lw + 2, ly + 9, 0xAA000000);
                ctx.drawTextWithShadow(this.textRenderer, Text.literal(wp.name), lx, ly, col);
            }
            if (Math.abs(mouseX - sx) <= 6 && Math.abs(mouseY - sy) <= 6) hover = wp;
        }

        // игрок — стрелка
        if (p != null) {
            double px = screenX(p.getX()), py = screenY(p.getZ());
            double ang = p.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            double ax = -Math.sin(ang), ay = Math.cos(ang);
            double tipX = px + ax * 7, tipY = py + ay * 7;
            double bx1 = px - ax * 4 + ay * 4, by1 = py - ay * 4 - ax * 4;
            double bx2 = px - ax * 4 - ay * 4, by2 = py - ay * 4 + ax * 4;
            for (int pass = 0; pass < 2; pass++) {
                int th = pass == 0 ? 3 : 1;
                int col = pass == 0 ? 0xFF000000 : 0xFFFFFFFF;
                ContainerSearch.line(ctx, tipX, tipY, bx1, by1, th, col);
                ContainerSearch.line(ctx, tipX, tipY, bx2, by2, th, col);
                ContainerSearch.line(ctx, bx1, by1, bx2, by2, th, col);
            }
        }

        // шапка
        ctx.fill(0, 0, this.width, TOP, 0xF0161B21);
        ctx.fill(0, TOP - 1, this.width, TOP, 0xFF56A8F5);
        ctx.drawTextWithShadow(this.textRenderer, this.title, 6, 8, 0xFFFFFFFF);
        String dimName = I18n.t(Waypoints.DIM_NAMES[Waypoints.dimIndex(dim)]);
        String sc = scale >= 1 ? "1:" + fmt(scale) : fmt(1 / scale) + ":1";
        String head = dimName + "   " + I18n.t("масштаб ") + sc + "   " + I18n.t("изучено чанков: ") + MapData.knownChunks();
        int hw = this.textRenderer.getWidth(head);
        int titleEnd = 6 + this.textRenderer.getWidth(this.title) + 12;
        if (this.width - hw - 6 > titleEnd) ctx.drawTextWithShadow(this.textRenderer, Text.literal(head), this.width - hw - 6, 8, 0xFF9AA4AE);

        // низ: координаты под курсором и подсказка
        ctx.fill(0, this.height - BOTTOM, this.width, this.height, 0xF0161B21);
        if (mouseY >= TOP && mouseY <= TOP + h) {
            int bx = MathHelper.floor(worldX(mouseX)), bz = MathHelper.floor(worldZ(mouseY));
            String c = "X " + bx + "  Z " + bz;
            if (dim.equals(Waypoints.DIMS[0])) c += "   " + I18n.t("Незер: ") + Math.floorDiv(bx, 8) + " " + Math.floorDiv(bz, 8);
            else if (dim.equals(Waypoints.DIMS[1])) c += "   " + I18n.t("Верхний мир: ") + bx * 8 + " " + bz * 8;
            int cw = this.textRenderer.getWidth(c);
            int left = 4 + 96 * 2 + 12, rightEdge = this.width - 96 - 12;
            if (rightEdge - left > cw) ctx.drawTextWithShadow(this.textRenderer, Text.literal(c), left + (rightEdge - left - cw) / 2, this.height - 15, 0xFFDDDDDD);
        }
        String hint = I18n.t("ЛКМ — двигать, колёсико — масштаб, ПКМ — метка");
        int hintW = this.textRenderer.getWidth(hint);
        if (hintW < this.width - 12 && TOP + h - 14 > TOP) {
            ctx.fill(this.width / 2 - hintW / 2 - 4, TOP + h - 13, this.width / 2 + hintW / 2 + 4, TOP + h - 2, 0x88000000);
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hint), this.width / 2, TOP + h - 11, 0xFFAAAAAA);
        }

        super.render(ctx, mouseX, mouseY, delta);

        // подсказка над меткой
        if (hover != null && p != null) {
            double dist = Math.sqrt(Math.pow(hover.x + 0.5 - p.getX(), 2) + Math.pow(hover.z + 0.5 - p.getZ(), 2));
            List<Text> tip = new ArrayList<>();
            tip.add(Text.literal(hover.name));
            tip.add(Text.literal(hover.x + " " + hover.y + " " + hover.z));
            tip.add(Text.literal((int) dist + I18n.t(" м") + "   " + I18n.t("высота ") + (hover.y - (int) p.getY())));
            ctx.drawTooltip(this.textRenderer, tip, mouseX, mouseY);
        }
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 0.01 ? String.valueOf((long) Math.rint(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // фон рисуем сами (карта на весь экран), без размытия
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
