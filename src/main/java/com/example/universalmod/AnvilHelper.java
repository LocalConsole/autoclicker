package com.example.universalmod;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Наковальня дешевле:
 *  - в подсказке каждого предмета и книги — сколько раз его уже чинили/зачаровывали на наковальне
 *    и какой из-за этого штраф (каждый раз штраф почти удваивается: 0, 1, 3, 7, 15, 31…);
 *  - в окне наковальни — порядок объединения книг из инвентаря с предметом, при котором
 *    штрафы растут меньше всего (книги сливаются парами, «дерево», а не по одной в предмет).
 */
public class AnvilHelper {
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!ClickerConfig.anvilHelper) return;
            Integer rc = stack.get(DataComponentTypes.REPAIR_COST);
            if (rc == null || rc <= 0) return;
            int uses = uses(rc);
            lines.add(Text.literal("⚒ " + I18n.t("Наковальня: ") + uses + I18n.t(" раз, штраф +") + rc + I18n.t(" ур."))
                    .formatted(rc >= 15 ? Formatting.RED : Formatting.GRAY));
        });
    }

    /** Сколько раз предмет был на наковальне: штраф = 2^n − 1. */
    public static int uses(int repairCost) {
        int n = 0;
        while (((1 << n) - 1) < repairCost && n < 31) n++;
        return n;
    }

    private static int penalty(ItemStack st) {
        Integer rc = st.get(DataComponentTypes.REPAIR_COST);
        return rc == null ? 0 : rc;
    }

    /** Стоимость книги как «жертвы»: уровень × множитель (у книг множитель вдвое меньше). */
    private static int bookValue(ItemStack st) {
        int v = 0;
        try {
            ItemEnchantmentsComponent c = st.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (c == null) return 1;
            for (RegistryEntry<Enchantment> e : c.getEnchantments()) {
                int mult = Math.max(1, e.value().getAnvilCost() / 2);
                v += mult * c.getLevel(e);
            }
        } catch (Throwable t) {
            return 1;
        }
        return Math.max(1, v);
    }

    private record Part(String name, int penaltyUses, int value, boolean item) { }

    private static int cost(int uses) {
        return (1 << uses) - 1;
    }

    /** Окно наковальни: план объединения. */
    public static void draw(DrawContext ctx, ItemStack target, int gx, int gy, int gw) {
        if (!ClickerConfig.anvilHelper) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        var tr = mc.textRenderer;
        int x = gx + gw + 4, y = gy;
        int w = Math.min(210, ctx.getScaledWindowWidth() - x - 4);
        if (w < 120) return;

        // книги из инвентаря
        List<Part> books = new ArrayList<>();
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isOf(Items.ENCHANTED_BOOK)) continue;
            String n = VillagerTrades.enchantText(s);
            books.add(new Part(n.isEmpty() ? s.getName().getString() : n, uses(penalty(s)), bookValue(s), false));
        }
        List<String> lines = new ArrayList<>();
        if (!target.isEmpty()) {
            int u = uses(penalty(target));
            lines.add(I18n.t("Предмет: ") + u + I18n.t(" раз на наковальне (штраф +") + cost(u) + ")");
        }
        if (books.size() < 1) {
            lines.add(I18n.t("Книг с зачарованиями в инвентаре нет."));
        } else {
            // сначала самые «дорогие» книги — они уходят жертвой раньше, пока штрафы малы
            books.sort(Comparator.comparingInt((Part p) -> p.penaltyUses()).thenComparingInt(p -> -p.value()));
            List<Part> level = new ArrayList<>(books);
            int step = 1, total = 0;
            lines.add(I18n.t("Лучший порядок (книги парами):"));
            while (level.size() > 1) {
                List<Part> next = new ArrayList<>();
                for (int i = 0; i + 1 < level.size(); i += 2) {
                    Part a = level.get(i), b = level.get(i + 1);
                    // жертвой — та, что дешевле: её стоимость платится
                    Part left = a.value() >= b.value() ? a : b, right = left == a ? b : a;
                    int c = cost(left.penaltyUses()) + cost(right.penaltyUses()) + right.value();
                    total += c;
                    lines.add(step++ + ") " + short_(left.name()) + " + " + short_(right.name()) + " ≈ " + c + I18n.t(" ур.")
                            + (c > 39 ? I18n.t(" (СЛИШКОМ ДОРОГО)") : ""));
                    next.add(new Part(left.name() + ", " + right.name(), Math.max(left.penaltyUses(), right.penaltyUses()) + 1,
                            left.value() + right.value(), false));
                }
                if (level.size() % 2 == 1) next.add(level.get(level.size() - 1));
                level = next;
            }
            Part all = level.get(0);
            int pu = target.isEmpty() ? 0 : uses(penalty(target));
            int c = cost(pu) + cost(all.penaltyUses()) + all.value();
            total += c;
            lines.add(step + ") " + I18n.t("предмет + всё вместе ≈ ") + c + I18n.t(" ур.") + (c > 39 ? I18n.t(" (СЛИШКОМ ДОРОГО)") : ""));
            lines.add(I18n.t("Всего примерно: ") + total + I18n.t(" ур."));
        }
        List<net.minecraft.text.OrderedText> wrapped = new ArrayList<>();
        for (String s : lines) wrapped.addAll(tr.wrapLines(Text.literal(s), w - 8));
        int h = 14 + wrapped.size() * 10 + 4;
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF6F7A86);
        ctx.fill(x, y, x + w, y + h, 0xF0161B21);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Наковальня дешевле")), x + 4, y + 3, 0xFFFFFFFF);
        int cy = y + 14;
        for (var l : wrapped) {
            ctx.drawTextWithShadow(tr, l, x + 4, cy, 0xFFDDDDDD);
            cy += 10;
        }
    }

    private static String short_(String s) {
        return s.length() > 28 ? s.substring(0, 27) + "…" : s;
    }
}
