package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Макросы: слева список биндов (клавиша + сообщение), справа настройки выбранного бинда:
 * сообщение/команда, клавиша, автоповтор вкл/выкл, режим повтора, интервал.
 * Всё сохраняется сразу.
 */
public class MacroScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int GAP = 2;

    private static class Lbl {
        final String text;
        final int x, y, color;

        Lbl(String text, int x, int y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    private final Screen parent;
    private final List<ButtonWidget> listWs = new ArrayList<>();      // прокручиваемый список биндов
    private final List<ClickableWidget> panelWs = new ArrayList<>();  // правая панель + кнопка «добавить»
    private final List<Lbl> labels = new ArrayList<>();
    private ButtonWidget doneButton;
    private ButtonWidget addButton;
    private ButtonWidget keyButton;
    private ButtonWidget globalButton;

    private int selected = 0;
    private boolean listening = false;
    private int scroll = 0;
    private int viewTop, viewBottom, listTop, listBottom;
    private int x0, pw, lw, rx, rw;
    private int warnY = -1;
    private int lastMouseX = 0, lastMouseY = 0;

    public MacroScreen(Screen parent) {
        super(Text.literal(I18n.t("Макросы")));
        this.parent = parent;
    }

    private static String onOff(boolean on) {
        return on ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ");
    }

    private MacroBind current() {
        List<MacroBind> binds = MacroConfig.binds;
        if (selected < 0 || selected >= binds.size()) return null;
        return binds.get(selected);
    }

    private Text entryLabel(int i) {
        MacroBind m = MacroConfig.binds.get(i);
        String key = m.keyCode < 0 ? "-" : m.keyText().getString();
        String txt = m.text.isBlank() ? I18n.t("(пусто)") : m.text;
        String s = (m.running ? "▶ " : "") + "[" + key + "] " + txt;
        return Text.literal(this.textRenderer.trimToWidth(s, lw - 14));
    }

    private Text keyLabel(MacroBind m) {
        if (listening) return Text.literal("> ... <");
        return m.keyText();
    }

    private Text globalText() {
        return Text.literal(I18n.t("Макросы") + ": " + onOff(ClickerConfig.macrosEnabled));
    }

    @Override
    protected void init() {
        listWs.clear();
        panelWs.clear();
        labels.clear();
        warnY = -1;

        pw = Math.min(420, this.width - 20);
        x0 = (this.width - pw) / 2;
        lw = Math.min(140, pw * 2 / 5);
        rx = x0 + lw + 8;
        rw = pw - lw - 8;
        viewTop = 34;
        viewBottom = this.height - 34;
        listTop = viewTop;
        listBottom = viewBottom - 24;

        if (selected >= MacroConfig.binds.size()) selected = MacroConfig.binds.size() - 1;
        if (selected < 0) selected = 0;

        // Глобальный переключатель над панелями
        globalButton = ButtonWidget.builder(globalText(), b -> {
            ClickerConfig.macrosEnabled = !ClickerConfig.macrosEnabled;
            ClickerConfig.save();
            b.setMessage(globalText());
        }).dimensions(x0, 8, pw, 20).build();
        panelWs.add(globalButton);

        // ----- Левая колонка: список биндов -----
        for (int i = 0; i < MacroConfig.binds.size(); i++) {
            final int idx = i;
            ButtonWidget e = ButtonWidget.builder(entryLabel(i), b -> {
                selected = idx;
                listening = false;
                this.clearAndInit();
            }).dimensions(x0, 0, lw, ROW_H).build();
            listWs.add(e);
            addSelectableChild(e);
        }

        addButton = ButtonWidget.builder(Text.literal(I18n.t("+ Добавить макрос")), b -> {
            MacroBind nb = new MacroBind();
            nb.version = MacroBind.CURRENT_VERSION;
            MacroConfig.binds.add(nb);
            selected = MacroConfig.binds.size() - 1;
            listening = false;
            MacroConfig.save();
            scrollToSelected();
            this.clearAndInit();
        }).dimensions(x0, viewBottom - 20, lw, 20).build();
        panelWs.add(addButton);
        addSelectableChild(addButton);

        // ----- Правая колонка: настройки выбранного бинда -----
        final MacroBind m = current();
        if (m != null) buildSettings(m);

        for (ClickableWidget w : panelWs) {
            if (w != addButton) addSelectableChild(w);
        }

        doneButton = ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build();
        addDrawableChild(doneButton);

        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        updateListPositions();
    }

    private void buildSettings(final MacroBind m) {
        int y = viewTop + 8;

        labels.add(new Lbl(I18n.t("Макрос ") + (selected + 1), rx, y, 0xFFFFD866));
        y += 16;

        // Сообщение / команда
        labels.add(new Lbl(I18n.t("Сообщение или /команда:"), rx, y, 0xFFAAAAAA));
        TextFieldWidget tf = new TextFieldWidget(this.textRenderer, rx, y + 11, rw, 20, Text.literal(""));
        tf.setMaxLength(256);
        tf.setText(m.text);
        tf.setPlaceholder(Text.literal(I18n.t("Сообщение или /команда")));
        tf.setChangedListener(t -> {
            m.text = t;
            MacroConfig.save();
        });
        panelWs.add(tf);
        y += 36;

        // Клавиша (бинд)
        labels.add(new Lbl(I18n.t("Клавиша (бинд):"), rx, y, 0xFFAAAAAA));
        keyButton = ButtonWidget.builder(keyLabel(m), b -> {
            listening = true;
            b.setMessage(keyLabel(m));
        }).dimensions(rx, y + 11, rw - 24, 20).build();
        panelWs.add(keyButton);
        panelWs.add(ButtonWidget.builder(Text.literal("X"), b -> {
            m.keyCode = -1;
            m.mouse = false;
            listening = false;
            MacroConfig.save();
            this.clearAndInit();
        }).dimensions(rx + rw - 20, y + 11, 20, 20)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Сбросить клавишу")))).build());
        y += 36;

        // Автоповтор вкл/выкл
        panelWs.add(ButtonWidget.builder(Text.literal(I18n.t("Автоповтор: ") + onOff(m.repeat)), b -> {
            m.repeat = !m.repeat;
            if (!m.repeat) m.running = false;
            MacroConfig.save();
            this.clearAndInit();
        }).dimensions(rx, y, rw, 20)
                .tooltip(Tooltip.of(Text.literal(I18n.t("Автоматически повторять сообщение или команду")))).build());
        y += 24;

        if (m.repeat) {
            // Режим повтора
            panelWs.add(ButtonWidget.builder(Text.literal(m.holdOnly
                            ? I18n.t("Режим: пока зажата") : I18n.t("Режим: клавиша вкл/выкл")), b -> {
                m.holdOnly = !m.holdOnly;
                if (m.holdOnly) m.running = false;
                MacroConfig.save();
                this.clearAndInit();
            }).dimensions(rx, y, rw, 20)
                    .tooltip(Tooltip.of(Text.literal(m.holdOnly
                            ? I18n.t("Повтор идёт, только пока клавиша зажата")
                            : I18n.t("Нажал клавишу — повтор включился, нажал ещё раз — выключился")))).build());
            y += 24;

            // Интервал + запуск
            labels.add(new Lbl(I18n.t("Интервал повтора, мс:"), rx, y, 0xFFAAAAAA));
            int ivw = Math.min(70, rw / 2 - 2);
            TextFieldWidget iv = new TextFieldWidget(this.textRenderer, rx, y + 11, ivw, 20, Text.literal(""));
            iv.setMaxLength(7);
            iv.setTextPredicate(s -> s.matches("\\d*"));
            iv.setText(String.valueOf(m.intervalMs()));
            iv.setPlaceholder(Text.literal(I18n.t("Интервал, мс")));
            iv.setChangedListener(t -> {
                if (t.isEmpty()) return;
                try {
                    long ms = Math.max(MacroBind.MIN_INTERVAL_MS, Math.min(3_600_000L, Long.parseLong(t)));
                    m.seconds = (int) (ms / 1000);
                    m.millis = (int) (ms % 1000);
                    MacroConfig.save();
                } catch (NumberFormatException ignored) {
                }
            });
            panelWs.add(iv);

            ButtonWidget run = ButtonWidget.builder(Text.literal(m.running
                            ? "■ " + I18n.t("Остановить") : "▶ " + I18n.t("Запустить сейчас")), b -> {
                m.running = !m.running;
                m.nextAt = 0;
                this.clearAndInit();
            }).dimensions(rx + ivw + 4, y + 11, rw - ivw - 4, 20).build();
            run.active = !m.holdOnly;
            panelWs.add(run);
            y += 36;

            warnY = y;
            y += 12;
        }

        // Удалить
        panelWs.add(ButtonWidget.builder(Text.literal(I18n.t("Удалить макрос")), b -> {
            MacroConfig.binds.remove(m);
            listening = false;
            MacroConfig.save();
            this.clearAndInit();
        }).dimensions(rx, y + 4, rw, 20).build());
    }

    // ---------- Прокрутка списка ----------

    private int listContentH() {
        return MacroConfig.binds.size() * (ROW_H + GAP) + GAP;
    }

    private int maxScroll() {
        return Math.max(0, listContentH() - (listBottom - listTop));
    }

    private void scrollToSelected() {
        int bottom = (MacroConfig.binds.size()) * (ROW_H + GAP) + GAP;
        scroll = Math.max(0, bottom - (listBottom - listTop));
    }

    private void updateListPositions() {
        for (int i = 0; i < listWs.size(); i++) {
            listWs.get(i).setY(listTop + GAP + i * (ROW_H + GAP) - scroll);
        }
    }

    private boolean inList(double mx, double my) {
        return mx >= x0 && mx <= x0 + lw && my >= listTop && my <= listBottom;
    }

    // ---------- Ввод ----------

    private void bind(boolean mouse, int code) {
        MacroBind m = current();
        listening = false;
        if (m != null) {
            m.keyCode = code;
            m.mouse = mouse;
            MacroConfig.save();
        }
        this.clearAndInit();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                bind(false, -1);   // Esc = снять клавишу
            } else {
                bind(false, InputUtil.fromKeyCode(input).getCode());
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listening) {
            bind(true, click.button());
            return true;
        }
        // Кнопки списка за пределами окна прокрутки не должны ловить клики
        if (lastMouseX >= x0 && lastMouseX <= x0 + lw && !inList(lastMouseX, lastMouseY)) {
            if (doneButton.mouseClicked(click, doubled)) return true;
            if (globalButton.mouseClicked(click, doubled)) return true;
            return addButton.mouseClicked(click, doubled);
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inList(mouseX, mouseY)) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * 22)));
            updateListPositions();
        }
        return true;
    }

    // ---------- Отрисовка ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        for (int i = 0; i < listWs.size(); i++) listWs.get(i).setMessage(entryLabel(i));
        updateListPositions();
        super.render(context, mouseX, mouseY, delta);

        // Фон панелей
        context.fill(x0 - 4, viewTop, x0 + lw + 4, viewBottom, 0x55000000);
        context.fill(rx - 4, viewTop, x0 + pw + 4, viewBottom, 0x55000000);

        // Список биндов
        context.enableScissor(x0 - 4, listTop, x0 + lw + 4, listBottom);
        for (int i = 0; i < listWs.size(); i++) {
            ButtonWidget e = listWs.get(i);
            if (e.getY() + ROW_H < listTop || e.getY() > listBottom) continue;
            if (i == selected) {
                context.fill(x0 - 2, e.getY() - 1, x0 + lw + 2, e.getY() + ROW_H + 1, 0xCCFFD866);
            }
            e.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();

        int max = maxScroll();
        if (max > 0) {
            int viewH = listBottom - listTop;
            int thumb = Math.max(16, viewH * viewH / listContentH());
            int ty = listTop + (int) ((viewH - thumb) * (scroll / (double) max));
            context.fill(x0 + lw + 5, listTop, x0 + lw + 7, listBottom, 0x33FFFFFF);
            context.fill(x0 + lw + 5, ty, x0 + lw + 7, ty + thumb, 0xAAFFFFFF);
        }

        // Правая панель
        MacroBind m = current();
        if (m == null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.t("Нет макросов. Добавьте первый слева.")),
                    rx, viewTop + 10, 0xFFAAAAAA);
        }
        for (Lbl l : labels) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(l.text), l.x, l.y, l.color);
        }
        if (m != null && warnY >= 0 && m.repeat && m.intervalMs() < 1000) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("! " + this.textRenderer.trimToWidth(I18n.t("Слишком частый повтор может кикнуть за спам"), rw - 10)),
                    rx, warnY, 0xFFFFAA33);
        }
        for (ClickableWidget w : panelWs) w.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        MacroConfig.save();
        this.client.setScreen(parent);
    }
}
