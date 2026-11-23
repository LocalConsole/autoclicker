package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.text.Text;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Значки существ для карт: у игрока — лицо его скина, у мобов — их яйцо призыва (у каждого моба своё),
 * у предметов — сам предмет. Выше/ниже вас — стрелка ▲/▼ (и, если включено, число блоков).
 */
public final class EntityIcons {
    private EntityIcons() { }

    private static final Map<EntityType<?>, ItemStack> EGGS = new HashMap<>();
    private static Method skinGetter;
    private static Method skinDraw;
    private static boolean skinResolved = false;

    /** Ищем методы скина по типам, а не по именам: так не зависит от переименований между версиями. */
    private static void resolveSkin() {
        if (skinResolved) return;
        skinResolved = true;
        try {
            for (Method m : AbstractClientPlayerEntity.class.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getSimpleName().toLowerCase().contains("skin")) {
                    skinGetter = m;
                    break;
                }
            }
            if (skinGetter == null) return;
            Class<?> skinType = skinGetter.getReturnType();
            for (Method m : PlayerSkinDrawer.class.getMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (t.length == 5 && t[0] == DrawContext.class && t[1] == skinType
                        && t[2] == int.class && t[3] == int.class && t[4] == int.class) {
                    skinDraw = m;
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean drawFace(DrawContext ctx, AbstractClientPlayerEntity p, int x, int y, int size) {
        resolveSkin();
        if (skinGetter == null || skinDraw == null) return false;
        try {
            Object skin = skinGetter.invoke(p);
            if (skin == null) return false;
            skinDraw.invoke(null, ctx, skin, x, y, size);
            return true;
        } catch (Throwable t) {
            skinDraw = null; // больше не пробуем, рисуем точкой
            return false;
        }
    }

    private static ItemStack egg(EntityType<?> type) {
        return EGGS.computeIfAbsent(type, t -> {
            try {
                SpawnEggItem egg = SpawnEggItem.forEntity(t);
                return egg == null ? ItemStack.EMPTY : new ItemStack(egg);
            } catch (Throwable e) {
                return ItemStack.EMPTY;
            }
        });
    }

    /** Нарисовать предмет размером size×size (обычный значок — 16). */
    public static void drawItemScaled(DrawContext ctx, ItemStack st, int x, int y, int size) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        float s = size / 16f;
        m.scale(s, s);
        ctx.drawItem(st, 0, 0);
        m.popMatrix();
    }

    /**
     * Значок существа с центром в (cx, cy).
     * @param fallback цвет точки, если значка нет
     */
    public static void draw(DrawContext ctx, Entity e, int cx, int cy, int size, int fallback, double dy) {
        int x = cx - size / 2, y = cy - size / 2;
        boolean done = false;
        if (ClickerConfig.mmIcons) {
            if (e instanceof AbstractClientPlayerEntity pl) {
                ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
                done = drawFace(ctx, pl, x, y, size);
            } else if (e instanceof ItemEntity it) {
                drawItemScaled(ctx, it.getStack(), x, y, size);
                done = true;
            } else if (MobFaces.draw(ctx, e, x, y, size)) {
                done = true; // лицо моба из его текстуры
            } else {
                ItemStack egg = egg(e.getType());
                if (!egg.isEmpty()) {
                    drawItemScaled(ctx, egg, x, y, size);
                    done = true;
                }
            }
        }
        if (!done) {
            int r = Math.max(2, size / 3);
            ctx.fill(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, 0xFF000000);
            ctx.fill(cx - r, cy - r, cx + r, cy + r, fallback);
        }
        // выше / ниже: маленький треугольник над/под значком (без текста — не загромождает карту)
        if (ClickerConfig.mmHeight && Math.abs(dy) > 3) {
            boolean up = dy > 0;
            int col = up ? 0xFF7CF0FF : 0xFFFFB04C;
            int tipY = up ? y - 4 : y + size + 3;
            for (int i = 0; i < 3; i++) {
                int row = up ? tipY + i : tipY - i;
                ctx.fill(cx - i - 1, row, cx + i + 2, row + 1, 0xFF000000);
                ctx.fill(cx - i, row, cx + i + 1, row + 1, col);
            }
            if (ClickerConfig.mmHeightNum) {
                MinecraftClient mc = MinecraftClient.getInstance();
                String a = (up ? "+" : "") + (int) Math.round(dy);
                int ty = up ? tipY - 8 : tipY + 2;
                ctx.drawText(mc.textRenderer, Text.literal(a), cx + 4, ty, col, true);
            }
        }
    }

    /** Значок для предмета без существа (не используется для мобов). */
    public static ItemStack unknown() {
        return new ItemStack(Items.BARRIER);
    }
}
