package com.example.universalmod;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Настройки показа прочности на экране. */
public class ArmorHudScreen extends Screen {
    private final Screen parent;

    public ArmorHudScreen(Screen parent) {
        super(Text.literal(I18n.t("Прочность на экране")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int gap = 10;
        int w = Math.min(160, (this.width - gap - 20) / 2);
        int x1 = this.width / 2 - gap / 2 - w;
        int x2 = this.width / 2 + gap / 2;
        int step = 22;
        int y = this.height / 2 - 75;

        // Левая колонка: что показывать
        addDrawableChild(toggle(x1, y, w, I18n.t("Показ прочности"),
                () -> ClickerConfig.armorHud, () -> ClickerConfig.armorHud = !ClickerConfig.armorHud));
        addDrawableChild(toggle(x1, y + step, w, I18n.t("Броня"),
                () -> ClickerConfig.armorShowArmor, () -> ClickerConfig.armorShowArmor = !ClickerConfig.armorShowArmor));
        addDrawableChild(toggle(x1, y + step * 2, w, I18n.t("Основная рука"),
                () -> ClickerConfig.armorShowMain, () -> ClickerConfig.armorShowMain = !ClickerConfig.armorShowMain));
        addDrawableChild(toggle(x1, y + step * 3, w, I18n.t("Вторая рука"),
                () -> ClickerConfig.armorShowOff, () -> ClickerConfig.armorShowOff = !ClickerConfig.armorShowOff));
        addDrawableChild(toggle(x1, y + step * 4, w, I18n.t("Цвета"),
                () -> ClickerConfig.armorColors, () -> ClickerConfig.armorColors = !ClickerConfig.armorColors));
        addDrawableChild(ButtonWidget.builder(modeText(), b -> {
            ClickerConfig.armorPercent = !ClickerConfig.armorPercent;
            b.setMessage(modeText());
        }).dimensions(x1, y + step * 5, w, 20).build());

        // Правая колонка: формат и расположение
        addDrawableChild(ButtonWidget.builder(maxText(), b -> {
            ClickerConfig.armorShowMax = !ClickerConfig.armorShowMax;
            b.setMessage(maxText());
        }).dimensions(x2, y, w, 20).build());
        addDrawableChild(toggle(x2, y + step, w, I18n.t("Каждый предмет отдельно"),
                () -> ClickerConfig.armorSeparate, () -> ClickerConfig.armorSeparate = !ClickerConfig.armorSeparate));
        addDrawableChild(ButtonWidget.builder(orientText(), b -> {
            ClickerConfig.armorVertical = !ClickerConfig.armorVertical;
            b.setMessage(orientText());
        }).dimensions(x2, y + step * 2, w, 20).build());
        addDrawableChild(new ClickerScreen.ValueSlider(x2, y + step * 3, w, 20, I18n.t("Блок X"), "%",
                0, 100, ClickerConfig.armorX / 10, v -> ClickerConfig.armorX = v * 10));
        addDrawableChild(new ClickerScreen.ValueSlider(x2, y + step * 4, w, 20, I18n.t("Блок Y"), "%",
                0, 100, ClickerConfig.armorY / 10, v -> ClickerConfig.armorY = v * 10));
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Перетащить мышкой...")),
                b -> this.client.setScreen(new ArmorHudMoveScreen(this)))
                .dimensions(x2, y + step * 5, w, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Назад")), b -> this.close())
                .dimensions(this.width / 2 - 100, y + step * 6 + 8, 200, 20).build());
    }

    private static Text modeText() {
        return Text.literal(I18n.t("Формат: ") + (ClickerConfig.armorPercent ? I18n.t("проценты") : I18n.t("цифры")));
    }

    private static Text maxText() {
        return Text.literal(I18n.t("Цифры: ") + (ClickerConfig.armorShowMax ? "262/363" : I18n.t("только 262")));
    }

    private static Text orientText() {
        return Text.literal(I18n.t("Блок: ") + (ClickerConfig.armorVertical ? I18n.t("вертикально") : I18n.t("горизонтально")));
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
