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

    private static final int PER_PAGE = 4;
    private int page = 0;
    private String typed = null;

    /** Запоминает ник в списке (самый свежий — первым). Удаляются только вручную. */
    private static void remember(String n) {
        ClickerConfig.nicknames.remove(n);
        ClickerConfig.nicknames.add(0, n);
        ClickerConfig.nickname = n;
        ClickerConfig.save();
    }

    private int pages() {
        return Math.max(1, (ClickerConfig.nicknames.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        field = new TextFieldWidget(this.textRenderer, cx - 100, 34, 200, 20, Text.literal(""));
        field.setMaxLength(16);
        field.setText(typed != null ? typed : current());
        field.setChangedListener(t -> typed = t);
        addDrawableChild(field);
        setInitialFocus(field);

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Применить")), b -> {
            String n = field.getText().trim();
            if (!valid(n)) {
                status = Text.literal(I18n.t("Ник: 3–16 символов, латиница, цифры и _"));
                return;
            }
            if (apply(n)) {
                remember(n);
                this.close();
            } else {
                status = Text.literal(I18n.t("Не удалось сменить ник"));
            }
        }).dimensions(cx - 100, 58, 200, 20).build());

        // Сохранённые ники: клик — сменить, X — удалить
        page = Math.min(page, pages() - 1);
        int from = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = from + i;
            if (idx >= ClickerConfig.nicknames.size()) break;
            final String n = ClickerConfig.nicknames.get(idx);
            int y = 84 + i * 22;
            addDrawableChild(ButtonWidget.builder(Text.literal(n), b -> {
                if (apply(n)) {
                    remember(n);
                    this.close();
                } else {
                    status = Text.literal(I18n.t("Не удалось сменить ник"));
                }
            }).dimensions(cx - 100, y, 170, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                ClickerConfig.nicknames.remove(n);
                ClickerConfig.save();
                this.clearAndInit();
            }).dimensions(cx + 74, y, 26, 20).build());
        }

        if (pages() > 1) {
            int ny = 84 + PER_PAGE * 22;
            addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                page = (page + pages() - 1) % pages();
                this.clearAndInit();
            }).dimensions(cx - 100, ny, 40, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                page = (page + 1) % pages();
                this.clearAndInit();
            }).dimensions(cx + 60, ny, 40, 20).build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Назад")), b -> this.close())
                .dimensions(cx - 100, 84 + PER_PAGE * 22 + 26, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int cx = this.width / 2;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, cx, 8, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(I18n.t("Сейчас: ") + current()), cx, 21, 0xFFAAAAAA);
        if (ClickerConfig.nicknames.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(I18n.t("Сохранённых ников нет")), cx, 112, 0xFF888888);
        }
        if (pages() > 1) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal((page + 1) + " / " + pages()), cx, 84 + PER_PAGE * 22 + 6, 0xFFFFFFFF);
        }
        int by = 84 + PER_PAGE * 22 + 52;
        if (status.getString().length() > 0) {
            context.drawCenteredTextWithShadow(this.textRenderer, status, cx, by, 0xFFFF5555);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(I18n.t("Только для офлайн-серверов и одиночной игры")), cx, by, 0xFF888888);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
