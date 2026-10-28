package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Главное меню мода: прокручиваемый список настроек по разделам, клавиша — рядом с каждой настройкой. */
public class ClickerScreen extends Screen {
    private static final int KW = 90;   // ширина кнопки клавиши
    private static final int ROW = 24;  // шаг строк

    // Индексы клавиш в UniversalMod.keys()
    private static final int K_AUTO_L = 0, K_AUTO_R = 1, K_HOLD_L = 2, K_HOLD_R = 3,
            K_FAST_L = 4, K_FAST_R = 5, K_PLACE = 6, K_BREAK = 7, K_COOLDOWN = 8,
            K_SHULKER = 9, K_SHULKER_MODE = 10, K_FULLBRIGHT = 11, K_SETTINGS = 12,
            K_FOOD = 13, K_ARMOR = 14, K_MACROS = 15, K_FREELOOK = 16, K_SEARCH = 17, K_ZOOM = 18,
            K_SORT_CH = 19, K_SORT_CV = 20, K_SORT_IH = 21, K_SORT_IV = 22,
            K_REFILL_H = 23, K_REFILL_O = 24, K_INVIS = 25, K_FRAMES = 26, K_OUTLINE = 27, K_CONT_HL = 28;

    private static class Entry {
        int y;
        int h;
        String header;
        String label;
        ClickableWidget a;
        ClickableWidget b;
    }

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private ButtonWidget[] keyButtons;
    private ButtonWidget doneButton;
    private int listening = -1;
    private int scroll = 0;
    private int contentH = 0;
    private int viewTop, viewBottom, x0, cw, cursor;
    private int lastMouseY = 0;

    public ClickerScreen(Screen parent) {
        super(Text.literal("Universal Mod"));
        this.parent = parent;
    }

    private int wd(int keyIdx) {
        return keyIdx >= 0 ? cw - KW - 4 : cw;
    }

    private void section(String title) {
        if (cursor > 0) cursor += 8;
        Entry e = new Entry();
        e.y = cursor;
        e.h = 16;
        e.header = I18n.t(title);
        entries.add(e);
        cursor += 18;
    }

    private void row(ClickableWidget w, int keyIdx) {
        Entry e = new Entry();
        e.y = cursor;
        e.h = 20;
        e.a = w;
        addSelectableChild(w);
        if (keyIdx >= 0) {
            e.b = keyButton(keyIdx);
            addSelectableChild(e.b);
        }
        entries.add(e);
        cursor += ROW;
    }

    private void labelRow(String label, int keyIdx) {
        Entry e = new Entry();
        e.y = cursor;
        e.h = 20;
        e.label = I18n.t(label);
        e.b = keyButton(keyIdx);
        addSelectableChild(e.b);
        entries.add(e);
        cursor += ROW;
    }

    private void toggle(String label, BooleanSupplier state, Runnable flip, int keyIdx) {
        row(toggleButton(x0, 0, wd(keyIdx), 20, I18n.t(label), state, flip), keyIdx);
    }

    private void slider(String label, String suffix, int min, int max, int val, IntConsumer set) {
        row(new ValueSlider(x0, 0, cw, 20, I18n.t(label), suffix, min, max, val, set), -1);
    }

    /** Ползунок с названиями вариантов вместо чисел. */
    private void sliderNamed(String label, String[] names, int val, IntConsumer set) {
        String[] tr = new String[names.length];
        for (int i = 0; i < tr.length; i++) tr[i] = I18n.t(names[i]);
        ValueSlider sl = new ValueSlider(x0, 0, cw, 20, I18n.t(label), "", 0, names.length - 1,
                Math.max(0, Math.min(names.length - 1, val)), set);
        sl.setNames(tr);
        row(sl, -1);
    }

    private void button(String label, Runnable action) {
        row(ButtonWidget.builder(Text.literal(label), b -> action.run()).dimensions(x0, 0, cw, 20).build(), -1);
    }

    private ButtonWidget keyButton(int idx) {
        ButtonWidget b = ButtonWidget.builder(keyLabel(idx), btn -> {
            listening = idx;
            refreshKeys();
        }).dimensions(x0 + cw - KW, 0, KW, 20).build();
        keyButtons[idx] = b;
        return b;
    }

    private Text keyLabel(int idx) {
        if (listening == idx) return Text.literal("> ... <");
        KeyBinding k = UniversalMod.keys()[idx];
        if (k.isUnbound()) return Text.literal(I18n.t("Назначить"));
        return k.getBoundKeyLocalizedText();
    }

    private void refreshKeys() {
        for (int i = 0; i < keyButtons.length; i++) {
            if (keyButtons[i] != null) keyButtons[i].setMessage(keyLabel(i));
        }
    }

    @Override
    protected void init() {
        entries.clear();
        keyButtons = new ButtonWidget[UniversalMod.keys().length];
        cw = Math.min(320, this.width - 30);
        x0 = (this.width - cw) / 2;
        viewTop = 30;
        viewBottom = this.height - 34;
        cursor = 0;

        section("Язык");
        button("Язык / Til / Language: " + I18n.langName(), () -> {
            I18n.nextLang();
            ClickerConfig.save();
            this.clearAndInit();
        });

        section("Аккаунт");
        button(I18n.t("Сменить ник...") + " (" + NicknameScreen.current() + ")",
                () -> this.client.setScreen(new NicknameScreen(this)));

        section("Автокликер");
        slider("Скорость ЛКМ", " CPS", ClickerConfig.MIN_CPS, ClickerConfig.MAX_CPS,
                ClickerConfig.leftCps, v -> ClickerConfig.leftCps = v);
        slider("Скорость ПКМ", " CPS", ClickerConfig.MIN_CPS, ClickerConfig.MAX_CPS,
                ClickerConfig.rightCps, v -> ClickerConfig.rightCps = v);
        toggle("Автоклик ЛКМ", UniversalMod::isLeftOn,
                () -> UniversalMod.setLeftOn(!UniversalMod.isLeftOn()), K_AUTO_L);
        toggle("Автоклик ПКМ", UniversalMod::isRightOn,
                () -> UniversalMod.setRightOn(!UniversalMod.isRightOn()), K_AUTO_R);
        toggle("Зажать ЛКМ", UniversalMod::isLeftHold,
                () -> UniversalMod.setLeftHold(!UniversalMod.isLeftHold()), K_HOLD_L);
        toggle("Зажать ПКМ", UniversalMod::isRightHold,
                () -> UniversalMod.setRightHold(!UniversalMod.isRightHold()), K_HOLD_R);
        toggle("ЛКМ по кулдауну", () -> ClickerConfig.leftCooldown,
                () -> ClickerConfig.leftCooldown = !ClickerConfig.leftCooldown, K_COOLDOWN);

        section("Быстрые клики");
        toggle("Быстрый ЛКМ", () -> ClickerConfig.fastLeftClick,
                () -> ClickerConfig.fastLeftClick = !ClickerConfig.fastLeftClick, K_FAST_L);
        slider("Кликов за тик ЛКМ", "", ClickerConfig.MIN_FAST, ClickerConfig.MAX_FAST,
                ClickerConfig.fastLeftCount, v -> ClickerConfig.fastLeftCount = v);
        toggle("Быстрый ПКМ", () -> ClickerConfig.fastRightClick,
                () -> ClickerConfig.fastRightClick = !ClickerConfig.fastRightClick, K_FAST_R);
        slider("Кликов за тик ПКМ", "", ClickerConfig.MIN_FAST, ClickerConfig.MAX_FAST,
                ClickerConfig.fastRightCount, v -> ClickerConfig.fastRightCount = v);

        section("Блоки");
        toggle("Быстрая установка", () -> ClickerConfig.fastPlace,
                () -> ClickerConfig.fastPlace = !ClickerConfig.fastPlace, K_PLACE);
        toggle("Быстрое разрушение", () -> ClickerConfig.fastBreak,
                () -> ClickerConfig.fastBreak = !ClickerConfig.fastBreak, K_BREAK);

        section("Шалкеры");
        toggle("Просмотр шалкера", () -> ClickerConfig.shulkerPreview,
                () -> ClickerConfig.shulkerPreview = !ClickerConfig.shulkerPreview, K_SHULKER);
        row(ButtonWidget.builder(shulkerModeText(), b -> {
            ClickerConfig.shulkerSum = !ClickerConfig.shulkerSum;
            b.setMessage(shulkerModeText());
        }).dimensions(x0, 0, wd(K_SHULKER_MODE), 20).build(), K_SHULKER_MODE);

        section("Яркость");
        toggle("Фулбрайт", () -> ClickerConfig.fullbright,
                () -> ClickerConfig.fullbright = !ClickerConfig.fullbright, K_FULLBRIGHT);

        section("Камера");
        labelRow("Осмотр камеры (зажать)", K_FREELOOK);
        toggle("Убрать наклон экрана при уроне", () -> ClickerConfig.noHurtTilt,
                () -> ClickerConfig.noHurtTilt = !ClickerConfig.noHurtTilt, -1);

        section("Невидимые блоки");
        toggle("Показывать невидимые блоки", () -> ClickerConfig.showInvisible,
                () -> ClickerConfig.showInvisible = !ClickerConfig.showInvisible, K_INVIS);
        toggle("Барьер", () -> ClickerConfig.invisBarrier,
                () -> ClickerConfig.invisBarrier = !ClickerConfig.invisBarrier, -1);
        toggle("Структурная пустота", () -> ClickerConfig.invisVoid,
                () -> ClickerConfig.invisVoid = !ClickerConfig.invisVoid, -1);
        toggle("Блок света", () -> ClickerConfig.invisLight,
                () -> ClickerConfig.invisLight = !ClickerConfig.invisLight, -1);
        slider("Радиус показа", " бл.", 4, 32, Math.max(4, Math.min(32, ClickerConfig.invisRadius)),
                v -> ClickerConfig.invisRadius = v);

        section("Рамки блоков");
        toggle("Показывать рамки блоков", () -> ClickerConfig.showFrames,
                () -> ClickerConfig.showFrames = !ClickerConfig.showFrames, K_FRAMES);
        toggle("Подозрительный песок", () -> ClickerConfig.frameSand,
                () -> ClickerConfig.frameSand = !ClickerConfig.frameSand, -1);
        toggle("Подозрительный гравий", () -> ClickerConfig.frameGravel,
                () -> ClickerConfig.frameGravel = !ClickerConfig.frameGravel, -1);
        toggle("Светокамень", () -> ClickerConfig.frameGlowstone,
                () -> ClickerConfig.frameGlowstone = !ClickerConfig.frameGlowstone, -1);
        toggle("Рыхлый снег", () -> ClickerConfig.framePowderSnow,
                () -> ClickerConfig.framePowderSnow = !ClickerConfig.framePowderSnow, -1);
        slider("Радиус рамок", " бл.", 4, 32, Math.max(4, Math.min(32, ClickerConfig.frameRadius)),
                v -> ClickerConfig.frameRadius = v);

        section("Контур блока");
        sliderNamed("Контур блока под прицелом", TargetOutline.NAMES, ClickerConfig.targetOutline,
                v -> ClickerConfig.targetOutline = v);
        labelRow("Контур блока: переключить цвет", K_OUTLINE);

        section("Частицы невидимости");
        sliderNamed("Частицы зелья невидимости", InvisibleParticles.NAMES, ClickerConfig.invisParticle,
                v -> ClickerConfig.invisParticle = v);

        section("Автозаполнение");
        toggle("Автозаполнение хотбара", () -> ClickerConfig.refillHotbar,
                () -> ClickerConfig.refillHotbar = !ClickerConfig.refillHotbar, K_REFILL_H);
        toggle("Автозаполнение второй руки", () -> ClickerConfig.refillOffhand,
                () -> ClickerConfig.refillOffhand = !ClickerConfig.refillOffhand, K_REFILL_O);
        slider("Пополнять, если меньше", " шт.", 1, 32, Math.max(1, Math.min(32, ClickerConfig.refillMin)),
                v -> ClickerConfig.refillMin = v);

        section("Сортировка");
        toggle("Кнопки сортировки в контейнере", () -> ClickerConfig.sortButtons,
                () -> ClickerConfig.sortButtons = !ClickerConfig.sortButtons, -1);
        toggle("Кнопки сортировки инвентаря", () -> ClickerConfig.sortInvButtons,
                () -> ClickerConfig.sortInvButtons = !ClickerConfig.sortInvButtons, -1);
        slider("Скорость сортировки", " кл./тик", 1, 20, ClickerConfig.sortSpeed, v -> ClickerConfig.sortSpeed = v);
        labelRow("Сорт. контейнера: по горизонтали", K_SORT_CH);
        labelRow("Сорт. контейнера: по вертикали", K_SORT_CV);
        labelRow("Сорт. инвентаря: по горизонтали", K_SORT_IH);
        labelRow("Сорт. инвентаря: по вертикали", K_SORT_IV);

        section("Зум");
        toggle("Зум", () -> ClickerConfig.zoom, () -> ClickerConfig.zoom = !ClickerConfig.zoom, -1);
        labelRow("Зум (зажать)", K_ZOOM);
        slider("Кратность зума", "x", 2, 20, Math.max(2, Math.min(20, Math.round(ClickerConfig.zoomLevel))),
                v -> ClickerConfig.zoomLevel = v);
        toggle("Плавный зум", () -> ClickerConfig.zoomSmooth, () -> ClickerConfig.zoomSmooth = !ClickerConfig.zoomSmooth, -1);
        toggle("Снижать чувствительность при зуме", () -> ClickerConfig.zoomSens, () -> ClickerConfig.zoomSens = !ClickerConfig.zoomSens, -1);

        section("Поиск в контейнерах");
        toggle("Подсветка контейнеров", () -> ClickerConfig.containerSearch,
                () -> ClickerConfig.containerSearch = !ClickerConfig.containerSearch, K_CONT_HL);
        row(ButtonWidget.builder(Text.literal(I18n.t("Поиск предмета...")),
                b -> this.client.setScreen(new ContainerSearchScreen(this)))
                .dimensions(x0, 0, wd(K_SEARCH), 20).build(), K_SEARCH);
        slider("Радиус подсветки", I18n.t(" бл."), ClickerConfig.MIN_RADIUS, ClickerConfig.MAX_RADIUS,
                ClickerConfig.searchRadius, v -> ClickerConfig.searchRadius = v);

        section("Чат");
        toggle("Копирование строки (Ctrl+ЛКМ)", () -> ClickerConfig.chatCopy,
                () -> ClickerConfig.chatCopy = !ClickerConfig.chatCopy, -1);
        toggle("Кнопки после скриншота", () -> ClickerConfig.screenshotButtons,
                () -> ClickerConfig.screenshotButtons = !ClickerConfig.screenshotButtons, -1);

        section("Интерфейс");
        row(ButtonWidget.builder(Text.literal(I18n.t("Макросы...") + " " + onOff(ClickerConfig.macrosEnabled)),
                b -> this.client.setScreen(new MacroScreen(this)))
                .dimensions(x0, 0, wd(K_MACROS), 20).build(), K_MACROS);
        row(ButtonWidget.builder(Text.literal(I18n.t("Быстрое использование...") + " (" + QuickUseConfig.binds.size() + ")"),
                b -> this.client.setScreen(new QuickUseScreen(this)))
                .dimensions(x0, 0, wd(-1), 20).build(), -1);
        row(ButtonWidget.builder(Text.literal(I18n.t("Показы еды...") + " " + onOff(ClickerConfig.foodHud)),
                b -> this.client.setScreen(new FoodHudScreen(this)))
                .dimensions(x0, 0, wd(K_FOOD), 20).build(), K_FOOD);
        row(ButtonWidget.builder(Text.literal(I18n.t("Прочность на экране (броня / руки)...") + " " + onOff(ClickerConfig.armorHud)),
                b -> this.client.setScreen(new ArmorHudScreen(this)))
                .dimensions(x0, 0, wd(K_ARMOR), 20).build(), K_ARMOR);

        section("Управление");
        labelRow("Открыть это меню", K_SETTINGS);

        contentH = cursor;

        doneButton = ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build();
        addDrawableChild(doneButton);

        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        updatePositions();
    }

    private int maxScroll() {
        return Math.max(0, contentH + 8 - (viewBottom - viewTop));
    }

    private void updatePositions() {
        for (Entry e : entries) {
            int ey = viewTop + 4 + e.y - scroll;
            if (e.a != null) e.a.setY(ey);
            if (e.b != null) e.b.setY(ey);
        }
    }

    private static String onOff(boolean on) {
        return "[" + (on ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ")) + "]";
    }

    private static Text shulkerModeText() {
        return Text.literal(I18n.t("Шалкер: ") + (ClickerConfig.shulkerSum ? I18n.t("сумма по предметам") : I18n.t("по слотам")));
    }

    private ButtonWidget toggleButton(int x, int y, int w, int h, String label,
                                      BooleanSupplier state, Runnable flip) {
        return ButtonWidget.builder(toggleText(label, state.getAsBoolean()), b -> {
            flip.run();
            b.setMessage(toggleText(label, state.getAsBoolean()));
        }).dimensions(x, y, w, h).build();
    }

    private static Text toggleText(String label, boolean on) {
        return Text.literal(label + ": " + (on ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ")));
    }

    // ---------- Ввод ----------

    private void bind(InputUtil.Key key) {
        UniversalMod.keys()[listening].setBoundKey(key);
        KeyBinding.updateKeysByCode();
        this.client.options.write();
        listening = -1;
        refreshKeys();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening >= 0) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                bind(InputUtil.UNKNOWN_KEY);   // Esc = снять клавишу
            } else {
                bind(InputUtil.fromKeyCode(input));
            }
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listening >= 0) {
            bind(InputUtil.Type.MOUSE.createFromCode(click.button()));
            return true;
        }
        // Вне области списка (заголовок / кнопка «Готово») прокручиваемые элементы не нажимаются
        if (lastMouseY < viewTop || lastMouseY > viewBottom) {
            return doneButton.mouseClicked(click, doubled);
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(verticalAmount * 22)));
        updatePositions();
        return true;
    }

    // ---------- Отрисовка ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        lastMouseY = mouseY;
        updatePositions();
        super.render(context, mouseX, mouseY, delta);

        int left = x0 - 6;
        int right = x0 + cw + 6;
        context.fill(left, viewTop, right, viewBottom, 0x55000000);
        context.enableScissor(left, viewTop, right, viewBottom);
        for (Entry e : entries) {
            int ey = viewTop + 4 + e.y - scroll;
            if (ey + e.h < viewTop || ey > viewBottom) continue;
            if (e.header != null) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(e.header), x0, ey + 3, 0xFFFFD866);
                context.fill(x0, ey + 14, x0 + cw, ey + 15, 0x44FFFFFF);
            }
            if (e.label != null) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(e.label), x0, ey + 6, 0xFFFFFFFF);
            }
            if (e.a != null) e.a.render(context, mouseX, mouseY, delta);
            if (e.b != null) e.b.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();

        // Полоса прокрутки
        int max = maxScroll();
        if (max > 0) {
            int viewH = viewBottom - viewTop;
            int thumb = Math.max(20, viewH * viewH / (contentH + 8));
            int ty = viewTop + (int) ((viewH - thumb) * (scroll / (double) max));
            context.fill(right + 2, viewTop, right + 5, viewBottom, 0x33FFFFFF);
            context.fill(right + 2, ty, right + 5, ty + thumb, 0xAAFFFFFF);
        }

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        ClickerConfig.save();
        this.client.setScreen(parent);
    }

    /** Слайдер целого значения от min до max. */
    static class ValueSlider extends SliderWidget {
        private final String label;
        private final String suffix;
        private final int min;
        private final int max;
        private final IntConsumer setter;

        ValueSlider(int x, int y, int w, int h, String label, String suffix,
                    int min, int max, int value, IntConsumer setter) {
            super(x, y, w, h, Text.empty(), (value - min) / (double) (max - min));
            this.label = label;
            this.suffix = suffix;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private String[] names;

        void setNames(String[] names) {
            this.names = names;
            updateMessage();
        }

        private int current() {
            return min + (int) Math.round(this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            String shown = names != null && current() - min < names.length ? names[current() - min] : current() + suffix;
            setMessage(Text.literal(label + ": " + shown));
        }

        @Override
        protected void applyValue() {
            setter.accept(current());
        }
    }
}
