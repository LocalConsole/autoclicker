package com.example.universalmod;

import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/** Одна привязка: клавиша -> предмет, который будет использован сразу (хорус, жемчуг эндера и т.д.). */
public class QuickUseBind {
    public int keyCode = -1;      // -1 = не назначена
    public boolean mouse = false; // true = кнопка мыши
    public String item = "";      // id предмета, например "ender_pearl" или "minecraft:chorus_fruit"

    // Рантайм-состояние (в файл не пишется)
    public transient boolean wasDown;
    public transient boolean frameDown;
    public transient boolean pending;

    public Text keyText() {
        if (keyCode < 0) return Text.literal(I18n.t("Назначить"));
        InputUtil.Type type = mouse ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM;
        return type.createFromCode(keyCode).getLocalizedText();
    }
}
