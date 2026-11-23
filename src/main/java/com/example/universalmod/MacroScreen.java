package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Макросы — список карточек, всё видно сразу:
 *   [клавиша] [сообщение или /команда .............] [✕]
 *   [режим: один раз / повтор (вкл-выкл клавишей) / повтор, пока зажата] [интервал] [▶]
 * Всё сохраняется сразу.
 */
public class MacroScreen extends Screen {
    private static final int CARD_H = 50, CARD_GAP = 4;
    private static final int ACCENT = 0xFFB57BEE;

    private final Screen parent;
    private int first = 0;
    private int listening = -1;       // какой макрос ждёт клавишу
    private int confirmDelete = -1;
    private int px, py, pw, ph, listY, cards;
    private final CommandSuggest suggest = new CommandSuggest();
    private final java.util.List<TextFieldWidget> msgFields = new java.util.ArrayList<>();

    public MacroScreen(Screen parent) {
        super(Text.literal(I18n.t("Макросы")));
        this.parent = parent;
    }

    private static String onOff(boolean on) {
        return on ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ");
    }

    private static int modeOf(MacroBind m) {
        return !m.repeat ? 0 : (m.holdOnly ? 2 : 1);
    }

    private static String modeName(int mode) {
        return switch (mode) {
            case 0 -> I18n.t("Один раз");
            case 1 -> I18n.t("Повтор: вкл/выкл клавишей");
            default -> I18n.t("Повтор, пока зажата");
        };
    }

    private static String modeTip(int mode) {
        return switch (mode) {
            case 0 -> I18n.t("Нажали клавишу — сообщение отправилось один раз");
            case 1 -> I18n.t("Нажали — повтор пошёл с интервалом, нажали ещё раз — остановился");
            default -> I18n.t("Пока клавиша зажата, сообщение повторяется с интервалом");
        };
    }

    @Override
    protected void init() {
        pw = Math.min(460, this.width - 16);
        ph = this.height - 16;
        px = (this.width - pw) / 2;
        py = 8;
        listY = py + 56;
        cards = Math.max(1, (py + ph - 30 - listY) / (CARD_H + CARD_GAP));
        int total = MacroConfig.binds.size();
        first = Math.max(0, Math.min(first, Math.max(0, total - cards)));

        // верх: общий выключатель и «добавить»
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Макросы: ") + onOff(ClickerConfig.macrosEnabled)), b -> {
            ClickerConfig.macrosEnabled = !ClickerConfig.macrosEnabled;
            ClickerConfig.save();
            this.clearAndInit();
        }).dimensions(px + 8, py + 28, 140, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Включить или выключить все макросы сразу")))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("+ Новый макрос")), b -> {
            MacroBind nb = new MacroBind();
            nb.version = MacroBind.CURRENT_VERSION;
            MacroConfig.binds.add(nb);
            MacroConfig.save();
            first = Math.max(0, MacroConfig.binds.size() - cards);
            listening = MacroConfig.binds.size() - 1; // сразу просим нажать клавишу
            this.clearAndInit();
        }).dimensions(px + pw - 8 - 140, py + 28, 140, 20).build());

        int cw = pw - 16;
        msgFields.clear();
        for (int i = 0; i < cards; i++) {
            int idx = first + i;
            if (idx >= total) break;
            MacroBind m = MacroConfig.binds.get(idx);
            int x = px + 8, y = listY + i * (CARD_H + CARD_GAP);

            // строка 1: клавиша, текст, удалить
            int kx = x + 24;
            addDrawableChild(ButtonWidget.builder(listening == idx ? Text.literal("> ... <") : m.keyText(), b -> {
                listening = idx;
                this.clearAndInit();
            }).dimensions(kx, y + 4, 80, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Нажмите и затем нужную клавишу или кнопку мыши. Esc — убрать клавишу")))).build());
            TextFieldWidget tf = new TextFieldWidget(this.textRenderer, kx + 84, y + 4, cw - (kx - x) - 84 - 28, 20, Text.literal(""));
            tf.setMaxLength(256);
            tf.setText(m.text);
            tf.setPlaceholder(Text.literal(I18n.t("Сообщение или /команда (после / — подсказки)")));
            msgFields.add(tf);
            tf.setChangedListener(t -> {
                m.text = t;
                MacroConfig.save();
            });
            addDrawableChild(tf);
            boolean conf = confirmDelete == idx;
            addDrawableChild(ButtonWidget.builder(Text.literal(conf ? "?" : "✕"), b -> {
                if (confirmDelete != idx) {
                    confirmDelete = idx;
                } else {
                    MacroConfig.binds.remove(m);
                    MacroConfig.save();
                    confirmDelete = -1;
                    listening = -1;
                }
                this.clearAndInit();
            }).dimensions(x + cw - 24, y + 4, 22, 20).tooltip(Tooltip.of(Text.literal(conf
                    ? I18n.t("Нажмите ещё раз — удалить") : I18n.t("Удалить макрос")))).build());

            // строка 2: режим, интервал, запуск
            int mode = modeOf(m);
            addDrawableChild(ButtonWidget.builder(Text.literal(modeName(mode)), b -> {
                int nm = (modeOf(m) + 1) % 3;
                m.repeat = nm != 0;
                m.holdOnly = nm == 2;
                m.running = false;
                MacroConfig.save();
                this.clearAndInit();
            }).dimensions(kx, y + 27, 170, 20).tooltip(Tooltip.of(Text.literal(modeTip(mode)))).build());
            if (m.repeat) {
                TextFieldWidget iv = new TextFieldWidget(this.textRenderer, kx + 174, y + 27, 56, 20, Text.literal(""));
                iv.setMaxLength(7);
                iv.setTextPredicate(s -> s.matches("\\d*"));
                iv.setText(String.valueOf(m.intervalMs()));
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
                addDrawableChild(iv);
                if (!m.holdOnly) {
                    ButtonWidget run = ButtonWidget.builder(Text.literal(m.running ? "■" : "▶"), b -> {
                        m.running = !m.running;
                        m.nextAt = 0;
                        this.clearAndInit();
                    }).dimensions(kx + 174 + 56 + 22, y + 27, 22, 20)
                            .tooltip(Tooltip.of(Text.literal(m.running ? I18n.t("Остановить повтор") : I18n.t("Запустить повтор сейчас")))).build();
                    addDrawableChild(run);
                }
            }
        }

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(this.width / 2 - 60, py + ph - 24, 120, 20).build());
    }

    // ---------- Ввод клавиши ----------

    private void bind(boolean mouse, int code) {
        if (listening >= 0 && listening < MacroConfig.binds.size()) {
            MacroBind m = MacroConfig.binds.get(listening);
            m.keyCode = code;
            m.mouse = mouse;
            MacroConfig.save();
        }
        listening = -1;
        this.clearAndInit();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening < 0 && suggest.keyPressed(input.key())) return true;
        if (listening >= 0) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) bind(false, -1);
            else bind(false, InputUtil.fromKeyCode(input).getCode());
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listening < 0 && suggest.mouseClicked(click.x(), click.y())) return true;
        if (listening >= 0) {
            bind(true, click.button());
            return true;
        }
        if (confirmDelete >= 0) {
            boolean r = super.mouseClicked(click, doubled);
            if (!r) {
                confirmDelete = -1;
                this.clearAndInit();
            }
            return r;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (suggest.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        int max = Math.max(0, MacroConfig.binds.size() - cards);
        int nf = Math.max(0, Math.min(max, first - (int) Math.signum(verticalAmount)));
        if (nf != first) {
            first = nf;
            this.clearAndInit();
        }
        return true;
    }

    // ---------- Отрисовка ----------

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF2E3742);
        ctx.fill(px, py, px + pw, py + ph, 0xF0161B21);
        ctx.fill(px, py, px + pw, py + 22, 0xFF1E2630);
        ctx.fill(px, py + 22, px + pw, py + 23, ACCENT);
        int total = MacroConfig.binds.size();
        int cw = pw - 16;
        for (int i = 0; i < cards; i++) {
            int idx = first + i;
            if (idx >= total) break;
            MacroBind m = MacroConfig.binds.get(idx);
            int x = px + 8, y = listY + i * (CARD_H + CARD_GAP);
            boolean active = m.running;
            ctx.fill(x - 1, y - 1, x + cw + 1, y + CARD_H + 1, active ? 0xFF5BD38A : (listening == idx ? ACCENT : 0xFF2E3742));
            ctx.fill(x, y, x + cw, y + CARD_H, 0xFF1A2027);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var tr = this.textRenderer;
        ctx.drawItem(new ItemStack(Items.COMMAND_BLOCK), px + 4, py + 3);
        ctx.drawTextWithShadow(tr, this.title, px + 24, py + 7, 0xFFFFFFFF);
        String hint = I18n.t("Клавиша → сообщение или /команда в чат. Работают, только когда нет открытых меню.");
        ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(hint, pw - 24 - tr.getWidth(this.title) - 16)),
                px + 24 + tr.getWidth(this.title) + 10, py + 7, 0xFF6F7A86);
        int total = MacroConfig.binds.size();
        if (total == 0) {
            ctx.drawCenteredTextWithShadow(tr, Text.literal(I18n.t("Макросов пока нет — нажмите «+ Новый макрос»")),
                    this.width / 2, listY + 14, 0xFF9AA4AE);
        }
        for (int i = 0; i < cards; i++) {
            int idx = first + i;
            if (idx >= total) break;
            MacroBind m = MacroConfig.binds.get(idx);
            int x = px + 8, y = listY + i * (CARD_H + CARD_GAP);
            // номер
            ctx.fill(x + 3, y + 6, x + 19, y + 22, 0xFF0E1216);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(String.valueOf(idx + 1)), x + 11, y + 10, ACCENT);
            if (m.repeat) {
                int kx = x + 24;
                ctx.drawTextWithShadow(tr, Text.literal(I18n.t("мс")), kx + 174 + 58, y + 33, 0xFF9AA4AE);
                if (m.intervalMs() < 1000) {
                    String w = "! " + I18n.t("частый повтор — можно получить кик за спам");
                    int wx = kx + 174 + 56 + 48;
                    int room = x + pw - 16 - wx - 4;
                    if (room > 20) ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(w, room)), wx, y + 33, 0xFFFFAA33);
                }
            }
            if (m.running) ctx.drawTextWithShadow(tr, Text.literal("●"), x + 7, y + 33, 0xFF5BD38A);
            if (m.keyCode < 0 && listening != idx && !m.text.isBlank()) {
                ctx.drawTextWithShadow(tr, Text.literal("!"), x + 9, y + 33, 0xFFFFAA33);
            }
        }
        if (total > cards) {
            String pg = (first + 1) + "–" + Math.min(total, first + cards) + " / " + total + I18n.t("  (колёсико — листать)");
            ctx.drawTextWithShadow(tr, Text.literal(pg), px + 10, py + ph - 18, 0xFF6F7A86);
        }
        // подсказки команд — поверх всего, у поля в фокусе
        for (TextFieldWidget f : msgFields) if (f.isFocused()) suggest.attach(f);
        suggest.update();
        suggest.render(ctx, this.height, mouseX, mouseY);
    }

    @Override
    public void close() {
        MacroConfig.save();
        this.client.setScreen(parent);
    }
}
