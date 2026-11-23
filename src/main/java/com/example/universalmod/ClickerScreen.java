package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
            K_REFILL_H = 23, K_REFILL_O = 24, K_INVIS = 25, K_FRAMES = 26, K_TARGET = 27, K_MODS = 28, K_WP_ADD = 29, K_WP_MENU = 30, K_RULER = 31, K_RULER_CLEAR = 32,
            K_SW_SPEAR = 33, K_SW_MACE = 34, K_SW_ELYTRA = 35, K_HITBOX = 36, K_CRYSTAL = 37, K_TRAP = 38,
            // Номера совпадают с порядком в UniversalMod.allKeys — там новые клавиши только дописываются в конец
            K_FARM = 39, K_ACCURATE = 40, K_CHEST_ESP = 41, K_FREECAM = 42,
            K_SCHEM_MODE = 43, K_SCHEM_SAVE = 44, K_SCHEM_ROT = 45, K_SCHEM_MENU = 46, K_SCHEM_BUILD = 47,
            K_TRAJ = 48, K_SHIELD = 49, K_LAVA = 50, K_WATER = 51, K_SCHEM_LIST = 52,
            K_VALUE_MARK = 53, K_VALUE_TAKE = 54, K_VALUE_MENU = 55,
            K_WORLDMAP = 56, K_MINIMAP = 57, K_MM_ZOOM = 58, K_MM_IN = 59, K_MM_OUT = 60, K_HELPER = 61, K_REROLL = 62;

    private static class Entry {
        int y;
        int h;
        String header;
        String key;
        List<net.minecraft.text.OrderedText> lines;
        String label;
        ClickableWidget a;
        ClickableWidget b;
        int grp;
        int gap;
        boolean isHeader;
        boolean hit;      // найдено поиском само (а не за компанию с разделом)
        int tabIdx = -1;  // вкладка раздела — показываем при поиске
    }

    /** Тексты настроек (название + описание) по группам — для поиска по описанию. */
    private final List<StringBuilder> groupText = new ArrayList<>();
    private int curGrp = 0;

    private int newGroup() {
        groupText.add(new StringBuilder());
        curGrp = groupText.size() - 1;
        return curGrp;
    }

    private void addText(String t) {
        if (t == null || groupText.isEmpty()) return;
        groupText.get(curGrp).append(' ').append(t.toLowerCase(Locale.ROOT));
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
    /** Развёрнутые разделы (по умолчанию все свёрнуты). Помним, пока запущена игра. */
    private static final java.util.Set<String> OPEN = new java.util.HashSet<>();
    private boolean collapsed = false;
    private boolean prevCollapsed = false;

    /** Вкладки (классы настроек): не раскрываются, переключают, какие разделы показаны. Помним, пока запущена игра. */
    private static final String[] TABS = {"Прочее", "Авто", "ПвП", "Мир", "Инвентарь", "Вид", "Схемы", "Карта"};
    private static int tab = -1; // берётся из настроек при первом открытии
    private final List<ButtonWidget> tabButtons = new ArrayList<>();

    /** Поиск по разделам: при непустом запросе вкладки не учитываются, показываются подходящие разделы. */
    private static String searchQuery = "";
    private TextFieldWidget searchField;
    private boolean keepFocus = false;

    private static boolean sectionMatches(String title, String q) {
        String d = Descs.get("\u00A7" + title);
        return SearchMatcher.matches(I18n.t(title) + " " + title + " " + (d == null ? "" : d), q);
    }

    /** К какой вкладке относится раздел. */
    private static int tabOf(String title) {
        switch (title) {
            case "Автокликер":
            case "Авто-копалка (зажать ЛКМ)":
            case "Быстрые клики":
            case "Блоки":
                return 1;
            case "Схемы (как Litematica)":
                return 6;
            case "Миникарта":
            case "Карта мира":
                return 7;
            case "Траектория снарядов":
            case "Авто-снос щита":
            case "Вёдра":
            case "Жемчуг и хорус в ловушке":
            case "Прочность брони цели":
            case "Подсветка хитбокса цели":
            case "Авто-кристалл":
            case "Смена предмета в хотбаре":
            case "Частицы невидимости":
                return 2;
            case "Жители и зачарования":
            case "Метка смерти":
            case "Метки":
            case "Линейка":
            case "Невидимые блоки":
            case "Рамки блоков":
            case "Контур блока":
            case "Поиск в контейнерах":
                return 3;
            case "Ценность предметов":
            case "Счётчик предметов":
            case "Автозаполнение":
            case "Перемещение предметов":
            case "Сортировка":
            case "Шалкеры":
                return 4;
            case "Координаты":
            case "Эффекты и тотемы":
            case "Яркость":
            case "Камера":
            case "Зум":
            case "Интерфейс":
                return 5;
            default:   // Язык, Аккаунт, Моды, Чат, Управление
                return 0;
        }
    }

    public ClickerScreen(Screen parent) {
        super(Text.literal("Universal Mod"));
        this.parent = parent;
        if (tab < 0) {
            tab = Math.max(0, Math.min(TABS.length - 1, ClickerConfig.settingsTab));
            OPEN.addAll(ClickerConfig.settingsOpen);
        }
    }

    private int wd(int keyIdx) {
        return keyIdx >= 0 ? cw - KW - 4 : cw;
    }

    private void section(String title) {
        String q = searchQuery.trim().toLowerCase(Locale.ROOT);
        boolean searching = !q.isEmpty();
        if (!searching && tabOf(title) != tab) {
            // раздел из другой вкладки: не показываем ни заголовок, ни настройки
            collapsed = true;
            return;
        }
        if (cursor > 0) cursor += prevCollapsed ? 2 : 8;
        Entry e = new Entry();
        e.y = cursor;
        e.h = 16;
        e.key = title;
        e.header = I18n.t(title);
        e.isHeader = true;
        e.tabIdx = tabOf(title);
        e.grp = newGroup();
        addText(title);
        addText(I18n.t(title));
        addText(I18n.t(TABS[tabOf(title)]));
        entries.add(e);
        cursor += 18;
        collapsed = !searching && !OPEN.contains(title);   // найденные по запросу разделы всегда раскрыты
        prevCollapsed = collapsed;
        desc("\u00A7" + title);
    }

    /** Описание из Descs под разделом (ключ «§…») или под настройкой. */
    private void desc(String key) {
        if (collapsed) return;
        String t = Descs.get(key);
        boolean sec = key.startsWith("\u00A7");
        if (!sec) addText(I18n.t(key));
        addText(t);
        if (t == null) return;
        Entry e = new Entry();
        e.grp = curGrp;
        if (!sec) cursor -= 2;
        e.y = cursor;
        e.lines = this.textRenderer.wrapLines(Text.literal(t), cw);
        e.h = e.lines.size() * 9 + 1;
        entries.add(e);
        cursor += e.h + (sec ? 4 : 5);
    }

    /** Мелкая подсказка под настройками раздела. */
    private void note(String text) {
        if (collapsed) return;
        addText(I18n.t(text));
        Entry e = new Entry();
        e.grp = curGrp;
        e.y = cursor;
        e.lines = this.textRenderer.wrapLines(Text.literal(I18n.t(text)), cw);
        e.h = e.lines.size() * 9 + 2;
        entries.add(e);
        cursor += e.h + 4;
    }

    private void row(ClickableWidget w, int keyIdx) {
        if (collapsed) return;
        Entry e = new Entry();
        e.y = cursor;
        e.h = 20;
        e.a = w;
        e.grp = newGroup();
        addText(w.getMessage().getString());
        if (keyIdx >= 0) {
            e.b = keyButton(keyIdx);
        }
        entries.add(e);
        cursor += ROW;
    }

    private void labelRow(String label, int keyIdx) {
        if (collapsed) return;
        Entry e = new Entry();
        e.y = cursor;
        e.h = 20;
        e.label = I18n.t(label);
        e.grp = newGroup();
        addText(e.label);
        e.b = keyButton(keyIdx);
        entries.add(e);
        cursor += ROW;
        desc(label);
    }

    private void toggle(String label, BooleanSupplier state, Runnable flip, int keyIdx) {
        row(toggleButton(x0, 0, wd(keyIdx), 20, I18n.t(label), state, flip), keyIdx);
        desc(label);
    }

    private void slider(String label, String suffix, int min, int max, int val, IntConsumer set) {
        row(new ValueSlider(x0, 0, cw, 20, I18n.t(label), suffix, min, max, val, set), -1);
        desc(label);
    }

    /** Ползунок с названиями вариантов вместо чисел. */
    private void sliderNamed(String label, String[] names, int val, IntConsumer set) {
        String[] tr = new String[names.length];
        for (int i = 0; i < tr.length; i++) tr[i] = I18n.t(names[i]);
        ValueSlider sl = new ValueSlider(x0, 0, cw, 20, I18n.t(label), "", 0, names.length - 1,
                Math.max(0, Math.min(names.length - 1, val)), set);
        sl.setNames(tr);
        row(sl, -1);
        desc(label);
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
        cw = Math.min(380, this.width - 30);
        x0 = (this.width - cw) / 2;
        viewTop = 54;
        viewBottom = this.height - 34;
        tabButtons.clear();
        int tabsW = Math.min(this.width - 20, 540);
        int tabX = (this.width - tabsW) / 2;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            int bx = tabX + tabsW * i / TABS.length;
            int bw = tabX + tabsW * (i + 1) / TABS.length - bx - 2;
            String name = I18n.t(TABS[i]);
            ButtonWidget tb = ButtonWidget.builder(
                            Text.literal(i == tab ? "\u00A7e" + name : name),
                            b -> {
                                tab = idx;
                                ClickerConfig.settingsTab = idx;
                                keepFocus = false;
                                scroll = 0;
                                this.clearAndInit();
                            })
                    .dimensions(bx, 28, bw, 20).build();
            tb.active = i != tab;
            tabButtons.add(tb);
            addDrawableChild(tb);
        }

        int sfW = Math.min(300, this.width - 200);
        searchField = new TextFieldWidget(this.textRenderer, this.width / 2 - sfW / 2 + 8, 6, sfW - 8, 16, Text.literal(""));
        searchField.setMaxLength(48);
        searchField.setPlaceholder(Text.literal(I18n.t("Поиск: контур, красный, щит, броня...")));
        searchField.setText(searchQuery);
        searchField.setChangedListener(t -> {
            if (t.equals(searchQuery)) return;
            searchQuery = t;
            scroll = 0;
            keepFocus = true;
            this.clearAndInit();
        });
        addDrawableChild(searchField);
        if (keepFocus) setInitialFocus(searchField);
        cursor = 0;
        collapsed = false;
        prevCollapsed = false;
        groupText.clear();
        curGrp = 0;

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

        section("Авто-копалка (зажать ЛКМ)");
        toggle("Следить за прочностью инструмента", () -> ClickerConfig.digEnabled,
                () -> ClickerConfig.digEnabled = !ClickerConfig.digEnabled, -1);
        slider("Порог прочности", " %", 1, 90, Math.max(1, Math.min(90, ClickerConfig.digLowPercent)),
                v -> ClickerConfig.digLowPercent = v);
        sliderNamed("При низкой прочности", AutoDig.LOW_ACTIONS, ClickerConfig.digLowAction,
                v -> ClickerConfig.digLowAction = v);
        sliderNamed("При поломке инструмента", AutoDig.BREAK_ACTIONS, ClickerConfig.digBreakAction,
                v -> ClickerConfig.digBreakAction = v);
        note("Работает, пока включено «Зажать ЛКМ». Когда прочность упадёт ниже порога — выключить зажатие или взять следующий слот хотбара с целым инструментом. При поломке — остановиться или перейти на следующий слот.");

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
        toggle("Вода: быстрый ПКМ", () -> ClickerConfig.waterFast,
                () -> ClickerConfig.waterFast = !ClickerConfig.waterFast, -1);

        section("Шалкеры");
        toggle("Просмотр шалкера", () -> ClickerConfig.shulkerPreview,
                () -> ClickerConfig.shulkerPreview = !ClickerConfig.shulkerPreview, K_SHULKER);
        row(ButtonWidget.builder(shulkerModeText(), b -> {
            ClickerConfig.shulkerSum = !ClickerConfig.shulkerSum;
            b.setMessage(shulkerModeText());
        }).dimensions(x0, 0, wd(K_SHULKER_MODE), 20).build(), K_SHULKER_MODE);
        desc("Шалкер: режим");
        toggle("Шалкер: сетка с предметами", () -> ClickerConfig.shulkerVisual,
                () -> ClickerConfig.shulkerVisual = !ClickerConfig.shulkerVisual, -1);
        toggle("Пометка «переименован на наковальне»", () -> ClickerConfig.shulkerRenamedNote,
                () -> ClickerConfig.shulkerRenamedNote = !ClickerConfig.shulkerRenamedNote, -1);

        section("Яркость");
        toggle("Фулбрайт", () -> ClickerConfig.fullbright,
                () -> ClickerConfig.fullbright = !ClickerConfig.fullbright, K_FULLBRIGHT);

        section("Камера");
        labelRow("Осмотр камеры (зажать)", K_FREELOOK);
        toggle("Свободная камера", () -> ClickerConfig.freecam, () -> ClickerConfig.freecam = !ClickerConfig.freecam, K_FREECAM);
        slider("Скорость камеры", " бл/с", 1, 100, Math.max(1, Math.min(100, Math.round(ClickerConfig.freecamSpeed))),
                v -> ClickerConfig.freecamSpeed = v);
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
        toggle("Контур невидимых блоков", () -> ClickerConfig.invisOutline,
                () -> ClickerConfig.invisOutline = !ClickerConfig.invisOutline, -1);
        sliderNamed("Цвет: барьер", Waypoints.COLOR_NAMES, ClickerConfig.invisBarrierColor,
                v -> ClickerConfig.invisBarrierColor = v);
        sliderNamed("Цвет: структурная пустота", Waypoints.COLOR_NAMES, ClickerConfig.invisVoidColor,
                v -> ClickerConfig.invisVoidColor = v);
        sliderNamed("Цвет: блок света", Waypoints.COLOR_NAMES, ClickerConfig.invisLightColor,
                v -> ClickerConfig.invisLightColor = v);

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
        toggle("Сплошной контур вместо частиц", () -> ClickerConfig.frameSolid,
                () -> ClickerConfig.frameSolid = !ClickerConfig.frameSolid, -1);
        sliderNamed("Цвет: подозрительный песок", Waypoints.COLOR_NAMES, ClickerConfig.frameSandColor,
                v -> ClickerConfig.frameSandColor = v);
        sliderNamed("Цвет: подозрительный гравий", Waypoints.COLOR_NAMES, ClickerConfig.frameGravelColor,
                v -> ClickerConfig.frameGravelColor = v);
        sliderNamed("Цвет: светокамень", Waypoints.COLOR_NAMES, ClickerConfig.frameGlowColor,
                v -> ClickerConfig.frameGlowColor = v);
        sliderNamed("Цвет: рыхлый снег", Waypoints.COLOR_NAMES, ClickerConfig.frameSnowColor,
                v -> ClickerConfig.frameSnowColor = v);
        slider("Радиус рамок", " бл.", 4, 32, Math.max(4, Math.min(32, ClickerConfig.frameRadius)),
                v -> ClickerConfig.frameRadius = v);

        section("Сундуки сквозь стены");
        toggle("Показывать сундуки сквозь стены", () -> ClickerConfig.chestEsp,
                () -> ClickerConfig.chestEsp = !ClickerConfig.chestEsp, K_CHEST_ESP);
        toggle("Кроме сундуков: печи, воронки, раздатчики", () -> ClickerConfig.chestEspAll,
                () -> ClickerConfig.chestEspAll = !ClickerConfig.chestEspAll, -1);
        toggle("Подпись: что за блок и расстояние", () -> ClickerConfig.chestEspText,
                () -> ClickerConfig.chestEspText = !ClickerConfig.chestEspText, -1);
        slider("Радиус поиска сундуков", " бл.", 16, 256, Math.max(16, Math.min(256, ClickerConfig.chestEspRadius)),
                v -> ClickerConfig.chestEspRadius = v);

        section("Контур блока");
        toggle("Свой контур выбранного блока", () -> ClickerConfig.targetOutline,
                () -> ClickerConfig.targetOutline = !ClickerConfig.targetOutline, K_TARGET);
        sliderNamed("Цвет контура", new String[]{"Светло-серый", "Красный", "Зелёный", "Синий"},
                ClickerConfig.targetOutlineColor, v -> ClickerConfig.targetOutlineColor = v);

        section("Частицы невидимости");
        sliderNamed("Частицы зелья невидимости", InvisibleParticles.NAMES, ClickerConfig.invisParticle,
                v -> ClickerConfig.invisParticle = v);
        slider("Частота частиц у невидимых", I18n.t(" в сек."), 1, 40, Math.max(1, Math.min(40, ClickerConfig.invisRate)),
                v -> ClickerConfig.invisRate = v);
        toggle("Частицы невидимости у мобов", () -> ClickerConfig.invisMobs,
                () -> ClickerConfig.invisMobs = !ClickerConfig.invisMobs, -1);

        section("Автозаполнение");
        toggle("Автозаполнение хотбара", () -> ClickerConfig.refillHotbar,
                () -> ClickerConfig.refillHotbar = !ClickerConfig.refillHotbar, K_REFILL_H);
        toggle("Автозаполнение второй руки", () -> ClickerConfig.refillOffhand,
                () -> ClickerConfig.refillOffhand = !ClickerConfig.refillOffhand, K_REFILL_O);
        slider("Пополнять, если меньше", " шт.", 1, 32, Math.max(1, Math.min(32, ClickerConfig.refillMin)),
                v -> ClickerConfig.refillMin = v);

        section("Перемещение предметов");
        toggle("Колёсико над слотом переносит предметы", () -> ClickerConfig.slotWheel,
                () -> ClickerConfig.slotWheel = !ClickerConfig.slotWheel, -1);
        toggle("Shift + ЛКМ: провести по слотам", () -> ClickerConfig.slotDrag,
                () -> ClickerConfig.slotDrag = !ClickerConfig.slotDrag, -1);
        toggle("Ctrl + Shift + клик: все такие же", () -> ClickerConfig.slotMatchAll,
                () -> ClickerConfig.slotMatchAll = !ClickerConfig.slotMatchAll, -1);

        section("Жители и зачарования");
        row(ButtonWidget.builder(Text.literal(I18n.t("Открыть помощник...")),
                        b -> this.client.setScreen(new TradeHelperScreen(this)))
                .dimensions(x0, 0, wd(K_HELPER), 20).build(), K_HELPER);
        labelRow("Библиотекарь: авто-перебор", K_REROLL);
        slider("Предел попыток перебора", "", 50, 2000, Math.max(50, Math.min(2000, ClickerConfig.rerollMax)), v -> ClickerConfig.rerollMax = v);
        toggle("Трейды жителей над головой", () -> ClickerConfig.villagerHeads,
                () -> ClickerConfig.villagerHeads = !ClickerConfig.villagerHeads, -1);
        toggle("Возможные трейды следующих уровней", () -> ClickerConfig.villagerNext,
                () -> ClickerConfig.villagerNext = !ClickerConfig.villagerNext, -1);
        toggle("Подсказки стола зачарований", () -> ClickerConfig.enchantHelper,
                () -> ClickerConfig.enchantHelper = !ClickerConfig.enchantHelper, -1);
        toggle("Подсказки наковальни", () -> ClickerConfig.anvilHelper,
                () -> ClickerConfig.anvilHelper = !ClickerConfig.anvilHelper, -1);

        section("Метки");
        row(ButtonWidget.builder(Text.literal(I18n.t("Меню меток...")),
                b -> this.client.setScreen(new WaypointScreen(this)))
                .dimensions(x0, 0, wd(K_WP_MENU), 20).build(), K_WP_MENU);
        desc("Меню меток...");
        labelRow("Поставить метку здесь", K_WP_ADD);
        toggle("Показывать метки на экране", () -> ClickerConfig.wpShow,
                () -> ClickerConfig.wpShow = !ClickerConfig.wpShow, -1);
        toggle("Значки меток прямо в мире", () -> ClickerConfig.wpWorld,
                () -> ClickerConfig.wpWorld = !ClickerConfig.wpWorld, -1);
        sliderNamed("Цвет новой метки", Waypoints.COLOR_NAMES, ClickerConfig.wpNewColor,
                v -> ClickerConfig.wpNewColor = v);
        slider("Радиус чанков спавна", " чанк.", 0, 10, Math.max(0, Math.min(10, ClickerConfig.spawnRadius)),
                v -> ClickerConfig.spawnRadius = v);

        section("Линейка");
        labelRow("Поставить точку линейки", K_RULER);
        labelRow("Очистить линейку", K_RULER_CLEAR);
        toggle("Показывать линейку", () -> ClickerConfig.rulerShow,
                () -> ClickerConfig.rulerShow = !ClickerConfig.rulerShow, -1);
        sliderNamed("Цвет линейки", Waypoints.COLOR_NAMES, ClickerConfig.rulerColor,
                v -> ClickerConfig.rulerColor = v);
        toggle("Стрелки внутрь (>—<)", () -> ClickerConfig.rulerArrowsIn,
                () -> ClickerConfig.rulerArrowsIn = !ClickerConfig.rulerArrowsIn, -1);

        section("Смена предмета в хотбаре");
        labelRow("Копьё: взять из хотбара", K_SW_SPEAR);
        toggle("Копьё: бить ЛКМ по цели", () -> ClickerConfig.swapSpearAuto,
                () -> ClickerConfig.swapSpearAuto = !ClickerConfig.swapSpearAuto, -1);
        toggle("Копьё: режим свапа (ждать цель)", () -> ClickerConfig.swapSpearMode,
                () -> ClickerConfig.swapSpearMode = !ClickerConfig.swapSpearMode, -1);
        toggle("Копьё: свап без цели (рывок)", () -> ClickerConfig.swapSpearLunge,
                () -> ClickerConfig.swapSpearLunge = !ClickerConfig.swapSpearLunge, -1);
        labelRow("Булава: взять из хотбара", K_SW_MACE);
        toggle("Булава: бить ЛКМ по цели", () -> ClickerConfig.swapMaceAuto,
                () -> ClickerConfig.swapMaceAuto = !ClickerConfig.swapMaceAuto, -1);
        toggle("Булава: режим свапа (ждать цель)", () -> ClickerConfig.swapMaceMode,
                () -> ClickerConfig.swapMaceMode = !ClickerConfig.swapMaceMode, -1);
        labelRow("Элитры: взять из хотбара", K_SW_ELYTRA);
        toggle("Элитры: сразу надеть", () -> ClickerConfig.swapElytraAuto,
                () -> ClickerConfig.swapElytraAuto = !ClickerConfig.swapElytraAuto, -1);
        toggle("Вернуть прошлый слот после свапа", () -> ClickerConfig.swapReturn,
                () -> ClickerConfig.swapReturn = !ClickerConfig.swapReturn, -1);
        slider("Ждать цель в прицеле", I18n.t(" сек."), HotbarSwap.MIN_WAIT, HotbarSwap.MAX_WAIT,
                Math.max(HotbarSwap.MIN_WAIT, Math.min(HotbarSwap.MAX_WAIT, ClickerConfig.swapWait)),
                v -> ClickerConfig.swapWait = v);
        toggle("Ждать перезарядку оружия", () -> ClickerConfig.swapWaitCooldown,
                () -> ClickerConfig.swapWaitCooldown = !ClickerConfig.swapWaitCooldown, -1);

        section("Жемчуг и хорус в ловушке");
        toggle("Бросать жемчуг и есть хорус, даже если прицел на верстаке, калитке и т.п.", () -> ClickerConfig.trapUse,
                () -> ClickerConfig.trapUse = !ClickerConfig.trapUse, K_TRAP);
        note("Только для жемчуга и хоруса в руке: клик по блоку или по существу (стойка для брони, рамка, житель, лодка и т.п.) не открывает его, а использует предмет. Сундуки и верстаки с жемчугом в руке при этом не откроются.");

        section("Прочность брони цели");
        toggle("Показывать прочность брони игрока в прицеле", () -> ClickerConfig.targetArmorHud,
                () -> ClickerConfig.targetArmorHud = !ClickerConfig.targetArmorHud, -1);

        section("Подсветка хитбокса цели");
        toggle("Подсвечивать хитбокс, когда можно бить", () -> ClickerConfig.hitboxHighlight,
                () -> ClickerConfig.hitboxHighlight = !ClickerConfig.hitboxHighlight, K_HITBOX);
        toggle("Все хитбоксы в досягаемости (не только в прицеле)", () -> ClickerConfig.hitboxAll,
                () -> ClickerConfig.hitboxAll = !ClickerConfig.hitboxAll, -1);
        sliderNamed("Цвет хитбокса", Waypoints.COLOR_NAMES, ClickerConfig.hitboxColor,
                v -> ClickerConfig.hitboxColor = v);

        section("Схемы (как Litematica)");
        toggle("Стрела — инструмент схем", () -> ClickerConfig.schemTool,
                () -> ClickerConfig.schemTool = !ClickerConfig.schemTool, -1);
        note("Предмет-инструмент в руке, Shift+1…5 — режим. 1: ПКМ/ЛКМ — точки, Ctrl+Shift — сохранить. 2: ПКМ — поставить схему, ЛКМ — другая, Ctrl+ПКМ — поворот, Ctrl+ЛКМ — убрать. 3 (креатив): Shift+ПКМ — вставить всё. 4: ПКМ — постройка в досягаемости. 5: ПКМ — бот сам ходит и строит всю схему из инвентаря.");
        row(ButtonWidget.builder(Text.literal(I18n.t("Инструмент: ") + new net.minecraft.item.ItemStack(SchematicTool.toolItem()).getName().getString()
                                + I18n.t(" — взять из руки")),
                        b -> {
                            SchematicTool.setToolFromHand(this.client);
                            this.clearAndInit();
                        })
                .dimensions(x0, 0, cw, 20).build(), -1);
        row(ButtonWidget.builder(Text.literal(I18n.t("Список схем...")),
                        b -> this.client.setScreen(new SchematicListScreen(this)))
                .dimensions(x0, 0, wd(K_SCHEM_LIST), 20).build(), K_SCHEM_LIST);
        row(ButtonWidget.builder(Text.literal(I18n.t("Материалы и замена блоков...")),
                        b -> this.client.setScreen(new SchematicScreen(this)))
                .dimensions(x0, 0, wd(K_SCHEM_MENU), 20).build(), K_SCHEM_MENU);
        labelRow("Схема: следующий режим", K_SCHEM_MODE);
        labelRow("Схема: сохранить выделение", K_SCHEM_SAVE);
        labelRow("Схема: повернуть", K_SCHEM_ROT);
        labelRow("Схема: вставить / автопостройка", K_SCHEM_BUILD);

        section("Траектория снарядов");
        toggle("Показывать траекторию", () -> ClickerConfig.trajectory,
                () -> ClickerConfig.trajectory = !ClickerConfig.trajectory, K_TRAJ);
        toggle("Показывать и от третьего лица", () -> ClickerConfig.trajectoryThirdPerson,
                () -> ClickerConfig.trajectoryThirdPerson = !ClickerConfig.trajectoryThirdPerson, -1);

        section("Авто-снос щита");
        toggle("Сносить щит топором", () -> ClickerConfig.shieldBreak,
                () -> ClickerConfig.shieldBreak = !ClickerConfig.shieldBreak, K_SHIELD);
        toggle("Вернуть прошлый слот после удара", () -> ClickerConfig.shieldBreakReturn,
                () -> ClickerConfig.shieldBreakReturn = !ClickerConfig.shieldBreakReturn, -1);

        section("Вёдра");
        toggle("Авто-лава под противника", () -> ClickerConfig.autoLava,
                () -> ClickerConfig.autoLava = !ClickerConfig.autoLava, K_LAVA);
        note("После удара по противнику, который не горит: 1 тик — ведро лавы в руку, 2 — лава под него, 3 — лава забирается, 4 — прежний слот.");
        toggle("Ватердроп (вода при падении)", () -> ClickerConfig.autoWater,
                () -> ClickerConfig.autoWater = !ClickerConfig.autoWater, K_WATER);
        note("При быстром падении вода ставится под себя перед землёй и забирается после приземления. Ведро воды должно быть в хотбаре.");

        section("Авто-кристалл");
        toggle("Кристалл на обсидиан", () -> ClickerConfig.autoCrystal,
                () -> ClickerConfig.autoCrystal = !ClickerConfig.autoCrystal, K_CRYSTAL);

        section("Моды");
        row(ButtonWidget.builder(Text.literal(I18n.t("Список модов...")),
                b -> this.client.setScreen(new ModListScreen(this)))
                .dimensions(x0, 0, wd(K_MODS), 20).build(), K_MODS);
        desc("Список модов...");
        toggle("Кнопка «Моды» в меню", () -> ClickerConfig.modsButton,
                () -> ClickerConfig.modsButton = !ClickerConfig.modsButton, -1);

        section("Миникарта");
        toggle("Показывать миникарту", () -> ClickerConfig.mmShow,
                () -> ClickerConfig.mmShow = !ClickerConfig.mmShow, K_MINIMAP);
        sliderNamed("Где на экране", Minimap.CORNERS, ClickerConfig.mmCorner, v -> ClickerConfig.mmCorner = v);
        slider("Размер миникарты", I18n.t(" пикс."), 48, 256, ClickerConfig.mmSize, v -> ClickerConfig.mmSize = v);
        slider("Отступ от края по X", "", 0, 100, Math.min(100, ClickerConfig.mmOffX), v -> ClickerConfig.mmOffX = v);
        slider("Отступ от края по Y", "", 0, 100, Math.min(100, ClickerConfig.mmOffY), v -> ClickerConfig.mmOffY = v);
        sliderNamed("Масштаб миникарты", Minimap.ZOOM_NAMES, ClickerConfig.mmZoom, v -> ClickerConfig.mmZoom = v);
        labelRow("Миникарта: масштаб", K_MM_ZOOM);
        labelRow("Миникарта: ближе", K_MM_IN);
        labelRow("Миникарта: дальше", K_MM_OUT);
        sliderNamed("Форма", Minimap.SHAPES, ClickerConfig.mmShape, v -> ClickerConfig.mmShape = v);
        toggle("Поворачивать вместе с игроком", () -> ClickerConfig.mmRotate,
                () -> ClickerConfig.mmRotate = !ClickerConfig.mmRotate, -1);
        sliderNamed("Пещерный режим", Minimap.CAVE, ClickerConfig.mmCave, v -> ClickerConfig.mmCave = v);
        toggle("Координаты под картой", () -> ClickerConfig.mmCoords,
                () -> ClickerConfig.mmCoords = !ClickerConfig.mmCoords, -1);
        toggle("Пересчёт Незер / Верхний мир", () -> ClickerConfig.mmConvert,
                () -> ClickerConfig.mmConvert = !ClickerConfig.mmConvert, -1);
        toggle("Название биома", () -> ClickerConfig.mmBiome,
                () -> ClickerConfig.mmBiome = !ClickerConfig.mmBiome, -1);
        toggle("Метки на миникарте", () -> ClickerConfig.mmWaypoints,
                () -> ClickerConfig.mmWaypoints = !ClickerConfig.mmWaypoints, -1);
        toggle("Игроки", () -> ClickerConfig.mmPlayers, () -> ClickerConfig.mmPlayers = !ClickerConfig.mmPlayers, -1);
        toggle("Враждебные мобы", () -> ClickerConfig.mmMobs, () -> ClickerConfig.mmMobs = !ClickerConfig.mmMobs, -1);
        toggle("Животные", () -> ClickerConfig.mmAnimals, () -> ClickerConfig.mmAnimals = !ClickerConfig.mmAnimals, -1);
        toggle("Предметы на земле", () -> ClickerConfig.mmItems, () -> ClickerConfig.mmItems = !ClickerConfig.mmItems, -1);
        toggle("Показывать, выше или ниже существо", () -> ClickerConfig.mmHeight,
                () -> ClickerConfig.mmHeight = !ClickerConfig.mmHeight, -1);
        toggle("Высокая детализация (чёткие пиксели, рельеф)", () -> ClickerConfig.mmDetail,
                () -> ClickerConfig.mmDetail = !ClickerConfig.mmDetail, -1);
        toggle("Значки: лица игроков, мобы, предметы", () -> ClickerConfig.mmIcons,
                () -> ClickerConfig.mmIcons = !ClickerConfig.mmIcons, -1);
        slider("Размер значков", I18n.t(" пикс."), 4, 16, ClickerConfig.mmIconSize, v -> ClickerConfig.mmIconSize = v);
        toggle("Число блоков выше/ниже", () -> ClickerConfig.mmHeightNum,
                () -> ClickerConfig.mmHeightNum = !ClickerConfig.mmHeightNum, -1);

        section("Карта мира");
        row(ButtonWidget.builder(Text.literal(I18n.t("Открыть карту мира...")),
                        b -> this.client.setScreen(new WorldMapScreen(this)))
                .dimensions(x0, 0, wd(K_WORLDMAP), 20).build(), K_WORLDMAP);
        toggle("Запоминать изученную местность", () -> ClickerConfig.mapRecord,
                () -> ClickerConfig.mapRecord = !ClickerConfig.mapRecord, -1);
        toggle("Открывать карту на себе", () -> ClickerConfig.mapCenterOnOpen,
                () -> ClickerConfig.mapCenterOnOpen = !ClickerConfig.mapCenterOnOpen, -1);

        section("Ценность предметов");
        row(ButtonWidget.builder(Text.literal(I18n.t("Настроить ценность...")),
                        b -> this.client.setScreen(new ValueScreen(this)))
                .dimensions(x0, 0, wd(K_VALUE_MENU), 20).build(), K_VALUE_MENU);
        labelRow("Ценность: отметить предмет", K_VALUE_MARK);
        labelRow("Ценность: забрать ценное", K_VALUE_TAKE);
        toggle("Забирать ценное при открытии сундука", () -> ClickerConfig.valueAuto,
                () -> ClickerConfig.valueAuto = !ClickerConfig.valueAuto, -1);
        toggle("Если не вышло — перенести курсором", () -> ClickerConfig.valueCursor,
                () -> ClickerConfig.valueCursor = !ClickerConfig.valueCursor, -1);
        slider("Повторов, если предмет не встал", "", 0, 10, ClickerConfig.valueRetries, v -> ClickerConfig.valueRetries = v);

        section("Сортировка");
        toggle("Кнопки сортировки в контейнере", () -> ClickerConfig.sortButtons,
                () -> ClickerConfig.sortButtons = !ClickerConfig.sortButtons, -1);
        toggle("Кнопки сортировки инвентаря", () -> ClickerConfig.sortInvButtons,
                () -> ClickerConfig.sortInvButtons = !ClickerConfig.sortInvButtons, -1);
        slider("Скорость сортировки", " кл./тик", 1, 20, ClickerConfig.sortSpeed, v -> ClickerConfig.sortSpeed = v);
        slider("Скорость выброса (✕)", " сл./тик", 1, 20, ClickerConfig.dropSpeed, v -> ClickerConfig.dropSpeed = v);
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
                () -> ClickerConfig.containerSearch = !ClickerConfig.containerSearch, -1);
        row(ButtonWidget.builder(Text.literal(I18n.t("Поиск предмета...")),
                b -> this.client.setScreen(new ContainerSearchScreen(this)))
                .dimensions(x0, 0, wd(K_SEARCH), 20).build(), K_SEARCH);
        desc("Поиск предмета...");
        slider("Радиус подсветки", I18n.t(" бл."), ClickerConfig.MIN_RADIUS, ClickerConfig.MAX_RADIUS,
                ClickerConfig.searchRadius, v -> ClickerConfig.searchRadius = v);

        section("Чат");
        toggle("Копирование строки (Ctrl+ЛКМ)", () -> ClickerConfig.chatCopy,
                () -> ClickerConfig.chatCopy = !ClickerConfig.chatCopy, -1);
        toggle("Кнопки после скриншота", () -> ClickerConfig.screenshotButtons,
                () -> ClickerConfig.screenshotButtons = !ClickerConfig.screenshotButtons, -1);

        section("Эффекты и тотемы");
        toggle("Показывать эффекты зелий с таймером", () -> ClickerConfig.effectsHud,
                () -> ClickerConfig.effectsHud = !ClickerConfig.effectsHud, -1);
        toggle("Показывать число тотемов", () -> ClickerConfig.effectsTotems,
                () -> ClickerConfig.effectsTotems = !ClickerConfig.effectsTotems, -1);
        sliderNamed("Положение эффектов", EffectsHud.POS_NAMES, ClickerConfig.effectsPos,
                v -> ClickerConfig.effectsPos = v);

        section("Метка смерти");
        toggle("Ставить метку там, где вы умерли", () -> ClickerConfig.deathMark,
                () -> ClickerConfig.deathMark = !ClickerConfig.deathMark, -1);

        section("Предупреждение о прочности");
        toggle("Предупреждать о низкой прочности брони и предмета в руке", () -> ClickerConfig.durWarn,
                () -> ClickerConfig.durWarn = !ClickerConfig.durWarn, -1);
        slider("Предупреждать при прочности ниже", "%", 1, 50, ClickerConfig.durWarnPercent,
                v -> ClickerConfig.durWarnPercent = v);

        section("Координаты");
        toggle("Показывать координаты и пересчёт Верхний мир / Незер", () -> ClickerConfig.coordsHud,
                () -> ClickerConfig.coordsHud = !ClickerConfig.coordsHud, -1);
        sliderNamed("Положение координат", EffectsHud.POS_NAMES, ClickerConfig.coordsPos,
                v -> ClickerConfig.coordsPos = v);

        section("Счётчик предметов");
        toggle("Показывать, сколько всего предмета из руки в инвентаре", () -> ClickerConfig.itemCounter,
                () -> ClickerConfig.itemCounter = !ClickerConfig.itemCounter, -1);

        section("Профили настроек");
        for (int i = 1; i <= 3; i++) {
            final int slot = i;
            button(I18n.t("Сохранить в профиль ") + slot, () -> {
                ClickerConfig.saveProfile(slot);
                this.clearAndInit();
            });
            button(I18n.t("Загрузить профиль ") + slot + (ClickerConfig.profileExists(slot) ? "" : " " + I18n.t("(пусто)")), () -> {
                if (ClickerConfig.loadProfile(slot)) this.clearAndInit();
            });
        }
        note("Профиль — копия всех настроек в папке config (universal_mod_profile_N.json). Файл можно передать другу или забрать на другой компьютер.");

        section("Интерфейс");
        row(ButtonWidget.builder(Text.literal(I18n.t("Макросы...") + " " + onOff(ClickerConfig.macrosEnabled)),
                b -> this.client.setScreen(new MacroScreen(this)))
                .dimensions(x0, 0, wd(K_MACROS), 20).build(), K_MACROS);
        desc("Макросы...");
        row(ButtonWidget.builder(Text.literal(I18n.t("Показы еды...") + " " + onOff(ClickerConfig.foodHud)),
                b -> this.client.setScreen(new FoodHudScreen(this)))
                .dimensions(x0, 0, wd(K_FOOD), 20).build(), K_FOOD);
        desc("Показы еды...");
        row(ButtonWidget.builder(Text.literal(I18n.t("Прочность на экране (броня / руки)...") + " " + onOff(ClickerConfig.armorHud)),
                b -> this.client.setScreen(new ArmorHudScreen(this)))
                .dimensions(x0, 0, wd(K_ARMOR), 20).build(), K_ARMOR);
        desc("Прочность на экране (броня / руки)...");

        section("Управление");
        labelRow("Открыть это меню", K_SETTINGS);

        finishLayout(searchQuery.trim().toLowerCase(Locale.ROOT));
        contentH = cursor;

        doneButton = ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build();
        addDrawableChild(doneButton);

        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        updatePositions();
    }

    /** Поиск по названиям и описаниям настроек: оставляем только подходящие настройки (или целые разделы). */
    private int found = 0;

    private void finishLayout(String q) {
        found = 0;
        int prevEnd = 0;
        for (Entry e : entries) {
            e.gap = e.y - prevEnd;
            prevEnd = e.y + e.h;
        }
        if (!q.isEmpty()) {
            List<Entry> kept = new ArrayList<>();
            int n = entries.size();
            int i = 0;
            while (i < n) {
                int j = i + 1;
                while (j < n && !entries.get(j).isHeader) j++;
                boolean secMatch = SearchMatcher.matches(groupText.get(entries.get(i).grp).toString(), q);
                List<Entry> part = new ArrayList<>();
                int k = i + 1;
                while (k < j) {
                    int g = entries.get(k).grp;
                    int m = k;
                    while (m < j && entries.get(m).grp == g) m++;
                    boolean own = SearchMatcher.matches(groupText.get(g).toString(), q);
                    if (own) for (Entry x : entries.subList(k, m)) x.hit = true;
                    if (secMatch || own) part.addAll(entries.subList(k, m));
                    k = m;
                }
                if (secMatch || !part.isEmpty()) {
                    found += part.isEmpty() ? 1 : (int) part.stream().filter(x -> x.a != null || x.label != null).count();
                    kept.add(entries.get(i));
                    kept.addAll(part);
                }
                i = j;
            }
            entries.clear();
            entries.addAll(kept);
            int end = 0;
            boolean first = true;
            for (Entry e : entries) {
                e.y = first ? 0 : end + e.gap;
                first = false;
                end = e.y + e.h;
            }
            cursor = end;
        }
        for (Entry e : entries) {
            if (e.a != null) addSelectableChild(e.a);
            if (e.b != null) addSelectableChild(e.b);
        }
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
            // поле поиска: важно сделать его «фокусом» окна, иначе клик проходит, а набирать текст нельзя
            if (searchField != null && searchField.isMouseOver(click.x(), click.y())) {
                this.setFocused(searchField);
                searchField.mouseClicked(click, doubled);
                return true;
            }
            for (ButtonWidget tb : tabButtons) {
                if (tb.mouseClicked(click, doubled)) return true;
            }
            return doneButton.mouseClicked(click, doubled);
        }
        if (click.button() == 0 && click.x() >= x0 && click.x() <= x0 + cw) {
            for (Entry e : entries) {
                if (e.key == null) continue;
                int ey = viewTop + 4 + e.y - scroll;
                if (click.y() >= ey && click.y() <= ey + 16) {
                    if (!OPEN.remove(e.key)) OPEN.add(e.key);
                    ClickerConfig.settingsOpen = new java.util.ArrayList<>(OPEN);
                    ClickerConfig.save();
                    this.clearAndInit();
                    return true;
                }
            }
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
        // шапка и панель списка
        context.fill(left - 1, viewTop - 1, right + 1, viewBottom + 1, 0xFF2E3742);
        context.fill(left, viewTop, right, viewBottom, 0xE0161B21);
        // подчёркивание активной вкладки
        if (!searchQuery.trim().isEmpty()) {
            // при поиске вкладки не важны — ничего не подчёркиваем
        } else if (tab >= 0 && tab < tabButtons.size()) {
            ButtonWidget tb = tabButtons.get(tab);
            context.fill(tb.getX() + 2, tb.getY() + tb.getHeight(), tb.getX() + tb.getWidth() - 2, tb.getY() + tb.getHeight() + 2, 0xFFFFD866);
        }
        context.enableScissor(left, viewTop, right, viewBottom);
        for (Entry e : entries) {
            int ey = viewTop + 4 + e.y - scroll;
            if (ey + e.h < viewTop || ey > viewBottom) continue;
            if (e.header != null) {
                boolean open = OPEN.contains(e.key);
                boolean hov = mouseX >= x0 && mouseX <= x0 + cw && mouseY >= ey && mouseY <= ey + 16
                        && mouseY >= viewTop && mouseY <= viewBottom;
                context.fill(x0 - 2, ey, x0 + cw + 2, ey + 16, hov ? 0x44FFD866 : 0x22FFFFFF);
                context.fill(x0 - 2, ey + 15, x0 + cw + 2, ey + 16, open ? 0xAAFFD866 : 0x44FFFFFF);
                net.minecraft.item.Item icon = sectionIcon(e.key);
                int tx = x0 + 2;
                if (icon != null) {
                    context.drawItem(new net.minecraft.item.ItemStack(icon), x0, ey);
                    tx = x0 + 19;
                }
                int chipRoom = !searchQuery.trim().isEmpty() && e.tabIdx >= 0
                        ? this.textRenderer.getWidth(I18n.t(TABS[e.tabIdx])) + 10 : 0;
                String head = this.textRenderer.trimToWidth(e.header, x0 + cw - 16 - tx - chipRoom);
                context.drawTextWithShadow(this.textRenderer, Text.literal(head), tx, ey + 4, 0xFFFFD866);
                context.drawTextWithShadow(this.textRenderer, Text.literal(open ? "\u25B2" : "\u25BC"),
                        x0 + cw - 10, ey + 4, 0xFFFFD866);
            }
            if (e.lines != null) {
                int ly = ey;
                for (net.minecraft.text.OrderedText line : e.lines) {
                    context.drawTextWithShadow(this.textRenderer, line, x0, ly, 0xFF9A9A9A);
                    ly += 9;
                }
            }
            if (e.label != null) {
                // подпись не должна залезать под кнопку клавиши справа
                int maxW = cw - (e.b != null ? KW + 8 : 0);
                context.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(e.label, maxW)),
                        x0, ey + 6, 0xFFFFFFFF);
            }
            if (e.a != null) e.a.render(context, mouseX, mouseY, delta);
            if (e.b != null) e.b.render(context, mouseX, mouseY, delta);
            boolean searching = !searchQuery.trim().isEmpty();
            if (searching && e.hit && !e.isHeader) {
                // найденная настройка — жёлтая полоска слева
                context.fill(x0 - 5, ey, x0 - 3, ey + e.h, 0xFFFFD866);
            }
            if (searching && e.isHeader && e.tabIdx >= 0) {
                String chip = I18n.t(TABS[e.tabIdx]);
                int cw2 = this.textRenderer.getWidth(chip) + 6;
                int chx = x0 + cw - 16 - cw2;
                context.fill(chx, ey + 2, chx + cw2, ey + 13, 0x66000000);
                context.drawTextWithShadow(this.textRenderer, Text.literal(chip), chx + 3, ey + 4, 0xFFBBBBBB);
            }
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

        int tw = this.textRenderer.getWidth(this.title);
        if (searchField != null) {
            int sx = searchField.getX();
            // значок лупы слева от поля
            context.drawTextWithShadow(this.textRenderer, Text.literal("\u2315"), sx - 11, 10, 0xFFAAAAAA);
            if (8 + tw < sx - 16) context.drawTextWithShadow(this.textRenderer, this.title, 8, 10, 0xFFFFD866);
            String q = searchQuery.trim();
            if (!q.isEmpty()) {
                String cnt = found == 0 ? I18n.t("ничего не найдено") : I18n.t("найдено: ") + found;
                int cx = sx + searchField.getWidth() + 8;
                if (cx + this.textRenderer.getWidth(cnt) < this.width - 4) {
                    context.drawTextWithShadow(this.textRenderer, Text.literal(cnt), cx, 10, found == 0 ? 0xFFFF6B6B : 0xFF5BD38A);
                }
            }
        } else {
            context.drawTextWithShadow(this.textRenderer, this.title, 8, 10, 0xFFFFD866);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        // шапка под поиском и вкладками
        context.fill(0, 0, this.width, viewTop - 4, 0x66101418);
        context.fill(0, viewTop - 5, this.width, viewTop - 4, 0x33FFFFFF);
    }

    /** Значок раздела — чтобы сразу было видно, про что он. */
    private static net.minecraft.item.Item sectionIcon(String t) {
        if (t == null) return null;
        return switch (t) {
            case "Язык" -> net.minecraft.item.Items.BOOK;
            case "Аккаунт" -> net.minecraft.item.Items.NAME_TAG;
            case "Автокликер" -> net.minecraft.item.Items.STONE_BUTTON;
            case "Авто-копалка (зажать ЛКМ)" -> net.minecraft.item.Items.IRON_PICKAXE;
            case "Быстрые клики" -> net.minecraft.item.Items.FEATHER;
            case "Блоки" -> net.minecraft.item.Items.GRASS_BLOCK;
            case "Шалкеры" -> net.minecraft.item.Items.SHULKER_BOX;
            case "Яркость" -> net.minecraft.item.Items.TORCH;
            case "Камера" -> net.minecraft.item.Items.ENDER_EYE;
            case "Невидимые блоки" -> net.minecraft.item.Items.BARRIER;
            case "Рамки блоков" -> net.minecraft.item.Items.SUSPICIOUS_SAND;
            case "Сундуки сквозь стены" -> net.minecraft.item.Items.CHEST;
            case "Контур блока" -> net.minecraft.item.Items.GLASS;
            case "Частицы невидимости" -> net.minecraft.item.Items.FERMENTED_SPIDER_EYE;
            case "Автозаполнение" -> net.minecraft.item.Items.HOPPER;
            case "Перемещение предметов" -> net.minecraft.item.Items.BUNDLE;
            case "Метки" -> net.minecraft.item.Items.COMPASS;
            case "Линейка" -> net.minecraft.item.Items.STICK;
            case "Смена предмета в хотбаре" -> net.minecraft.item.Items.MACE;
            case "Жемчуг и хорус в ловушке" -> net.minecraft.item.Items.ENDER_PEARL;
            case "Прочность брони цели" -> net.minecraft.item.Items.DIAMOND_CHESTPLATE;
            case "Подсветка хитбокса цели" -> net.minecraft.item.Items.TARGET;
            case "Схемы (как Litematica)" -> net.minecraft.item.Items.ARROW;
            case "Траектория снарядов" -> net.minecraft.item.Items.BOW;
            case "Авто-снос щита" -> net.minecraft.item.Items.SHIELD;
            case "Вёдра" -> net.minecraft.item.Items.WATER_BUCKET;
            case "Авто-кристалл" -> net.minecraft.item.Items.END_CRYSTAL;
            case "Моды" -> net.minecraft.item.Items.CRAFTING_TABLE;
            case "Ценность предметов" -> net.minecraft.item.Items.NETHER_STAR;
            case "Жители и зачарования" -> net.minecraft.item.Items.EMERALD;
            case "Миникарта" -> net.minecraft.item.Items.FILLED_MAP;
            case "Карта мира" -> net.minecraft.item.Items.CARTOGRAPHY_TABLE;
            case "Сортировка" -> net.minecraft.item.Items.BARREL;
            case "Зум" -> net.minecraft.item.Items.SPYGLASS;
            case "Поиск в контейнерах" -> net.minecraft.item.Items.SPYGLASS;
            case "Чат" -> net.minecraft.item.Items.WRITABLE_BOOK;
            case "Эффекты и тотемы" -> net.minecraft.item.Items.TOTEM_OF_UNDYING;
            case "Метка смерти" -> net.minecraft.item.Items.SKELETON_SKULL;
            case "Предупреждение о прочности" -> net.minecraft.item.Items.ANVIL;
            case "Координаты" -> net.minecraft.item.Items.MAP;
            case "Счётчик предметов" -> net.minecraft.item.Items.CLOCK;
            case "Профили настроек" -> net.minecraft.item.Items.WRITABLE_BOOK;
            case "Интерфейс" -> net.minecraft.item.Items.PAINTING;
            case "Управление" -> net.minecraft.item.Items.LEVER;
            default -> null;
        };
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
