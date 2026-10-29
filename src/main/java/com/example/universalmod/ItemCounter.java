package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/** Сколько всего предмета из основной руки у вас в инвентаре, отдельно сколько из них лежит в шалкерах. */
public class ItemCounter {
    public static void register() {
        HudElementRegistry.addLast(Identifier.of(UniversalMod.MOD_ID, "item_counter"),
                (context, tickCounter) -> draw(context));
    }

    private static void draw(DrawContext ctx) {
        if (!ClickerConfig.itemCounter) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity p = mc.player;
        if (p == null || mc.options.hudHidden) return;

        ItemStack held = p.getMainHandStack();
        if (held.isEmpty() || held.getMaxCount() <= 1) return;   // только то, что складывается в стаки
        Item item = held.getItem();

        int direct = 0;
        int inBoxes = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.isOf(item)) direct += s.getCount();
            ContainerComponent c = s.get(DataComponentTypes.CONTAINER);
            if (c != null) {
                for (ItemStack in : c.iterateNonEmpty()) {
                    if (in.isOf(item)) inBoxes += in.getCount();
                }
            }
        }

        String text = I18n.t("Всего: ") + (direct + inBoxes);
        if (inBoxes > 0) text += "  (" + I18n.t("в шалкерах: ") + inBoxes + ")";

        TextRenderer tr = mc.textRenderer;
        int x = (ctx.getScaledWindowWidth() - tr.getWidth(text)) / 2;
        int y = ctx.getScaledWindowHeight() - 72;
        ctx.drawTextWithShadow(tr, text, x, y, 0xFFE0E0E0);
    }
}
