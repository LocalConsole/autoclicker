package com.example.universalmod;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Содержимое шалкера в подсказке: по слотам или суммой по предметам. */
public class ShulkerTooltip {

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container == null) {
                return;
            }

            // Убираем ванильный короткий список ("... x64", "и ещё N...")
            lines.removeIf(ShulkerTooltip::isVanillaContainerLine);

            if (!ClickerConfig.shulkerPreview) {
                return;
            }

            if (ClickerConfig.shulkerSum) {
                // Режим "сумма": одинаковые предметы считаются вместе
                Map<Item, Integer> totals = new LinkedHashMap<>();
                Map<Item, Text> names = new LinkedHashMap<>();
                for (ItemStack s : container.iterateNonEmpty()) {
                    totals.merge(s.getItem(), s.getCount(), Integer::sum);
                    names.putIfAbsent(s.getItem(), s.getName());
                }
                for (Map.Entry<Item, Integer> e : totals.entrySet()) {
                    lines.add(Text.empty()
                            .append(names.get(e.getKey()))
                            .append(Text.literal(" x" + e.getValue()).formatted(Formatting.GRAY)));
                }
            } else {
                // Режим "по слотам": каждый стак отдельной строкой
                for (ItemStack s : container.iterateNonEmpty()) {
                    lines.add(Text.empty()
                            .append(s.getName())
                            .append(Text.literal(" x" + s.getCount()).formatted(Formatting.GRAY)));
                }
            }
        });
    }

    private static boolean isVanillaContainerLine(Text t) {
        if (t.getContent() instanceof TranslatableTextContent c) {
            return c.getKey().startsWith("item.container.");
        }
        return false;
    }
}
