package com.example.universalmod;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Список схем: слева файлы, справа превью (вид сверху, как карта), размер и кнопки
 * «Выбрать», «Поставить здесь», «Материалы», «Переименовать», «Удалить». Внизу — смена предмета-инструмента.
 */
public class SchematicListScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int ACCENT = 0xFF56A8F5;
    private static final int PREVIEW = 120;

    private final Screen parent;
    private final List<Path> files = new ArrayList<>();
    private static final Map<String, Info> CACHE = new HashMap<>();
    private int first = 0;
    private int selected = -1;
    private boolean confirmDelete = false;
    private TextFieldWidget nameField;
    private int px, py, pw, ph, listX, listY, listW, listRows, rightX, rightW;

    /** Короткие сведения о схеме и превью. */
    private static class Info {
        long modified;
        int sx, sy, sz, blocks, cw, ch;
        int[] colors; // вид сверху, 0 — пусто
    }

    public SchematicListScreen(Screen parent) {
        super(Text.literal(I18n.t("Схемы")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        files.clear();
        files.addAll(Schematic.list());
        if (selected < 0 && SchematicTool.schem != null) {
            for (int i = 0; i < files.size(); i++) if (files.get(i).equals(SchematicTool.schem.file())) selected = i;
        }
        if (selected >= files.size()) selected = files.size() - 1;

        pw = Math.min(560, this.width - 16);
        ph = this.height - 16;
        px = (this.width - pw) / 2;
        py = 8;
        listX = px + 8;
        listW = Math.max(150, pw - PREVIEW - 40);
        listY = py + 44;
        listRows = Math.max(1, (py + ph - 60 - listY) / ROW_H);
        first = Math.max(0, Math.min(first, Math.max(0, files.size() - listRows)));
        rightX = listX + listW + 12;
        rightW = px + pw - 8 - rightX;

        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= files.size()) break;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                selected = idx;
                confirmDelete = false;
                clearAndInit();
            }).dimensions(listX, listY + i * ROW_H, listW - 6, ROW_H - 2).build());
        }

        int by = listY + PREVIEW + 52;
        int bw = rightW;
        boolean has = selected >= 0;
        ButtonWidget choose = ButtonWidget.builder(Text.literal(I18n.t("Выбрать")), b -> choose(false))
                .dimensions(rightX, by, bw, 20).build();
        ButtonWidget here = ButtonWidget.builder(Text.literal(I18n.t("Поставить у ног")), b -> choose(true))
                .dimensions(rightX, by + 22, bw, 20)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Схема встанет углом там, где вы стоите. Подвинуть — режим 2"))))
                .build();
        ButtonWidget mats = ButtonWidget.builder(Text.literal(I18n.t("Материалы")), b -> {
            choose(false);
            this.client.setScreen(new SchematicScreen(this));
        }).dimensions(rightX, by + 44, bw, 20).build();
        nameField = new TextFieldWidget(this.textRenderer, rightX, by + 70, bw - 24, 18, Text.literal(""));
        nameField.setMaxLength(48);
        nameField.setPlaceholder(Text.literal(I18n.t("новое имя")));
        ButtonWidget rename = ButtonWidget.builder(Text.literal("✎"), b -> rename())
                .dimensions(rightX + bw - 22, by + 69, 22, 20)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Переименовать")))).build();
        ButtonWidget del = ButtonWidget.builder(Text.literal(confirmDelete ? I18n.t("Точно удалить?") : I18n.t("Удалить")), b -> delete())
                .dimensions(rightX, by + 94, bw, 20).build();
        for (var w : new ButtonWidget[]{choose, here, mats, rename, del}) {
            w.active = has;
            addDrawableChild(w);
        }
        nameField.setEditable(has);
        addDrawableChild(nameField);

        // низ: инструмент и готово
        int fy = py + ph - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Инструмент: взять из руки")), b -> {
                    if (!SchematicTool.setToolFromHand(this.client)) confirmDelete = false;
                    clearAndInit();
                }).dimensions(px + 30, fy, 170, 20)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Возьмите нужный предмет в основную руку и нажмите. По умолчанию — стрела"))))
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> close())
                .dimensions(px + pw - 128, fy, 120, 20).build());
    }

    private Info info(Path p) {
        String key = p.getFileName().toString();
        long mod = 0;
        try {
            mod = Files.getLastModifiedTime(p).toMillis();
        } catch (Exception ignored) {
        }
        Info in = CACHE.get(key);
        if (in != null && in.modified == mod) return in;
        Schematic s = Schematic.load(p);
        in = new Info();
        in.modified = mod;
        if (s != null) {
            in.sx = s.sx;
            in.sy = s.sy;
            in.sz = s.sz;
            for (int b : s.blocks) if (b != 0) in.blocks++;
            makePreview(s, in);
        }
        CACHE.put(key, in);
        return in;
    }

    /** Вид сверху: верхний блок каждого столбца цветом карты, выше — светлее. */
    private static void makePreview(Schematic s, Info in) {
        int step = Math.max(1, (int) Math.ceil(Math.max(s.sx, s.sz) / 96.0));
        in.cw = (s.sx + step - 1) / step;
        in.ch = (s.sz + step - 1) / step;
        in.colors = new int[in.cw * in.ch];
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int cz = 0; cz < in.ch; cz++) {
            for (int cx = 0; cx < in.cw; cx++) {
                int x = cx * step, z = cz * step;
                for (int y = s.sy - 1; y >= 0; y--) {
                    BlockState st = s.get(x, y, z);
                    if (st.isAir()) continue;
                    int rgb = 0x808080;
                    try {
                        if (mc.world != null) rgb = st.getMapColor(mc.world, BlockPos.ORIGIN).color;
                    } catch (Throwable ignored) {
                    }
                    if (rgb == 0) rgb = 0x808080;
                    float f = s.sy <= 1 ? 1f : 0.55f + 0.45f * y / (float) (s.sy - 1);
                    int r = Math.min(255, (int) (((rgb >> 16) & 255) * f));
                    int g = Math.min(255, (int) (((rgb >> 8) & 255) * f));
                    int b = Math.min(255, (int) ((rgb & 255) * f));
                    in.colors[cx + cz * in.cw] = 0xFF000000 | (r << 16) | (g << 8) | b;
                    break;
                }
            }
        }
    }

    private void choose(boolean placeHere) {
        if (selected < 0 || selected >= files.size()) return;
        Schematic s = Schematic.load(files.get(selected));
        if (s == null) return;
        SchematicTool.select(s);
        if (placeHere && this.client.player != null) SchematicTool.placeAt(this.client, this.client.player.getBlockPos());
        if (placeHere) close();
    }

    private void rename() {
        if (selected < 0) return;
        String n = nameField.getText().trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (n.isEmpty()) return;
        Path from = files.get(selected);
        Path to = Schematic.DIR.resolve(n + Schematic.EXT);
        if (Files.exists(to)) {
            nameField.setText("");
            nameField.setPlaceholder(Text.literal(I18n.t("Такое имя уже есть")));
            return;
        }
        try {
            Files.move(from, to);
            CACHE.remove(from.getFileName().toString());
            if (SchematicTool.schem != null && SchematicTool.schem.file().equals(from)) {
                SchematicTool.schem.name = n;
                SchematicTool.saveState();
            }
        } catch (Exception ignored) {
        }
        nameField.setText("");
        clearAndInit();
        for (int i = 0; i < files.size(); i++) if (files.get(i).equals(to)) selected = i;
        clearAndInit();
    }

    private void delete() {
        if (selected < 0) return;
        if (!confirmDelete) {
            confirmDelete = true;
            clearAndInit();
            return;
        }
        Path p = files.get(selected);
        try {
            Files.deleteIfExists(p);
            CACHE.remove(p.getFileName().toString());
            if (SchematicTool.schem != null && SchematicTool.schem.file().equals(p)) SchematicTool.select(null);
        } catch (Exception ignored) {
        }
        confirmDelete = false;
        selected = -1;
        clearAndInit();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, files.size() - listRows);
        int nf = Math.max(0, Math.min(max, first - (int) Math.signum(verticalAmount)));
        if (nf != first) {
            first = nf;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        frame(ctx, px, py, pw, ph, 0xF0161B21);
        ctx.fill(px, py, px + pw, py + 30, 0xFF1E2630);
        ctx.fill(px, py + 30, px + pw, py + 31, ACCENT);
        frame(ctx, listX - 3, listY - 14, listW + 3, listRows * ROW_H + 16, 0xFF11151A);
        frame(ctx, rightX - 3, listY - 14, rightW + 6, ph - (listY - py) - 40, 0xFF11151A);
    }

    private static void frame(DrawContext ctx, int x, int y, int w, int h, int bg) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2E3742);
        ctx.fill(x, y, x + w, y + h, bg);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var tr = this.textRenderer;
        ctx.drawItem(new ItemStack(SchematicTool.toolItem()), px + 6, py + 7);
        ctx.drawTextWithShadow(tr, this.title, px + 26, py + 5, 0xFFFFFFFF);
        String cur = SchematicTool.schem == null ? I18n.t("сейчас не выбрана") : I18n.t("сейчас: ") + SchematicTool.schem.name;
        ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(cur, pw - 40)), px + 26, py + 17, 0xFF9AA4AE);
        String modeTxt = SchematicTool.modeName(SchematicTool.mode);
        ctx.drawTextWithShadow(tr, Text.literal(modeTxt), px + pw - 8 - tr.getWidth(modeTxt), py + 5, 0xFFDDDDDD);
        String keys = I18n.t("Shift+1…5 — режим");
        ctx.drawTextWithShadow(tr, Text.literal(keys), px + pw - 8 - tr.getWidth(keys), py + 17, 0xFF9AA4AE);

        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Сохранённые схемы: ") + files.size()), listX, listY - 11, 0xFFFFFFFF);
        if (files.isEmpty()) {
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Пусто. Режим 1: выделите область")), listX + 4, listY + 4, 0xFF9AA4AE);
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("и нажмите Ctrl+Shift")), listX + 4, listY + 16, 0xFF9AA4AE);
        }
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm");
        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= files.size()) break;
            Path p = files.get(idx);
            Info in = info(p);
            int x = listX, y = listY + i * ROW_H, w = listW - 6, h = ROW_H - 2;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            boolean current = SchematicTool.schem != null && SchematicTool.schem.file().equals(p);
            ctx.fill(x, y, x + w, y + h, idx == selected ? 0xFF24405F : hover ? 0xFF262E38 : (i % 2 == 0 ? 0xFF1A2027 : 0xFF161B21));
            if (idx == selected) ctx.fill(x, y, x + 2, y + h, ACCENT);
            // мини-превью в строке
            miniPreview(ctx, in, x + 4, y + 2, 16);
            String name = p.getFileName().toString();
            name = name.substring(0, name.length() - Schematic.EXT.length());
            String size = in.sx + "×" + in.sy + "×" + in.sz;
            ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(name, w - 30 - tr.getWidth(size))), x + 24, y + 2,
                    current ? 0xFF5BD38A : 0xFFFFFFFF);
            ctx.drawTextWithShadow(tr, Text.literal(df.format(new Date(in.modified))), x + 24, y + 11, 0xFF6F7A86);
            ctx.drawTextWithShadow(tr, Text.literal(size), x + w - 4 - tr.getWidth(size), y + 6, 0xFF9AA4AE);
        }

        // справа — превью выбранной
        if (selected >= 0 && selected < files.size()) {
            Info in = info(files.get(selected));
            int bx = rightX + (rightW - PREVIEW) / 2;
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Вид сверху")), rightX, listY - 11, 0xFFFFFFFF);
            ctx.fill(bx - 1, listY - 1, bx + PREVIEW + 1, listY + PREVIEW + 1, 0xFF2E3742);
            ctx.fill(bx, listY, bx + PREVIEW, listY + PREVIEW, 0xFF0E1216);
            miniPreview(ctx, in, bx, listY, PREVIEW);
            int ty = listY + PREVIEW + 6;
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Размер: ") + in.sx + "×" + in.sy + "×" + in.sz), rightX, ty, 0xFFDDDDDD);
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Блоков: ") + in.blocks), rightX, ty + 11, 0xFFDDDDDD);
            ctx.drawTextWithShadow(tr, Text.literal(df.format(new Date(in.modified))), rightX, ty + 22, 0xFF6F7A86);
        } else {
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Выберите схему слева")), rightX, listY - 11, 0xFF9AA4AE);
        }
        ctx.drawItem(new ItemStack(SchematicTool.toolItem()), px + 10, py + ph - 24);
    }

    /** Превью в квадрате size×size. */
    private static void miniPreview(DrawContext ctx, Info in, int x, int y, int size) {
        if (in.colors == null || in.cw == 0 || in.ch == 0) {
            ctx.fill(x, y, x + size, y + size, 0xFF2A313A);
            return;
        }
        float cell = Math.min(size / (float) in.cw, size / (float) in.ch);
        int ox = x + (int) ((size - cell * in.cw) / 2), oy = y + (int) ((size - cell * in.ch) / 2);
        if (cell < 1f) {
            // совсем маленькое — рисуем через пропуск
            int stepX = (int) Math.ceil(in.cw / (float) size), stepZ = (int) Math.ceil(in.ch / (float) size);
            for (int py = 0; py < Math.min(size, in.ch); py++) {
                for (int px = 0; px < Math.min(size, in.cw); px++) {
                    int cx = Math.min(in.cw - 1, px * stepX), cz = Math.min(in.ch - 1, py * stepZ);
                    int c = in.colors[cx + cz * in.cw];
                    if (c != 0) ctx.fill(x + px, y + py, x + px + 1, y + py + 1, c);
                }
            }
            return;
        }
        for (int cz = 0; cz < in.ch; cz++) {
            int y1 = oy + (int) (cz * cell), y2 = oy + (int) ((cz + 1) * cell);
            for (int cx = 0; cx < in.cw; cx++) {
                int c = in.colors[cx + cz * in.cw];
                if (c == 0) continue;
                ctx.fill(ox + (int) (cx * cell), y1, ox + (int) ((cx + 1) * cell), y2, c);
            }
        }
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
