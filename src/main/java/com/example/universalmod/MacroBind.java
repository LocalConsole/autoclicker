package com.example.universalmod;

import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/** Один макрос: клавиша -> сообщение в чат или команда, с необязательным автоповтором. */
public class MacroBind {
    /** Старый формат (до версии 2): только для переноса настроек из прошлых версий мода. */
    public enum Mode { ONCE, HOLD, AUTO }

    public static final int MIN_INTERVAL_MS = 50; // один тик игры
    public static final int CURRENT_VERSION = 2;

    public int version = 0;
    public int keyCode = -1;      // -1 = не назначена
    public boolean mouse = false; // true = кнопка мыши
    public String text = "";      // "/команда" или обычное сообщение
    public boolean repeat = false;   // автоповтор включён
    public boolean holdOnly = false; // повторять только пока зажата клавиша (иначе клавиша включает/выключает повтор)
    public int seconds = 1;
    public int millis = 0;

    // Старые поля (читаются только для переноса, дальше не пишутся)
    public Mode mode;
    public Boolean enabled;

    // Рантайм-состояние (в файл не пишется)
    /** Повтор сейчас идёт. Намеренно НЕ сохраняется: иначе после перезапуска игры макрос сам начинал спамить. */
    public transient boolean running;
    public transient boolean wasDown;
    public transient boolean frameDown;
    public transient boolean pending;
    public transient long nextAt;

    public long intervalMs() {
        return Math.max(MIN_INTERVAL_MS, seconds * 1000L + millis);
    }

    public Text keyText() {
        if (keyCode < 0) return Text.literal(I18n.t("Назначить"));
        InputUtil.Type type = mouse ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM;
        return type.createFromCode(keyCode).getLocalizedText();
    }
}
