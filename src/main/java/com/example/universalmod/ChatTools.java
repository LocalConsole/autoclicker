package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

/** Ctrl+ЛКМ по строке чата — копирование; кнопки «Открыть» / «Папка» после скриншота (F2). */
public class ChatTools {
    private static Field visibleField;
    private static Field scrolledField;
    private static boolean fieldsResolved = false;

    // ---------- Скриншот ----------

    /** Добавляет к сообщению «Скриншот сохранён» кнопки открыть файл и показать папку. */
    public static Text decorate(Text message) {
        try {
            if (!ClickerConfig.screenshotButtons) return message;
            if (!(message.getContent() instanceof TranslatableTextContent t)) return message;
            if (!"screenshot.success".equals(t.getKey())) return message;
            Object[] args = t.getArgs();
            if (args.length == 0) return message;
            String name = (args[0] instanceof Text tx) ? tx.getString() : String.valueOf(args[0]);

            File dir = new File(MinecraftClient.getInstance().runDirectory, "screenshots");
            File file = new File(dir, name);

            MutableText out = message.copy();
            out.append(Text.literal(" "));
            out.append(button(I18n.t("Открыть"), file));
            out.append(Text.literal(" "));
            out.append(button(I18n.t("Папка"), dir));
            return out;
        } catch (Throwable e) {
            return message;
        }
    }

    private static Text button(String label, File target) {
        return Text.literal("[" + label + "]").setStyle(Style.EMPTY
                .withColor(Formatting.AQUA)
                .withClickEvent(new ClickEvent.OpenFile(target.getAbsoluteFile())));
    }

    // ---------- Копирование строки чата ----------

    private static void resolveFields() {
        if (fieldsResolved) return;
        fieldsResolved = true;
        // Ищем поля по типу, а не по имени: в игре имена обфусцированы
        List<Field> ints = new ArrayList<>();
        for (Field f : ChatHud.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (List.class.isAssignableFrom(f.getType())
                    && f.getGenericType() instanceof ParameterizedType pt
                    && pt.getActualTypeArguments().length == 1
                    && pt.getActualTypeArguments()[0] == ChatHudLine.Visible.class) {
                visibleField = f;
            } else if (f.getType() == int.class) {
                ints.add(f);
            }
        }
        if (ints.size() == 1) scrolledField = ints.get(0);
        for (Field f : new Field[]{visibleField, scrolledField}) {
            if (f != null) f.setAccessible(true);
        }
    }

    /** Возвращает true, если клик обработан (строка скопирована). */
    public static boolean tryCopy(Click click) {
        try {
            if (!ClickerConfig.chatCopy || click.button() != 0) return false;
            MinecraftClient mc = MinecraftClient.getInstance();
            long win = mc.getWindow().getHandle();
            if (GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) != GLFW.GLFW_PRESS
                    && GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) != GLFW.GLFW_PRESS) return false;
            ChatHud hud = mc.inGameHud.getChatHud();
            resolveFields();
            if (visibleField == null) return false;
            List<?> vis = (List<?>) visibleField.get(hud);
            int scrolled = (scrolledField == null) ? 0 : scrolledField.getInt(hud);

            double scale = mc.options.getChatScale().getValue();
            double spacing = mc.options.getChatLineSpacing().getValue();
            double x = click.x() / scale - 4.0;
            double y = (mc.getWindow().getScaledHeight() - 40 - click.y()) / (scale * (spacing + 1.0));
            if (x < -4.0 || x > Math.floor(Math.floor(mc.options.getChatWidth().getValue() * 280.0 + 40.0) / scale)) return false;
            if (y < 0 || y >= hud.getVisibleLineCount()) return false;
            int idx = (int) (y / 9.0 + scrolled);
            if (idx < 0 || idx >= vis.size()) return false;

            String text = fullText(vis, idx);
            if (text.isEmpty()) return false;
            mc.keyboard.setClipboard(text);
            mc.inGameHud.setOverlayMessage(Text.literal(I18n.t("Строка чата скопирована")), false);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Весь текст сообщения (все перенесённые строки). Новые строки стоят в списке первыми. */
    private static String fullText(List<?> vis, int idx) {
        int start = idx;
        while (start > 0 && !((ChatHudLine.Visible) vis.get(start)).endOfEntry()) start--;
        int end = idx;
        while (end + 1 < vis.size() && !((ChatHudLine.Visible) vis.get(end + 1)).endOfEntry()) end++;

        StringBuilder sb = new StringBuilder();
        for (int i = end; i >= start; i--) {
            StringBuilder line = new StringBuilder();
            ((ChatHudLine.Visible) vis.get(i)).content().accept((index, style, cp) -> {
                line.appendCodePoint(cp);
                return true;
            });
            String part = line.toString().strip();
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part);
        }
        return sb.toString();
    }
}
