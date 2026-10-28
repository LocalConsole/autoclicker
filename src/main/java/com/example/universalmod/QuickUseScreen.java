package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Быстрое использование: список строк «клавиша — предмет». Нажал клавишу — предмет использован сразу,
 * даже если прицел наведён на верстак, люк, сундук и т.п.
 */
public class QuickUseScreen extends Screen {
    private static final int MAX_BINDS = 8;
    private static final int ROW_H = 24;

    private final Screen parent;
    private final List<ButtonWidget> itemButtons = new ArrayList<>();
    private final List<ButtonWidget> keyButtons = new ArrayList<>();
    private int listening = -1; // индекс строки, у которой ждём нажатие клавиши
    private int x0, pw, top;

    public QuickUseScreen(Screen parent) {
        super(Text.literal(I18n.t("Быстрое использование")));
        this.parent = parent;
    }

    private Text keyLabel(int i) {
        if (listening == i) return Text.literal("> ... <");
        return QuickUseConfig.binds.get(i).keyText();
    }

    private Text itemLabel(QuickUseBind b) {
        Item it = QuickUse.parse(b.item);
        if (it == null) return Text.literal(I18n.t("Выбрать предмет..."));
        return Text.literal("   " + new ItemStack(it).getName().getString());
    }

    @Override
    protected void init() {
        itemButtons.clear();
        keyButtons.clear();
        pw = Math.min(380, this.width - 20);
        x0 = (this.width - pw) / 2;
        top = 56;

        int keyW = Math.min(110, pw / 3);
        int rmW = 20;
        int fieldW = pw - keyW - rmW - 8;

        for (int i = 0; i < QuickUseConfig.binds.size(); i++) {
            final int idx = i;
            final QuickUseBind b = QuickUseConfig.binds.get(i);
            int y = top + i * ROW_H;

            ButtonWidget kb = ButtonWidget.builder(keyLabel(i), btn -> {
                listening = idx;
                btn.setMessage(keyLabel(idx));
            }).dimensions(x0, y, keyW, 20).build();
            keyButtons.add(kb);
            addDrawableChild(kb);

            ButtonWidget ib = ButtonWidget.builder(itemLabel(b), btn -> {
                listening = -1;
                this.client.setScreen(new ItemPickerScreen(this, id -> {
                    b.item = id;
                    QuickUseConfig.save();
                }));
            }).dimensions(x0 + keyW + 4, y, fieldW, 20).build();
            itemButtons.add(ib);
            addDrawableChild(ib);

            addDrawableChild(ButtonWidget.builder(Text.literal("X"), btn -> {
                QuickUseConfig.binds.remove(idx);
                listening = -1;
                QuickUseConfig.save();
                this.clearAndInit();
            }).dimensions(x0 + pw - rmW, y, rmW, 20)
                    .tooltip(Tooltip.of(Text.literal(I18n.t("Удалить")))).build());
        }

        int by = top + QuickUseConfig.binds.size() * ROW_H + 4;
        ButtonWidget add = ButtonWidget.builder(Text.literal(I18n.t("+ Добавить предмет")), btn -> {
            QuickUseConfig.binds.add(new QuickUseBind());
            listening = -1;
            QuickUseConfig.save();
            this.clearAndInit();
        }).dimensions(x0, by, pw, 20).build();
        add.active = QuickUseConfig.binds.size() < MAX_BINDS;
        addDrawableChild(add);

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void bind(boolean mouse, int code) {
        int i = listening;
        listening = -1;
        if (i >= 0 && i < QuickUseConfig.binds.size()) {
            QuickUseBind b = QuickUseConfig.binds.get(i);
            b.keyCode = code;
            b.mouse = mouse;
            QuickUseConfig.save();
        }
        this.clearAndInit();
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (listening >= 0) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) bind(false, -1); // Esc = снять клавишу
            else bind(false, InputUtil.fromKeyCode(input).getCode());
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (listening >= 0) {
            bind(true, click.button());
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        for (int i = 0; i < itemButtons.size(); i++) {
            Item it = QuickUse.parse(QuickUseConfig.binds.get(i).item);
            if (it != null) {
                ButtonWidget ib = itemButtons.get(i);
                context.drawItem(new ItemStack(it), ib.getX() + 2, ib.getY() + 2);
            }
        }
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(
                        I18n.t("Клавиша использует предмет сразу, даже при наведении на верстак, люк и т.п."), this.width - 20)),
                this.width / 2, 26, 0xFFAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(
                        I18n.t("Нажмите на поле предмета и выберите его из списка"), this.width - 20)),
                this.width / 2, 38, 0xFFAAAAAA);
        if (QuickUseConfig.binds.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(I18n.t("Пока пусто. Добавьте первый предмет.")),
                    this.width / 2, top + 4, 0xFF888888);
        }
    }

    @Override
    public void close() {
        QuickUseConfig.save();
        this.client.setScreen(parent);
    }
}
