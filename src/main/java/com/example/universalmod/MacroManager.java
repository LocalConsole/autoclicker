package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Обработка макросов. Клавиши читаются напрямую из GLFW: каждый кадр (чтобы не терять короткие нажатия)
 * и каждый тик. Пока открыто любое меню или чат, макросы не срабатывают и повтор стоит на паузе.
 */
public class MacroManager {

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.FOOD_BAR,
                Identifier.of(UniversalMod.MOD_ID, "macro_poll"),
                (context, tickCounter) -> poll(MinecraftClient.getInstance()));
    }

    /** Полный сброс: останавливает все повторы (выход из мира, макросы выключены глобально). */
    public static void reset() {
        for (MacroBind b : MacroConfig.binds) {
            b.running = false;
            b.wasDown = false;
            b.frameDown = false;
            b.pending = false;
        }
    }

    /** Каждый кадр: запоминаем нажатия, которые могли бы пройти между тиками. */
    private static void poll(MinecraftClient client) {
        if (client == null || client.player == null || !ClickerConfig.macrosEnabled) return;
        long window = client.getWindow().getHandle();
        boolean screenOpen = client.currentScreen != null;
        for (MacroBind b : MacroConfig.binds) {
            if (b.keyCode < 0) {
                b.frameDown = false;
                continue;
            }
            boolean raw = isDown(window, b);
            if (raw && !b.frameDown && !screenOpen) b.pending = true;
            b.frameDown = raw;
        }
    }

    public static void tick(MinecraftClient client) {
        if (!ClickerConfig.macrosEnabled) {
            reset();
            return;
        }
        long window = client.getWindow().getHandle();
        long now = System.currentTimeMillis();
        boolean screenOpen = client.currentScreen != null;

        for (MacroBind b : MacroConfig.binds) {
            if (b.text == null || b.text.isBlank()) {
                b.wasDown = false;
                b.pending = false;
                continue;
            }

            boolean raw = b.keyCode >= 0 && isDown(window, b);
            if (screenOpen) {
                // В меню/чате не отправляем и не ловим нажатия
                b.wasDown = raw;
                b.pending = false;
                // Повтор на паузе: после закрытия меню ждём целый интервал, а не стреляем сразу
                if (b.repeat) b.nextAt = now + b.intervalMs();
                continue;
            }
            boolean pressed = b.pending || (raw && !b.wasDown);
            b.pending = false;
            b.wasDown = raw;

            if (!b.repeat) {
                if (pressed) send(client, b.text);
                continue;
            }

            if (b.holdOnly) {
                // Повтор, пока клавиша зажата
                if (pressed) {
                    send(client, b.text);
                    b.nextAt = now;
                    schedule(b, now);
                } else if (raw && now >= b.nextAt) {
                    send(client, b.text);
                    schedule(b, now);
                }
                continue;
            }

            // Клавиша включает и выключает повтор (запустить можно и кнопкой в меню)
            if (pressed) {
                b.running = !b.running;
                String label = b.keyCode >= 0 ? b.keyText().getString() : b.text;
                client.player.sendMessage(Text.literal(I18n.t("Авто-макрос ") + label
                        + ": " + (b.running ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ"))), true);
                if (b.running) {
                    send(client, b.text);
                    b.nextAt = now;
                    schedule(b, now);
                }
            } else if (b.running && now >= b.nextAt) {
                send(client, b.text);
                schedule(b, now);
            }
        }
    }

    /** Следующая отправка ровно через интервал от прошлой (без накопления дрейфа и без «очереди» пропущенных). */
    private static void schedule(MacroBind b, long now) {
        long iv = b.intervalMs();
        b.nextAt += iv;
        if (b.nextAt <= now) b.nextAt = now + iv;
    }

    private static boolean isDown(long window, MacroBind b) {
        if (b.mouse) {
            return GLFW.glfwGetMouseButton(window, b.keyCode) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, b.keyCode) == GLFW.GLFW_PRESS;
    }

    private static void send(MinecraftClient client, String raw) {
        if (client.player == null || client.player.networkHandler == null) return;
        String t = raw.trim();
        if (t.isEmpty()) return;
        if (t.startsWith("/")) {
            client.player.networkHandler.sendChatCommand(t.substring(1));
        } else {
            client.player.networkHandler.sendChatMessage(t);
        }
    }
}
