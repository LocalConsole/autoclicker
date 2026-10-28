package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Перетаскивание прочности мышкой в любое место экрана (блоком или каждый предмет отдельно). */
public class ArmorHudMoveScreen extends Screen {
    private final Screen parent;
    private boolean dragging = false;
    private int dragSlot = -1; // -1 = весь блок
    private double grabX, grabY;

    public ArmorHudMoveScreen(Screen parent) {
        super(Text.literal(I18n.t("Перемещение")));
        this.parent = parent;
    }

    private void frame(DrawContext context, int bx, int by, int bw, int bh, int c) {
        int x = bx - 2, y = by - 2, x2 = bx + bw + 2, y2 = by + bh + 2;
        context.fill(x, y, x2, y + 1, c);
        context.fill(x, y2 - 1, x2, y2, c);
        context.fill(x, y, x + 1, y2, c);
        context.fill(x2 - 1, y, x2, y2, c);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ArmorHud.draw(context, true);
        if (ClickerConfig.armorSeparate) {
            for (int i = 0; i < ArmorHud.SLOTS; i++) {
                if (!ArmorHud.itemShown[i]) continue;
                frame(context, ArmorHud.itemX[i], ArmorHud.itemY[i], ArmorHud.itemW[i], ArmorHud.itemH[i],
                        dragging && dragSlot == i ? 0xFFFFD83A : 0xFFFFFFFF);
            }
        } else {
            frame(context, ArmorHud.boundsX, ArmorHud.boundsY, ArmorHud.boundsW, ArmorHud.boundsH,
                    dragging ? 0xFFFFD83A : 0xFFFFFFFF);
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(I18n.t("Зажмите предмет мышкой и перетащите. Esc — готово.")), this.width / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (ClickerConfig.armorSeparate) {
            for (int i = ArmorHud.SLOTS - 1; i >= 0; i--) {
                if (!ArmorHud.itemShown[i]) continue;
                if (mx >= ArmorHud.itemX[i] - 3 && mx <= ArmorHud.itemX[i] + ArmorHud.itemW[i] + 3
                        && my >= ArmorHud.itemY[i] - 3 && my <= ArmorHud.itemY[i] + ArmorHud.itemH[i] + 3) {
                    dragging = true;
                    dragSlot = i;
                    grabX = mx - ArmorHud.itemX[i];
                    grabY = my - ArmorHud.itemY[i];
                    return true;
                }
            }
        } else if (mx >= ArmorHud.boundsX - 4 && mx <= ArmorHud.boundsX + ArmorHud.boundsW + 4
                && my >= ArmorHud.boundsY - 4 && my <= ArmorHud.boundsY + ArmorHud.boundsH + 4) {
            dragging = true;
            dragSlot = -1;
            grabX = mx - ArmorHud.boundsX;
            grabY = my - ArmorHud.boundsY;
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            int w = dragSlot >= 0 ? ArmorHud.itemW[dragSlot] : ArmorHud.boundsW;
            int h = dragSlot >= 0 ? ArmorHud.itemH[dragSlot] : ArmorHud.boundsH;
            int freeW = Math.max(1, this.width - w);
            int freeH = Math.max(1, this.height - h);
            int px = clamp(Math.round((click.x() - grabX) / freeW * 1000));
            int py = clamp(Math.round((click.y() - grabY) / freeH * 1000));
            if (dragSlot >= 0) {
                ClickerConfig.armorPosX[dragSlot] = px;
                ClickerConfig.armorPosY[dragSlot] = py;
            } else {
                ClickerConfig.armorX = px;
                ClickerConfig.armorY = py;
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    private static int clamp(long v) {
        return (int) Math.max(0, Math.min(1000, v));
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        dragSlot = -1;
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        ClickerConfig.save();
        this.client.setScreen(parent);
    }
}
