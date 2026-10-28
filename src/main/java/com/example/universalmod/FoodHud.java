package com.example.universalmod;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Показ насыщения, предпросмотр еды и значения еды в подсказке (в духе AppleSkin). */
public class FoodHud {
    private static final Identifier FOOD_FULL = Identifier.ofVanilla("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.ofVanilla("hud/food_half");

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.FOOD_BAR,
                Identifier.of(UniversalMod.MOD_ID, "food_overlay"),
                (context, tickCounter) -> render(context));

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, type, lines) -> {
            if (!ClickerConfig.foodHud || !ClickerConfig.foodTooltip) return;
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null) return;
            lines.add(Text.literal(I18n.t("Голод: +") + food.nutrition()
                    + I18n.t("   Насыщение: +") + String.format("%.1f", food.saturation()))
                    .formatted(Formatting.GOLD));
        });
    }

    private static FoodComponent heldFood(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        FoodComponent f = main.get(DataComponentTypes.FOOD);
        if (f != null) return f;
        return player.getOffHandStack().get(DataComponentTypes.FOOD);
    }

    private static void render(DrawContext context) {
        if (!ClickerConfig.foodHud) return;
        if (!ClickerConfig.foodSaturation && !ClickerConfig.foodPreview && !ClickerConfig.foodSatNumber) return;
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || client.options.hudHidden) return;
        if (client.interactionManager == null || !client.interactionManager.hasStatusBars()) return;
        if (player.getVehicle() instanceof LivingEntity) return; // вместо голода показывается здоровье коня

        int right = context.getScaledWindowWidth() / 2 + 91;
        int top = context.getScaledWindowHeight() - 39;

        int food = player.getHungerManager().getFoodLevel();
        float sat = player.getHungerManager().getSaturationLevel();

        // Предпросмотр: сколько голода вернёт еда в руке
        if (ClickerConfig.foodPreview) {
            FoodComponent held = heldFood(player);
            if (held != null && (food < 20 || held.canAlwaysEat())) {
                int target = Math.min(20, food + held.nutrition());
                float alpha = 0.35f + 0.3f * (float) Math.sin(System.currentTimeMillis() / 250.0);
                int color = ((int) (Math.max(0f, Math.min(1f, alpha)) * 255) << 24) | 0xFFFFFF;
                for (int i = 0; i < 10; i++) {
                    int low = i * 2;
                    if (food >= low + 2) continue; // иконка и так полная
                    int x = right - i * 8 - 9;
                    if (target >= low + 2) {
                        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_FULL, x, top, 9, 9, color);
                    } else if (target == low + 1) {
                        context.drawGuiTexture(RenderPipelines.GUI_TEXTURED, FOOD_HALF, x, top, 9, 9, color);
                    }
                }
            }
        }

        // Насыщение числом над полоской голода
        if (ClickerConfig.foodSatNumber) {
            String t = I18n.t("Насыщение: ") + String.format("%.1f", sat);
            int tw = client.textRenderer.getWidth(t);
            context.drawTextWithShadow(client.textRenderer, t, right - tw, top - 11, 0xFFFFD83A);
        }

        // Насыщение: золотая рамка на иконках голода
        if (ClickerConfig.foodSaturation && sat > 0) {
            int gold = 0xFFFFD83A;
            int points = (int) Math.ceil(Math.min(sat, 20f));
            for (int i = 0; i < 10; i++) {
                int low = i * 2;
                if (points <= low) break;
                int x = right - i * 8 - 9;
                boolean half = points == low + 1;
                int xr = half ? x + 5 : x + 9;
                context.fill(x + 1, top, xr - (half ? 0 : 1), top + 1, gold);
                context.fill(x + 1, top + 8, xr - (half ? 0 : 1), top + 9, gold);
                context.fill(x, top + 1, x + 1, top + 8, gold);
                if (!half) context.fill(x + 8, top + 1, x + 9, top + 8, gold);
            }
        }
    }
}
