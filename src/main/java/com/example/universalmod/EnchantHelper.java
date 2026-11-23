package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.EnchantmentScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * Стол зачарований без загадок. Игра заранее знает «семя» зачарования игрока и сообщает его клиенту,
 * поэтому можно посчитать ТОЧНО то же, что посчитает сервер:
 *  - полный список зачарований для каждой из трёх кнопок (а не одно «зачарование ...?»);
 *  - что выпадет при любом количестве книжных полок от 0 до 15 — с поиском нужного зачарования
 *    (например «починка»): видно, сколько полок поставить и какую кнопку нажать.
 * После каждого зачарования семя меняется — таблица пересчитывается сама.
 */
public class EnchantHelper {
    private static int cacheSeed = Integer.MIN_VALUE;
    private static ItemStack cacheItem = ItemStack.EMPTY;
    private static String cacheTarget = null;
    private static List<String[]> cacheRows = new ArrayList<>(); // [полки, кнопка, уровни, текст]

    private static List<EnchantmentLevelEntry> generate(MinecraftClient mc, ItemStack stack, int seed, int slot, int level) {
        Random random = Random.create((long) seed + slot);
        var reg = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var tag = reg.getOptional(EnchantmentTags.IN_ENCHANTING_TABLE);
        if (tag.isEmpty()) return new ArrayList<>();
        List<EnchantmentLevelEntry> list = EnchantmentHelper.generateEnchantments(random, stack, level, tag.get().stream());
        if (stack.isOf(Items.BOOK) && list.size() > 1) list.remove(random.nextInt(list.size()));
        return list;
    }

    private static String text(List<EnchantmentLevelEntry> list) {
        StringBuilder sb = new StringBuilder();
        for (EnchantmentLevelEntry e : list) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(Enchantment.getName(e.enchantment(), e.level()).getString());
        }
        return sb.toString();
    }

    /** Уровни трёх кнопок при данном числе полок — так же, как считает игра. */
    private static int[] powers(ItemStack stack, int seed, int shelves) {
        Random r = Random.create(seed);
        int[] p = new int[3];
        for (int j = 0; j < 3; j++) {
            p[j] = EnchantmentHelper.calculateRequiredExperienceLevel(r, j, shelves, stack);
            if (p[j] < j + 1) p[j] = 0;
        }
        return p;
    }

    private static void rebuild(MinecraftClient mc, ItemStack stack, int seed) {
        cacheRows = new ArrayList<>();
        String target = ClickerConfig.enchantTarget.trim();
        for (int shelves = 15; shelves >= 0; shelves--) {
            int[] p = powers(stack, seed, shelves);
            for (int j = 2; j >= 0; j--) {
                if (p[j] <= 0) continue;
                String t = text(generate(mc, stack, seed, j, p[j]));
                if (t.isEmpty()) continue;
                if (target.isEmpty() ? j == 2 : SearchMatcher.matches(t, target)) {
                    cacheRows.add(new String[]{String.valueOf(shelves), String.valueOf(j + 1), String.valueOf(p[j]), t});
                    if (target.isEmpty()) break;
                }
            }
        }
        cacheSeed = seed;
        cacheItem = stack.copy();
        cacheTarget = target;
    }

    public static void draw(DrawContext ctx, EnchantmentScreen screen, int gx, int gy, int gw, int gh) {
        if (!ClickerConfig.enchantHelper) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        EnchantmentScreenHandler h = screen.getScreenHandler();
        ItemStack stack = h.getSlot(0).getStack();
        var tr = mc.textRenderer;
        int x = gx + gw + 4, y = gy;
        int w = Math.min(230, ctx.getScaledWindowWidth() - x - 4);
        if (w < 120) return;
        if (stack.isEmpty() || !stack.isEnchantable()) {
            box(ctx, x, y, w, 24);
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Положите предмет — покажу всё,")), x + 4, y + 3, 0xFF9AA4AE);
            ctx.drawTextWithShadow(tr, Text.literal(I18n.t("что выпадет на каждой кнопке")), x + 4, y + 13, 0xFF9AA4AE);
            return;
        }
        int seed = h.getSeed();
        List<String> now = new ArrayList<>();
        int[] cur = h.enchantmentPower;
        try {
            for (int j = 0; j < 3; j++) {
                if (cur[j] <= 0) {
                    now.add((j + 1) + ": —");
                    continue;
                }
                now.add((j + 1) + " (" + cur[j] + I18n.t(" ур.") + "): " + text(generate(mc, stack, seed, j, cur[j])));
            }
            if (seed != cacheSeed || !ItemStack.areItemsAndComponentsEqual(stack, cacheItem)
                    || !ClickerConfig.enchantTarget.trim().equals(cacheTarget)) {
                rebuild(mc, stack, seed);
            }
        } catch (Throwable t) {
            return;
        }

        List<net.minecraft.text.OrderedText> lines = new ArrayList<>();
        for (String s : now) lines.addAll(tr.wrapLines(Text.literal(s), w - 8));
        String target = ClickerConfig.enchantTarget.trim();
        String head2 = target.isEmpty() ? I18n.t("Кнопка 3 при разном числе полок:")
                : I18n.t("Где выпадет «") + target + "»:";
        List<net.minecraft.text.OrderedText> rows = new ArrayList<>();
        for (String[] r : cacheRows) {
            rows.addAll(tr.wrapLines(Text.literal(r[0] + I18n.t(" полок → кнопка ") + r[1] + " (" + r[2] + "): " + r[3]), w - 8));
            if (rows.size() > 18) break;
        }
        if (rows.isEmpty()) rows.add(Text.literal(I18n.t("Ни при каком числе полок. Зачаруйте что-то дешёвое — семя сменится")).asOrderedText());
        int hh = 14 + lines.size() * 10 + 16 + rows.size() * 10 + 4;
        box(ctx, x, y, w, hh);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Сейчас выпадет:")), x + 4, y + 3, 0xFFFFFFFF);
        int cy = y + 14;
        for (var l : lines) {
            ctx.drawTextWithShadow(tr, l, x + 4, cy, 0xFFB57BEE);
            cy += 10;
        }
        cy += 4;
        ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(head2, w - 8)), x + 4, cy, 0xFFF2C94C);
        cy += 12;
        for (var l : rows) {
            ctx.drawTextWithShadow(tr, l, x + 4, cy, 0xFFDDDDDD);
            cy += 10;
        }
    }

    private static void box(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF7A5BA8);
        ctx.fill(x, y, x + w, y + h, 0xF0161B21);
    }

    /** Чтобы не было предупреждения о неиспользуемом импорте на некоторых версиях. */
    static RegistryEntry<Enchantment> unused() {
        return null;
    }
}
