package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Меню меток: добавить по координатам (даже там, где не были), изменить, скрыть, удалить; статус загрузки чанка. */
public class WaypointScreen extends Screen {
    private static final int ROW_H = 26;
    private static final int W = 330;

    private final Screen parent;
    private String fName = "", fX = "", fY = "", fZ = "", msg = "";
    private int fColor;
    private int fDim;
    private int editing = -1;
    private int first = 0;
    private int listTop, listBottom;
    private TextFieldWidget nameF, xF, yF, zF;

    public WaypointScreen(Screen parent) {
        super(Text.literal(I18n.t("Метки")));
        this.parent = parent;
        this.fColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, ClickerConfig.wpNewColor));
        this.fDim = Waypoints.dimIndex(Waypoints.currentDim(MinecraftClient.getInstance()));
    }

    private Text colorText() {
        return Text.literal(I18n.t("Цвет: ") + I18n.t(Waypoints.COLOR_NAMES[fColor]));
    }

    private Text dimText() {
        return Text.literal(I18n.t(Waypoints.DIM_NAMES[fDim]));
    }

    private TextFieldWidget coordField(int x, int y, int w, String ph, String value, java.util.function.Consumer<String> set) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 20, Text.literal(""));
        f.setMaxLength(9);
        f.setTextPredicate(s -> s.matches("-?\\d*"));
        f.setPlaceholder(Text.literal(ph));
        f.setText(value);
        f.setChangedListener(set::accept);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int x0 = this.width / 2 - W / 2;

        nameF = new TextFieldWidget(this.textRenderer, x0, 28, 200, 20, Text.literal(""));
        nameF.setMaxLength(32);
        nameF.setPlaceholder(Text.literal(I18n.t("Название")));
        nameF.setText(fName);
        nameF.setChangedListener(t -> fName = t);
        addDrawableChild(nameF);

        addDrawableChild(ButtonWidget.builder(colorText(), b -> {
            fColor = (fColor + 1) % Waypoints.COLORS.length;
            b.setMessage(colorText());
        }).dimensions(x0 + 206, 28, 124, 20).build());

        xF = coordField(x0, 52, 70, "X", fX, t -> fX = t);
        yF = coordField(x0 + 76, 52, 70, "Y", fY, t -> fY = t);
        zF = coordField(x0 + 152, 52, 70, "Z", fZ, t -> fZ = t);

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Моя позиция")), b -> {
            if (this.client == null || this.client.player == null) return;
            BlockPos p = this.client.player.getBlockPos();
            xF.setText(String.valueOf(p.getX()));
            yF.setText(String.valueOf(p.getY()));
            zF.setText(String.valueOf(p.getZ()));
        }).dimensions(x0 + 228, 52, 102, 20).build());

        addDrawableChild(ButtonWidget.builder(dimText(), b -> {
            fDim = (fDim + 1) % Waypoints.DIMS.length;
            b.setMessage(dimText());
        }).dimensions(x0, 76, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t(editing >= 0 ? "Сохранить" : "Добавить")), b -> save())
                .dimensions(x0 + 106, 76, 130, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Очистить")), b -> {
            clearForm();
            clearAndInit();
        }).dimensions(x0 + 242, 76, 88, 20).build());

        listTop = 104;
        listBottom = this.height - 60;
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int idx = first + i;
            if (idx >= Waypoints.list.size()) break;
            Waypoint w = Waypoints.list.get(idx);
            int y = listTop + i * ROW_H + 2;
            addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t(w.enabled ? "Вкл" : "Выкл")), b -> {
                w.enabled = !w.enabled;
                Waypoints.save();
                b.setMessage(Text.literal(I18n.t(w.enabled ? "Вкл" : "Выкл")));
            }).dimensions(x0 + W - 88, y, 34, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Изм.")), b -> {
                fName = w.name;
                fX = String.valueOf(w.x);
                fY = String.valueOf(w.y);
                fZ = String.valueOf(w.z);
                fColor = w.color;
                fDim = Waypoints.dimIndex(w.dim);
                editing = idx;
                msg = "";
                clearAndInit();
            }).dimensions(x0 + W - 52, y, 34, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> {
                Waypoints.list.remove(idx);
                if (editing == idx) editing = -1;
                else if (editing > idx) editing--;
                Waypoints.save();
                first = Math.max(0, Math.min(first, Waypoints.list.size() - visibleRows()));
                clearAndInit();
            }).dimensions(x0 + W - 16, y, 16, 20).build());
        }

        int by = this.height - 52;
        TextFieldWidget sx = new TextFieldWidget(this.textRenderer, x0 + 150, by, 70, 20, Text.literal(""));
        sx.setMaxLength(9);
        sx.setTextPredicate(s -> s.matches("-?\\d*"));
        sx.setPlaceholder(Text.literal("X"));
        TextFieldWidget sz = new TextFieldWidget(this.textRenderer, x0 + 226, by, 70, 20, Text.literal(""));
        sz.setMaxLength(9);
        sz.setTextPredicate(s -> s.matches("-?\\d*"));
        sz.setPlaceholder(Text.literal("Z"));
        if (ClickerConfig.spawnKnown) {
            sx.setText(String.valueOf(ClickerConfig.spawnX));
            sz.setText(String.valueOf(ClickerConfig.spawnZ));
        }
        Runnable upd = () -> {
            Integer a = parse(sx.getText()), c = parse(sz.getText());
            if (a != null && c != null) {
                ClickerConfig.spawnKnown = true;
                ClickerConfig.spawnX = a;
                ClickerConfig.spawnZ = c;
            } else {
                ClickerConfig.spawnKnown = false;
            }
        };
        sx.setChangedListener(t -> upd.run());
        sz.setChangedListener(t -> upd.run());
        addDrawableChild(sx);
        addDrawableChild(sz);

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private static Integer parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private void clearForm() {
        fName = "";
        fX = "";
        fY = "";
        fZ = "";
        editing = -1;
        msg = "";
    }

    private void save() {
        Integer x = parse(fX), z = parse(fZ);
        if (x == null || z == null) {
            msg = I18n.t("Введите координаты X и Z");
            return;
        }
        Integer yv = parse(fY);
        int y = yv != null ? yv : 64;
        String name = fName.trim();
        String dim = Waypoints.DIMS[fDim];
        if (editing >= 0 && editing < Waypoints.list.size()) {
            Waypoint w = Waypoints.list.get(editing);
            if (!name.isEmpty()) w.name = name;
            w.x = x;
            w.y = y;
            w.z = z;
            w.color = fColor;
            w.dim = dim;
        } else {
            if (name.isEmpty()) name = I18n.t("Метка ") + (Waypoints.list.size() + 1);
            Waypoints.list.add(new Waypoint(name, x, y, z, fColor, dim));
            first = Math.max(0, Waypoints.list.size() - visibleRows());
        }
        Waypoints.save();
        clearForm();
        clearAndInit();
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, Waypoints.list.size() - visibleRows());
        int nf = Math.max(0, Math.min(max, first - (int) Math.signum(verticalAmount)));
        if (nf != first) {
            first = nf;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int x0 = this.width / 2 - W / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        if (!msg.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(msg), x0, 98 - 1, 0xFFFF6666);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int idx = first + i;
            if (idx >= Waypoints.list.size()) break;
            Waypoint w = Waypoints.list.get(idx);
            int y = listTop + i * ROW_H;
            int col = Waypoints.COLORS[Math.max(0, Math.min(Waypoints.COLORS.length - 1, w.color))];
            context.fill(x0, y + 3, x0 + 9, y + 12, 0xFF000000);
            context.fill(x0 + 1, y + 4, x0 + 8, y + 11, col);
            String l1 = w.name + "  " + w.x + ", " + w.y + ", " + w.z + "  [" + I18n.t(Waypoints.DIM_NAMES[Waypoints.dimIndex(w.dim)]) + "]";
            context.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(l1, W - 110)),
                    x0 + 14, y + 3, w.enabled ? 0xFFFFFFFF : 0xFF777777);
            int st = Waypoints.statusOf(mc, w);
            String s2 = st == 2 ? I18n.t("Чанк спавна: остаётся загруженным без игрока")
                    : st == 1 ? I18n.t("Загружен сейчас, без игрока выгрузится")
                    : I18n.t("Чанк не загружен");
            int c2 = st == 2 ? 0xFF55FF55 : st == 1 ? 0xFFFFD866 : 0xFF999999;
            context.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(s2, W - 110)),
                    x0 + 14, y + 14, c2);
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.t("Спавн мира (X, Z):")),
                x0, this.height - 52 + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(I18n.t("Форсчанки и чанклоадеры клиент определить не может"), W)),
                x0, this.height - 52 - 11, 0xFF777777);
    }

    @Override
    public void close() {
        ClickerConfig.save();
        Waypoints.save();
        this.client.setScreen(parent);
    }
}
