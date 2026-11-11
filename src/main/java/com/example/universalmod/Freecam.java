package com.example.universalmod;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

/**
 * Свободная камера: камера отрывается от игрока и летает сама (сквозь блоки), а персонаж стоит на месте.
 * Управление: клавиши движения — полёт, прыжок/приседание — вверх/вниз, спринт — ускорение,
 * колёсико мыши — скорость, мышь — поворот камеры. Атаки и использование предметов на это время отключены.
 */
public class Freecam {
    public static boolean active = false;
    public static double x, y, z;
    public static float yaw, pitch;
    /** Сколько раз за кадр сработал хук позиции камеры. Если 0 — миксин не встал. */
    public static int hookHits = 0;

    private static final double BOOST = 3.0;

    private static ClientPlayerEntity owner;
    private static Input savedInput;
    private static Perspective prevPerspective = Perspective.FIRST_PERSON;
    private static long lastNs = 0;
    private static int activeTicks = 0;
    private static boolean speedDirty = false;

    /** Пустой ввод: игрок не двигается, пока камера летает сама. */
    private static class FrozenInput extends Input {
    }

    public static void register() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) ->
                active ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                active ? ActionResult.FAIL : ActionResult.PASS);
    }

    public static void toggle(MinecraftClient client) {
        if (client == null || client.player == null) return;
        if (active) {
            disable(client);
            say(client, I18n.t("Свободная камера") + ": " + I18n.t("ВЫКЛ"), true);
            return;
        }
        if (!ClickerConfig.freecam || FreeLook.active) return;
        enable(client);
        say(client, I18n.t("Свободная камера") + ": " + I18n.t("ВКЛ"), true);
    }

    private static void enable(MinecraftClient client) {
        ClientPlayerEntity p = client.player;
        owner = p;
        x = p.getX();
        y = p.getEyeY();
        z = p.getZ();
        yaw = p.getYaw();
        pitch = p.getPitch();
        savedInput = p.input;
        p.input = new FrozenInput();
        // Третье лицо нужно, чтобы игра рисовала самого игрока, а не прятала его как «свой» вид.
        prevPerspective = client.options.getPerspective();
        client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        lastNs = 0;
        activeTicks = 0;
        hookHits = 0;
        active = true;
    }

    public static void disable(MinecraftClient client) {
        if (!active) return;
        if (owner != null && savedInput != null && owner.input instanceof FrozenInput) {
            owner.input = savedInput;
        }
        savedInput = null;
        owner = null;
        active = false;
        if (client != null && client.options != null) client.options.setPerspective(prevPerspective);
        if (speedDirty) {
            speedDirty = false;
            ClickerConfig.save();
        }
    }

    /** Каждый тик: следим, чтобы всё осталось в порядке (смерть, смена мира, выключили в настройках, хук не встал). */
    public static void tick(MinecraftClient client) {
        if (!active) return;
        if (client.player == null || client.player != owner || !client.player.isAlive() || !ClickerConfig.freecam) {
            disable(client);
            return;
        }
        if (++activeTicks > 40 && hookHits == 0) {
            disable(client);
            say(client, I18n.t("Свободная камера не встала: миксин не нашёл место вставки. Пришлите logs/latest.log"), false);
            return;
        }
        if (client.options.getPerspective() == Perspective.FIRST_PERSON) {
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
    }

    /** Поворот камеры от движения мыши (те же коэффициенты, что у игрока в ванили). */
    public static void turn(double dx, double dy) {
        yaw += (float) (dx * 0.15);
        pitch = Math.max(-90f, Math.min(90f, pitch + (float) (dy * 0.15)));
    }

    /** Колёсико мыши меняет скорость полёта. */
    public static void scroll(double vertical) {
        if (vertical == 0) return;
        double v = ClickerConfig.freecamSpeed * (vertical > 0 ? 1.25 : 1 / 1.25);
        ClickerConfig.freecamSpeed = (float) Math.max(1.0, Math.min(100.0, v));
        speedDirty = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.player != null) {
            say(mc, I18n.t("Скорость камеры") + ": " + Math.round(ClickerConfig.freecamSpeed)
                    + I18n.t(" бл/с"), true);
        }
    }

    /** Вызывается каждый кадр из хука камеры: двигает точку камеры по нажатым клавишам. */
    public static void frame(MinecraftClient client) {
        hookHits++;
        long now = System.nanoTime();
        double dt = lastNs == 0 ? 0 : Math.min(0.1, (now - lastNs) / 1e9);
        lastNs = now;
        if (client == null || client.currentScreen != null || dt <= 0) return;

        GameOptions o = client.options;
        long w = client.getWindow().getHandle();
        double fwd = axis(w, o.forwardKey, o.backKey);
        double side = axis(w, o.rightKey, o.leftKey);
        double up = axis(w, o.jumpKey, o.sneakKey);
        if (fwd == 0 && side == 0 && up == 0) return;

        double yr = Math.toRadians(yaw);
        double pr = Math.toRadians(pitch);
        // Вперёд — туда, куда смотрит камера (с учётом наклона), вбок — строго горизонтально.
        double mx = -Math.sin(yr) * Math.cos(pr) * fwd + -Math.cos(yr) * side;
        double my = -Math.sin(pr) * fwd + up;
        double mz = Math.cos(yr) * Math.cos(pr) * fwd + -Math.sin(yr) * side;
        double len = Math.sqrt(mx * mx + my * my + mz * mz);
        if (len > 1.0) {
            mx /= len;
            my /= len;
            mz /= len;
        }
        double speed = ClickerConfig.freecamSpeed * (down(o.sprintKey, w) ? BOOST : 1.0) * dt;
        x += mx * speed;
        y += my * speed;
        z += mz * speed;
    }

    private static double axis(long window, KeyBinding pos, KeyBinding neg) {
        return (down(pos, window) ? 1 : 0) - (down(neg, window) ? 1 : 0);
    }

    private static boolean down(KeyBinding kb, long window) {
        if (kb.isUnbound()) return false;
        String tk = kb.getBoundKeyTranslationKey();
        int code = InputUtil.fromTranslationKey(tk).getCode();
        if (tk.startsWith("key.mouse")) return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, code) == GLFW.GLFW_PRESS;
    }

    private static void say(MinecraftClient client, String text, boolean overlay) {
        if (client.player != null) client.player.sendMessage(Text.literal(text), overlay);
    }
}
