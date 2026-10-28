package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Цветная рамка блока, на который смотрит игрок: светло-серая, красная или зелёная.
 * Рисуется ванильными линиями по настоящей форме блока ({@link WorldOutlines}).
 */
public class TargetOutline {
    /** Названия режимов: 0 — выключено. */
    public static final String[] NAMES = {"Выкл", "Светло-серый", "Красный", "Зелёный"};

    private static final int[] COLORS = {0, 0xD0D0D0, 0xFF2020, 0x20FF40};

    public static void tick(MinecraftClient client) {
        // рамка рисуется каждый кадр, тик не нужен
    }

    public static WorldOutlines.Mark mark(MinecraftClient client) {
        int mode = ClickerConfig.targetOutline;
        if (mode <= 0 || mode >= COLORS.length || client.player == null || client.world == null
                || client.currentScreen != null) return null;
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return null;
        return WorldOutlines.Mark.of(bhr.getBlockPos(), COLORS[mode]);
    }
}
