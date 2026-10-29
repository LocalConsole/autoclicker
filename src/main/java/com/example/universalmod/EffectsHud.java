package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Эффекты зелий с таймером и число тотемов в инвентаре. Таймер берётся из оставшегося времени эффекта
 * на клиенте (его присылает сервер), поэтому он всегда совпадает с игрой.
 */
public class EffectsHud {
    public static final String[] POS_NAMES = {"Слева сверху", "Справа сверху", "Слева снизу", "Справа снизу"};
    private static final int MAX_LINES = 12;

    private record Line(String text, int color) {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(UniversalMod.MOD_ID, "effects_hud"),
                (context, tickCounter) -> draw(context));
    }

    private static void draw(DrawContext ctx) {
        if (!ClickerConfig.effectsHud) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden) return;

        List<Line> lines = new ArrayList<>();

        List<StatusEffectInstance> effects = new ArrayList<>(p.getStatusEffects());
        // Сначала те, что закончатся раньше; бесконечные в конце
        effects.sort(Comparator.comparingInt(e -> e.isInfinite() ? Integer.MAX_VALUE : e.getDuration()));
        for (StatusEffectInstance e : effects) {
            String name = e.getEffectType().value().getName().getString();
            if (e.getAmplifier() > 0) name += " " + roman(e.getAmplifier() + 1);
            String time = e.isInfinite() ? "\u221E" : formatTicks(e.getDuration());
            int color = e.isInfinite() ? 0xFFFFFFFF : timeColor(e.getDuration());
            lines.add(new Line(name + "  " + time, color));
            if (lines.size() >= MAX_LINES) break;
        }

        if (ClickerConfig.effectsTotems) {
            int totems = countTotems(p);
            if (totems > 0 || !lines.isEmpty()) {
                int color = totems == 0 ? 0xFFFF5555 : totems == 1 ? 0xFFFFFF55 : 0xFF55FF55;
                lines.add(new Line(I18n.t("Тотемы: ") + totems, color));
            }
        }
        if (lines.isEmpty()) return;

        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth();
        int sh = ctx.getScaledWindowHeight();
        int w = 0;
        for (Line l : lines) w = Math.max(w, tr.getWidth(l.text()));
        int h = lines.size() * 10;

        int pos = Math.max(0, Math.min(3, ClickerConfig.effectsPos));
        boolean right = pos == 1 || pos == 3;
        boolean bottom = pos == 2 || pos == 3;
        int x = right ? sw - 4 - w : 4;
        int y = bottom ? sh - 4 - h : 4;

        for (Line l : lines) {
            int lx = right ? x + (w - tr.getWidth(l.text())) : x;
            ctx.drawTextWithShadow(tr, l.text(), lx, y, l.color());
            y += 10;
        }
    }

    private static int countTotems(PlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) n++;
        }
        return n;
    }

    private static String formatTicks(int ticks) {
        int s = Math.max(0, ticks) / 20;
        if (s >= 3600) return (s / 3600) + ":" + two((s / 60) % 60) + ":" + two(s % 60);
        return (s / 60) + ":" + two(s % 60);
    }

    private static String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private static int timeColor(int ticks) {
        if (ticks <= 200) return 0xFFFF5555;    // меньше 10 секунд
        if (ticks <= 600) return 0xFFFFFF55;    // меньше 30 секунд
        return 0xFFFFFFFF;
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n < r.length ? r[n] : String.valueOf(n);
    }
}
