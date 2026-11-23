package com.example.universalmod;

import com.example.universalmod.mixin.HandledScreenAccessor;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.AnvilScreen;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;

/** Панели-помощники справа от окон: торговля, стол зачарований, наковальня. */
public final class ScreenPanels {
    private ScreenPanels() { }

    public static void draw(DrawContext ctx, HandledScreen<?> s, int x, int y) {
        try {
            HandledScreenAccessor a = (HandledScreenAccessor) s;
            int w = a.universalmod$getBgWidth(), h = a.universalmod$getBgHeight();
            if (s instanceof MerchantScreen ms) VillagerTrades.drawMerchantPanel(ctx, ms, x, y, w);
            else if (s instanceof EnchantmentScreen es) EnchantHelper.draw(ctx, es, x, y, w, h);
            else if (s instanceof AnvilScreen as) AnvilHelper.draw(ctx, as.getScreenHandler().getSlot(0).getStack(), x, y, w);
        } catch (Throwable ignored) {
            // помощник не должен ломать окно игры
        }
    }
}
