package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Прочность брони и предметов в руках прямо на экране. */
public class ArmorHud {
    public static final int SLOTS = 6; // шлем, нагрудник, поножи, ботинки, основная рука, вторая рука

    private record Entry(int slot, ItemStack stack, String text, int color) {}

    // Границы блока (режим «блоком») и отдельных предметов — для перетаскивания
    public static int boundsX, boundsY, boundsW, boundsH;
    public static final int[] itemX = new int[SLOTS];
    public static final int[] itemY = new int[SLOTS];
    public static final int[] itemW = new int[SLOTS];
    public static final int[] itemH = new int[SLOTS];
    public static final boolean[] itemShown = new boolean[SLOTS];

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(UniversalMod.MOD_ID, "durability_hud"),
                (context, tickCounter) -> {
                    if (ClickerConfig.armorHud) draw(context, false);
                });
    }

    private static boolean slotEnabled(int slot) {
        if (slot < 4) return ClickerConfig.armorShowArmor;
        return slot == 4 ? ClickerConfig.armorShowMain : ClickerConfig.armorShowOff;
    }

    private static ItemStack realStack(PlayerEntity p, int slot) {
        return switch (slot) {
            case 0 -> p.getEquippedStack(EquipmentSlot.HEAD);
            case 1 -> p.getEquippedStack(EquipmentSlot.CHEST);
            case 2 -> p.getEquippedStack(EquipmentSlot.LEGS);
            case 3 -> p.getEquippedStack(EquipmentSlot.FEET);
            case 4 -> p.getMainHandStack();
            default -> p.getOffHandStack();
        };
    }

    private static ItemStack sampleStack(int slot) {
        return new ItemStack(switch (slot) {
            case 0 -> Items.DIAMOND_HELMET;
            case 1 -> Items.DIAMOND_CHESTPLATE;
            case 2 -> Items.DIAMOND_LEGGINGS;
            case 3 -> Items.DIAMOND_BOOTS;
            case 4 -> Items.DIAMOND_SWORD;
            default -> Items.SHIELD;
        });
    }

    /** preview = true: рисуем образцы для пустых слотов (экран перемещения). */
    public static void draw(DrawContext context, boolean preview) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || (!preview && client.options.hudHidden)) return;

        for (int i = 0; i < SLOTS; i++) itemShown[i] = false;

        List<Entry> list = new ArrayList<>();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!slotEnabled(slot)) continue;
            ItemStack st = realStack(player, slot);
            Entry e = (!st.isEmpty() && st.isDamageable()) ? make(slot, st) : null;
            if (e == null && preview) {
                int[] pcts = {100, 75, 55, 30, 90, 20};
                e = sample(slot, pcts[slot]);
            }
            if (e != null) list.add(e);
        }
        if (list.isEmpty()) return;

        TextRenderer tr = client.textRenderer;
        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        if (ClickerConfig.armorSeparate) {
            // Каждый предмет в своём месте
            for (Entry e : list) {
                int w = 18 + tr.getWidth(e.text());
                int h = 18;
                int x = Math.round((sw - w) * ClickerConfig.armorPosX[e.slot()] / 1000f);
                int y = Math.round((sh - h) * ClickerConfig.armorPosY[e.slot()] / 1000f);
                drawEntry(context, tr, e, x, y);
                itemX[e.slot()] = x;
                itemY[e.slot()] = y;
                itemW[e.slot()] = w;
                itemH[e.slot()] = h;
                itemShown[e.slot()] = true;
            }
            return;
        }

        // Единый блок
        boolean vertical = ClickerConfig.armorVertical;
        int rowH = 18;
        int gap = 8;
        int blockW = 0;
        int blockH = 0;
        for (Entry e : list) {
            int w = 18 + tr.getWidth(e.text());
            if (vertical) {
                blockW = Math.max(blockW, w);
                blockH += rowH;
            } else {
                blockW += w + gap;
                blockH = rowH;
            }
        }
        if (!vertical) blockW -= gap;

        int x = Math.round((sw - blockW) * ClickerConfig.armorX / 1000f);
        int y = Math.round((sh - blockH) * ClickerConfig.armorY / 1000f);
        boundsX = x;
        boundsY = y;
        boundsW = blockW;
        boundsH = blockH;

        for (Entry e : list) {
            drawEntry(context, tr, e, x, y);
            if (vertical) {
                y += rowH;
            } else {
                x += 18 + tr.getWidth(e.text()) + gap;
            }
        }
    }

    private static void drawEntry(DrawContext context, TextRenderer tr, Entry e, int x, int y) {
        context.drawItem(e.stack(), x, y);
        context.drawTextWithShadow(tr, e.text(), x + 18, y + 5, e.color());
    }

    private static Entry make(int slot, ItemStack stack) {
        int max = stack.getMaxDamage();
        int left = max - stack.getDamage();
        float percent = max <= 0 ? 100f : left * 100f / max;
        return new Entry(slot, stack, format(left, max, percent), color(percent));
    }

    private static Entry sample(int slot, int percent) {
        ItemStack st = sampleStack(slot);
        int max = Math.max(1, st.getMaxDamage());
        int left = Math.round(max * percent / 100f);
        return new Entry(slot, st, format(left, max, percent), color(percent));
    }

    private static String format(int left, int max, float percent) {
        if (ClickerConfig.armorPercent) return Math.round(percent) + "%";
        return ClickerConfig.armorShowMax ? left + "/" + max : String.valueOf(left);
    }

    private static int color(float percent) {
        if (!ClickerConfig.armorColors) return 0xFFFFFFFF;
        if (percent > 50f) return 0xFF55FF55;      // зелёный
        if (percent > 25f) return 0xFFFFFF55;      // жёлтый
        return 0xFFFF5555;                         // красный
    }
}
