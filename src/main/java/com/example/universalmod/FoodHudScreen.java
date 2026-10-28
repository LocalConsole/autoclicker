package com.example.universalmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Включение/выключение показов еды. */
public class FoodHudScreen extends Screen {
    private final Screen parent;

    public FoodHudScreen(Screen parent) {
        super(Text.literal(I18n.t("Показы еды")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = Math.min(240, this.width - 20);
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - 62;
        addDrawableChild(toggle(x, y - 24, w, I18n.t("Насыщение числом"),
                () -> ClickerConfig.foodSatNumber, () -> ClickerConfig.foodSatNumber = !ClickerConfig.foodSatNumber));
        addDrawableChild(toggle(x, y, w, I18n.t("Насыщение (золотая рамка)"),
                () -> ClickerConfig.foodSaturation, () -> ClickerConfig.foodSaturation = !ClickerConfig.foodSaturation));
        addDrawableChild(toggle(x, y + 24, w, I18n.t("Предпросмотр еды в руке"),
                () -> ClickerConfig.foodPreview, () -> ClickerConfig.foodPreview = !ClickerConfig.foodPreview));
        addDrawableChild(toggle(x, y + 48, w, I18n.t("Значения еды в подсказке"),
                () -> ClickerConfig.foodTooltip, () -> ClickerConfig.foodTooltip = !ClickerConfig.foodTooltip));
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Назад")), b -> this.close())
                .dimensions(x, y + 82, w, 20).build());
    }

    private ButtonWidget toggle(int x, int y, int w, String label, BooleanSupplier state, Runnable flip) {
        return ButtonWidget.builder(text(label, state.getAsBoolean()), b -> {
            flip.run();
            b.setMessage(text(label, state.getAsBoolean()));
        }).dimensions(x, y, w, 20).build();
    }

    private static Text text(String label, boolean on) {
        return Text.literal(label + ": " + (on ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ")));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 100, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        ClickerConfig.save();
        this.client.setScreen(parent);
    }
}
