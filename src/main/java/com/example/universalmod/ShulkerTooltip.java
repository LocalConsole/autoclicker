package com.example.universalmod;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
                // Режим "сумма": одинаковые предметы считаются вместе, переименованные — отдельными строками
                Map<String, ItemStack> first = new LinkedHashMap<>();
                Map<String, Integer> totals = new LinkedHashMap<>();
                for (ItemStack s : container.iterateNonEmpty()) {
                    Text custom = s.get(DataComponentTypes.CUSTOM_NAME);
                    String key = Registries.ITEM.getId(s.getItem()) + "|" + (custom == null ? "" : custom.getString());
                    totals.merge(key, s.getCount(), Integer::sum);
                    first.putIfAbsent(key, s);
                }
                for (Map.Entry<String, Integer> e : totals.entrySet()) {
                    lines.add(Text.empty()
                            .append(label(first.get(e.getKey())))
                            .append(Text.literal(" x" + e.getValue()).formatted(Formatting.GRAY)));
                }
            } else {
                // Режим "по слотам": каждый стак отдельной строкой
                for (ItemStack s : container.iterateNonEmpty()) {
                    lines.add(Text.empty()
                            .append(label(s))
                            .append(Text.literal(" x" + s.getCount()).formatted(Formatting.GRAY)));
                }
            }
        });
    }

    /** Название как в игре: у переименованного предмета оно курсивом, а рядом серым настоящее название. */
    private static Text label(ItemStack s) {
        if (s.get(DataComponentTypes.CUSTOM_NAME) != null) {
            return Text.empty()
                    .append(Text.empty().append(s.getName()).formatted(Formatting.ITALIC))
                    .append(Text.literal(" [").formatted(Formatting.GRAY))
                    .append(Text.empty().append(s.getItem().getName()).formatted(Formatting.GRAY))
                    .append(Text.literal("]").formatted(Formatting.GRAY))
                    .append(ClickerConfig.shulkerRenamedNote
                            ? Text.literal(" (" + I18n.t("переименован на наковальне") + ")").formatted(Formatting.DARK_GRAY)
                            : Text.empty());
        }
        return s.getName();
    }

    private static boolean isVanillaContainerLine(Text t) {
        if (t.getContent() instanceof TranslatableTextContent c) {
            return c.getKey().startsWith("item.container.");
        }
        return false;
    }
}
