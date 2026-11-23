package com.example.universalmod;

import com.example.universalmod.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.session.Session;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** Смена ника (офлайн). Работает на серверах без проверки лицензии и в одиночной игре. */
public class NicknameScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget field;
    private Text status = Text.empty();

    public NicknameScreen(Screen parent) {
        super(Text.literal(I18n.t("Смена ника")));
        this.parent = parent;
    }

    public static boolean valid(String s) {
        return s != null && s.matches("[A-Za-z0-9_]{3,16}");
    }

    public static String current() {
        MinecraftClient c = MinecraftClient.getInstance();
        return (c == null || c.getSession() == null) ? "" : c.getSession().getUsername();
    }

    /** Подменяет сессию клиента на офлайн-сессию с нужным ником. */
    public static boolean apply(String name) {
        if (!valid(name)) return false;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            Session s = newSession(name, uuid);
            ((MinecraftClientAccessor) client).setSession(s);
            return true;
        } catch (Throwable t) {
            t.printStackTrace();
            return false;
        }
    }

    /** Конструктор Session менялся между версиями, поэтому собираем аргументы по типам. */
    private static Session newSession(String name, UUID uuid) throws Exception {
        Constructor<?> best = null;
        for (Constructor<?> c : Session.class.getConstructors()) {
            if (best == null || c.getParameterCount() > best.getParameterCount()) best = c;
        }
        if (best == null) throw new IllegalStateException("No Session constructor");
        Class<?>[] types = best.getParameterTypes();
        Object[] args = new Object[types.length];
        boolean nameSet = false;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == String.class) {
                args[i] = nameSet ? "0" : name;
                nameSet = true;
            } else if (t == UUID.class) {
                args[i] = uuid;
            } else if (t == Optional.class) {
                args[i] = Optional.empty();
            } else if (t.isEnum()) {
                Object[] consts = t.getEnumConstants();
                Object pick = consts[0];
                for (Object o : consts) if (o.toString().equalsIgnoreCase("LEGACY")) pick = o;
                args[i] = pick;
            } else {
                args[i] = null;
            }
        }
        return (Session) best.newInstance(args);
    }

    private static final int ROW_H = 22;
    private int first = 0;
    private String typed = null;
    private String confirmDelete = null;
    private int px, py, pw, ph, listY, listRows;

    /** Запоминает ник в списке (самый свежий — первым). Удаляются только вручную. */
    private static void remember(String n) {
        ClickerConfig.nicknames.remove(n);
        ClickerConfig.nicknames.add(0, n);
        ClickerConfig.nickname = n;
        ClickerConfig.save();
    }

    /** Почему ник не подходит (null — подходит). */
    private static String problem(String n) {
        if (n == null || n.isEmpty()) return I18n.t("Введите ник");
        if (n.length() < 3) return I18n.t("Слишком короткий: минимум 3 символа");
        if (n.length() > 16) return I18n.t("Слишком длинный: максимум 16 символов");
        if (!n.matches("[A-Za-z0-9_]+")) return I18n.t("Только латиница, цифры и _");
        return null;
    }

    private void use(String n) {
        remember(n);
        if (apply(n)) {
            status = Text.literal(I18n.t("Ник сменён: ") + n).styled(st -> st.withColor(0x5BD38A));
        } else {
            status = Text.literal(I18n.t("Ник сохранён в списке, но сменить сейчас не вышло")).styled(st -> st.withColor(0xFF6B6B));
        }
        typed = null;
        this.clearAndInit();
    }

    @Override
    protected void init() {
        pw = Math.min(320, this.width - 16);
        ph = Math.min(300, this.height - 16);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;

        int fy = py + 56;
        field = new TextFieldWidget(this.textRenderer, px + 10, fy, pw - 20 - 84, 20, Text.literal(""));
        field.setMaxLength(16);
        field.setPlaceholder(Text.literal(I18n.t("Новый ник")));
        field.setText(typed != null ? typed : "");
        field.setChangedListener(t -> typed = t);
        addDrawableChild(field);
        setInitialFocus(field);
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Сменить")), b -> {
            String n = field.getText().trim();
            String pr = problem(n);
            if (pr != null) {
                status = Text.literal(pr).styled(st -> st.withColor(0xFF6B6B));
                return;
            }
            use(n);
        }).dimensions(px + pw - 10 - 80, fy, 80, 20).build());

        listY = fy + 46;
        listRows = Math.max(1, (py + ph - 34 - listY) / ROW_H);
        int total = ClickerConfig.nicknames.size();
        first = Math.max(0, Math.min(first, Math.max(0, total - listRows)));
        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= total) break;
            final String n = ClickerConfig.nicknames.get(idx);
            int y = listY + i * ROW_H;
            boolean cur = n.equals(current());
            ButtonWidget pick = ButtonWidget.builder(Text.literal(""), b -> use(n))
                    .dimensions(px + 10, y, pw - 20 - 26, ROW_H - 2)
                    .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(cur ? I18n.t("Это ваш ник сейчас") : I18n.t("Нажмите, чтобы зайти под этим ником"))))
                    .build();
            addDrawableChild(pick);
            boolean confirm = n.equals(confirmDelete);
            addDrawableChild(ButtonWidget.builder(Text.literal(confirm ? "?" : "✕"), b -> {
                if (!n.equals(confirmDelete)) {
                    confirmDelete = n;
                } else {
                    ClickerConfig.nicknames.remove(n);
                    ClickerConfig.save();
                    confirmDelete = null;
                }
                this.clearAndInit();
            }).dimensions(px + pw - 10 - 22, y, 22, ROW_H - 2)
                    .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(confirm
                            ? I18n.t("Нажмите ещё раз, чтобы удалить") : I18n.t("Удалить из списка")))).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(this.width / 2 - 60, py + ph - 26, 120, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, ClickerConfig.nicknames.size() - listRows);
        int nf = Math.max(0, Math.min(max, first - (int) Math.signum(verticalAmount)));
        if (nf != first) {
            first = nf;
            this.clearAndInit();
        }
        return true;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF2E3742);
        ctx.fill(px, py, px + pw, py + ph, 0xF0161B21);
        ctx.fill(px, py, px + pw, py + 22, 0xFF1E2630);
        ctx.fill(px, py + 22, px + pw, py + 23, 0xFF5BD38A);
        ctx.fill(px + 6, listY - 14, px + pw - 6, listY + listRows * ROW_H + 2, 0xFF11151A);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var tr = this.textRenderer;
        context.drawItem(new net.minecraft.item.ItemStack(net.minecraft.item.Items.NAME_TAG), px + 4, py + 3);
        context.drawTextWithShadow(tr, this.title, px + 24, py + 7, 0xFFFFFFFF);

        // сейчас
        String now = I18n.t("Сейчас: ") + current();
        context.fill(px + 10, py + 31, px + 14, py + 35, 0xFF5BD38A);
        context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(now, pw - 30)), px + 18, py + 29, 0xFFDDDDDD);
        context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(I18n.t("Только для офлайн-серверов и одиночной игры"), pw - 20)),
                px + 10, py + 41, 0xFF6F7A86);

        // проверка ника прямо при вводе
        String t = typed == null ? "" : typed.trim();
        int hy = field.getY() + 23;
        if (status.getString().length() > 0) {
            context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(status.getString(), pw - 20)), px + 10, hy,
                    status.getStyle().getColor() != null ? 0xFF000000 | status.getStyle().getColor().getRgb() : 0xFFFF6B6B);
        } else if (!t.isEmpty()) {
            String pr = problem(t);
            context.drawTextWithShadow(tr, Text.literal(pr == null ? "✔ " + I18n.t("Подходит") : "✖ " + pr), px + 10, hy,
                    pr == null ? 0xFF5BD38A : 0xFFFF6B6B);
        }

        context.drawTextWithShadow(tr, Text.literal(I18n.t("Сохранённые ники")), px + 10, listY - 11, 0xFF9AA4AE);
        int total = ClickerConfig.nicknames.size();
        if (total == 0) {
            context.drawTextWithShadow(tr, Text.literal(I18n.t("Пока пусто — введите ник выше")), px + 14, listY + 6, 0xFF6F7A86);
        }
        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= total) break;
            String n = ClickerConfig.nicknames.get(idx);
            int y = listY + i * ROW_H;
            int x = px + 10, w = pw - 20 - 26;
            boolean cur = n.equals(current());
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW_H - 2;
            context.fill(x, y, x + w, y + ROW_H - 2, cur ? 0xFF1F3B2C : hover ? 0xFF262E38 : 0xFF1A2027);
            if (cur) context.fill(x, y, x + 2, y + ROW_H - 2, 0xFF5BD38A);
            context.drawTextWithShadow(tr, Text.literal(n), x + 8, y + 6, 0xFFFFFFFF);
            String tag = cur ? I18n.t("сейчас") : (hover ? I18n.t("войти →") : "");
            if (!tag.isEmpty()) context.drawTextWithShadow(tr, Text.literal(tag), x + w - 6 - tr.getWidth(tag), y + 6,
                    cur ? 0xFF5BD38A : 0xFF9AA4AE);
        }
        if (total > listRows) {
            int trackH = listRows * ROW_H;
            int th = Math.max(10, trackH * listRows / total);
            int ty = listY + (trackH - th) * first / Math.max(1, total - listRows);
            context.fill(px + pw - 5, listY, px + pw - 3, listY + trackH, 0xFF1E242B);
            context.fill(px + pw - 5, ty, px + pw - 3, ty + th, 0xFF56606C);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
