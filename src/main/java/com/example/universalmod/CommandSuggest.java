package com.example.universalmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Подсказки команд для поля ввода (как в чате): после «/» показываются команды, которые этот сервер
 * разрешает вам, а дальше — варианты аргументов (ники, предметы, координаты…), по мере того как вы пишете.
 * ↑/↓ — выбрать, Tab или Enter — подставить, Esc — скрыть, клик мышью — подставить.
 */
public class CommandSuggest {
    private static final int MAX_SHOWN = 8, ROW = 11;

    private TextFieldWidget field;
    private volatile Suggestions pending;
    private List<Suggestion> list = new ArrayList<>();
    private String forText = "";
    private int selected = 0, scroll = 0;
    private boolean hidden = false;
    private int bx, by, bw, bh;

    /** Поле, для которого сейчас показываем подсказки (обычно — в фокусе). */
    public void attach(TextFieldWidget f) {
        if (f != field) {
            field = f;
            list = new ArrayList<>();
            forText = "";
            hidden = false;
        }
    }

    @SuppressWarnings("unchecked")
    public void update() {
        if (field == null) return;
        String t = field.getText();
        if (!t.startsWith("/")) {
            list = new ArrayList<>();
            forText = t;
            return;
        }
        if (!t.equals(forText)) {
            forText = t;
            hidden = false;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.player.networkHandler == null) return;
            try {
                CommandDispatcher<Object> d = (CommandDispatcher<Object>) (Object) mc.player.networkHandler.getCommandDispatcher();
                Object src = mc.player.networkHandler.getCommandSource();
                String cmd = t.substring(1);
                int cursor = Math.max(0, Math.min(cmd.length(), field.getCursor() - 1));
                ParseResults<Object> parse = d.parse(new StringReader(cmd), src);
                d.getCompletionSuggestions(parse, cursor).thenAccept(s -> pending = s);
            } catch (Throwable ignored) {
            }
        }
        Suggestions s = pending;
        if (s != null) {
            pending = null;
            list = new ArrayList<>(s.getList());
            selected = 0;
            scroll = 0;
        }
    }

    public boolean visible() {
        return field != null && !hidden && !list.isEmpty() && field.isFocused() && field.getText().startsWith("/");
    }

    private void apply(int i) {
        if (i < 0 || i >= list.size()) return;
        String cmd = field.getText().substring(1);
        String n = "/" + list.get(i).apply(cmd);
        field.setText(n);
        field.setCursorToEnd(false);
        forText = ""; // запросить следующие варианты
    }

    public boolean keyPressed(int key) {
        if (!visible()) return false;
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> {
                selected = (selected + 1) % list.size();
                if (selected >= scroll + MAX_SHOWN) scroll = selected - MAX_SHOWN + 1;
                if (selected < scroll) scroll = selected;
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                selected = (selected - 1 + list.size()) % list.size();
                if (selected < scroll) scroll = selected;
                if (selected >= scroll + MAX_SHOWN) scroll = selected - MAX_SHOWN + 1;
                return true;
            }
            case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                apply(selected);
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                hidden = true;
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public boolean mouseClicked(double mx, double my) {
        if (!visible()) return false;
        if (mx < bx || mx > bx + bw || my < by || my > by + bh) return false;
        int i = scroll + (int) ((my - by - 2) / ROW);
        apply(i);
        return true;
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        if (!visible() || mx < bx || mx > bx + bw || my < by || my > by + bh) return false;
        scroll = Math.max(0, Math.min(Math.max(0, list.size() - MAX_SHOWN), scroll - (int) Math.signum(amount)));
        return true;
    }

    /** Список под полем (или над ним, если снизу нет места). */
    public void render(DrawContext ctx, int screenH, int mouseX, int mouseY) {
        if (!visible()) return;
        var tr = MinecraftClient.getInstance().textRenderer;
        int shown = Math.min(MAX_SHOWN, list.size());
        int w = 60;
        for (int i = scroll; i < scroll + shown; i++) w = Math.max(w, tr.getWidth(list.get(i).getText()) + 10);
        w = Math.min(w, 260);
        bh = shown * ROW + 4;
        bw = w;
        // начало подсказки — под началом заменяемого куска текста
        int start = list.get(0).getRange().getStart() + 1;
        String before = field.getText().substring(0, Math.min(start, field.getText().length()));
        bx = field.getX() + 4 + Math.min(tr.getWidth(before), Math.max(0, field.getWidth() - w));
        by = field.getY() + field.getHeight() + 1;
        if (by + bh > screenH - 2) by = field.getY() - bh - 1;
        ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF56A8F5);
        ctx.fill(bx, by, bx + bw, by + bh, 0xF0101418);
        for (int i = 0; i < shown; i++) {
            int idx = scroll + i;
            int ry = by + 2 + i * ROW;
            boolean hov = mouseX >= bx && mouseX <= bx + bw && mouseY >= ry && mouseY < ry + ROW;
            if (idx == selected || hov) ctx.fill(bx, ry - 1, bx + bw, ry + ROW - 1, idx == selected ? 0xFF24405F : 0xFF262E38);
            String s = tr.trimToWidth(list.get(idx).getText(), bw - 8);
            ctx.drawTextWithShadow(tr, Text.literal(s), bx + 4, ry + 1, idx == selected ? 0xFFFFFF55 : 0xFFDDDDDD);
        }
        if (list.size() > MAX_SHOWN) {
            String more = (scroll + 1) + "–" + (scroll + shown) + "/" + list.size();
            int mw = tr.getWidth(more);
            ctx.drawTextWithShadow(tr, Text.literal(more), bx + bw - mw - 2, by + bh + 2, 0xFF6F7A86);
        }
    }
}
