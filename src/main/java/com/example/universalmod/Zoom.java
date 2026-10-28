package com.example.universalmod;

import com.example.universalmod.mixin.SimpleOptionAccessor;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.util.Identifier;

/**
 * Зум: пока зажата клавиша, угол обзора делится на кратность. Колёсико мыши меняет кратность.
 * Основной способ — хук в GameRenderer (плавно, дробный FOV). Если хук не сработал в этой версии игры,
 * включается запасной способ: целочисленное значение настройки FOV.
 */
public class Zoom {
    public static boolean held = false;
    public static boolean dirty = false;
    public static int hookHits = 0;

    private static double factor = 1.0;
    private static long lastNs = 0;
    private static int frames = 0;
    private static boolean fallback = false;
    private static int baseFov = -1;

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.FOOD_BAR,
                Identifier.of(UniversalMod.MOD_ID, "zoom_frame"),
                (context, tickCounter) -> frame(MinecraftClient.getInstance()));
    }

    /** Текущая кратность с плавным переходом; можно вызывать сколько угодно раз за кадр. */
    public static double factor() {
        long now = System.nanoTime();
        double dt = lastNs == 0 ? 0 : Math.min(0.1, (now - lastNs) / 1e9);
        lastNs = now;
        double target = held ? Math.max(1.0, ClickerConfig.zoomLevel) : 1.0;
        if (!ClickerConfig.zoomSmooth) {
            factor = target;
        } else {
            double k = 1 - Math.exp(-dt * 14);
            factor = Math.exp(Math.log(factor) + (Math.log(target) - Math.log(factor)) * k);
        }
        return factor;
    }

    /** Множитель чувствительности мыши при зуме. */
    public static double sensScale() {
        if (!ClickerConfig.zoomSens || factor <= 1.001) return 1.0;
        return Math.max(0.05, 1.0 / factor);
    }

    public static void scroll(double vertical) {
        if (vertical == 0) return;
        double lvl = ClickerConfig.zoomLevel * (vertical > 0 ? 1.15 : 1 / 1.15);
        ClickerConfig.zoomLevel = (float) Math.max(1.2, Math.min(30.0, lvl));
        dirty = true;
    }

    // ---------- Запасной способ ----------

    private static void frame(MinecraftClient client) {
        if (client == null || client.options == null) return;
        double f = factor();
        frames++;
        if (!fallback && frames > 60 && hookHits == 0) fallback = true;
        if (!fallback) return;
        SimpleOption<Integer> opt = client.options.getFov();
        if (f > 1.01) {
            if (baseFov < 0) baseFov = opt.getValue();
            setRaw(opt, (int) Math.max(1, Math.round(baseFov / f)));
        } else if (baseFov >= 0) {
            setRaw(opt, baseFov);
            baseFov = -1;
        }
    }

    public static void restore(MinecraftClient client) {
        if (client == null || client.options == null || baseFov < 0) return;
        setRaw(client.options.getFov(), baseFov);
        baseFov = -1;
    }

    @SuppressWarnings("unchecked")
    private static void setRaw(SimpleOption<Integer> opt, int v) {
        ((SimpleOptionAccessor<Integer>) (Object) opt).setRawValue(v);
    }
}
