package com.example.universalmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Строка поиска предмета: контейнеры с ним подсвечиваются красной рамкой. */
public class ContainerSearchScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget field;

    public ContainerSearchScreen(Screen parent) {
        super(Text.literal(I18n.t("Поиск в контейнерах")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        field = new TextFieldWidget(this.textRenderer, cx - 100, 40, 200, 20, Text.literal(""));
        field.setMaxLength(64);
        field.setPlaceholder(Text.literal(I18n.t("Название предмета")));
        field.setText(ContainerSearch.query);
        field.setChangedListener(t -> ContainerSearch.query = t);
        addDrawableChild(field);
        setInitialFocus(field);

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Очистить")), b -> field.setText(""))
                .dimensions(cx - 100, 66, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(cx + 2, 66, 98, 20).build());
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFFFF);
        String info = I18n.t("Запомнено контейнеров: ") + ContainerSearch.cachedCount()
                + "   " + I18n.t("Найдено: ") + ContainerSearch.matchCount();
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(info), this.width / 2, 94, 0xFFAAAAAA);
        boolean single = this.client != null && this.client.getServer() != null;
        String note = single
                ? I18n.t("Одиночная игра: видны все контейнеры рядом")
                : I18n.t("Показаны только сундуки, которые были открыты вами");
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(note), this.width / 2, 108, 0xFFFFD866);
        if (!single) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(I18n.t("Память сохраняется после выхода и при смене ника")), this.width / 2, 122, 0xFF777777);
        }
    }

    @Override
    public void close() {
        ClickerConfig.searchQuery = ContainerSearch.query;
        ClickerConfig.save();
        this.client.setScreen(parent);
    }
}
