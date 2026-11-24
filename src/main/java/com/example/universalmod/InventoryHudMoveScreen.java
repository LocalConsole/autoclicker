package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Перетаскивание инвентаря на экране мышкой в любое место (как прочность брони). */
public class InventoryHudMoveScreen extends Screen {
    private final Screen parent;
    private boolean dragging = false;
    private int dragRow = -1; // -1 — весь блок
    private double grabX, grabY;

    public InventoryHudMoveScreen(Screen parent) {
        super(Text.literal(I18n.t("Перемещение инвентаря")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Вернуть над хотбар")), b -> {
            ClickerConfig.invHudFree = false;
            ClickerConfig.save();
        }).dimensions(this.width / 2 - 154, this.height - 28, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> close())
                .dimensions(this.width / 2 + 4, this.height - 28, 150, 20).build());
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
        InventoryHud.draw(context, true);
        if (ClickerConfig.invHudSeparate) {
            for (int r = 0; r < InventoryHud.ROWS; r++) {
                if (!InventoryHud.rowShown[r]) continue;
                frame(context, InventoryHud.rowX[r], InventoryHud.rowY[r], InventoryHud.rowW[r], InventoryHud.rowH[r],
                        dragging && dragRow == r ? 0xFFFFD83A : 0xFFFFFFFF);
                String n = r == 3 ? I18n.t("хотбар") : String.valueOf(r + 1);
                context.drawTextWithShadow(this.textRenderer, Text.literal(n), InventoryHud.rowX[r], InventoryHud.rowY[r] - 12, 0xFFFFD866);
            }
        } else if (InventoryHud.boundsW > 0) {
            frame(context, InventoryHud.boundsX, InventoryHud.boundsY, InventoryHud.boundsW, InventoryHud.boundsH,
                    dragging ? 0xFFFFD83A : 0xFFFFFFFF);
        }
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(I18n.t("Перетащите мышкой, колёсико — размер (") + ClickerConfig.invHudScale + I18n.t("%). Esc — готово.")),
                this.width / 2, 10, 0xFFFFFFFF);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x - 4 && mx <= x + w + 4 && my >= y - 4 && my <= y + h + 4;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true;
        double mx = click.x(), my = click.y();
        if (ClickerConfig.invHudSeparate) {
            for (int r = InventoryHud.ROWS - 1; r >= 0; r--) {
                if (!InventoryHud.rowShown[r]) continue;
                if (in(mx, my, InventoryHud.rowX[r], InventoryHud.rowY[r], InventoryHud.rowW[r], InventoryHud.rowH[r])) {
                    dragging = true;
                    dragRow = r;
                    grabX = mx - InventoryHud.rowX[r];
                    grabY = my - InventoryHud.rowY[r];
                    return true;
                }
            }
            return false;
        }
        if (in(mx, my, InventoryHud.boundsX, InventoryHud.boundsY, InventoryHud.boundsW, InventoryHud.boundsH)) {
            dragging = true;
            dragRow = -1;
            grabX = mx - InventoryHud.boundsX;
            grabY = my - InventoryHud.boundsY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            int w = dragRow >= 0 ? InventoryHud.rowW[dragRow] : InventoryHud.boundsW;
            int h = dragRow >= 0 ? InventoryHud.rowH[dragRow] : InventoryHud.boundsH;
            int freeW = Math.max(1, this.width - w);
            int freeH = Math.max(1, this.height - h);
            int px = clamp(Math.round((click.x() - grabX) / freeW * 1000));
            int py = clamp(Math.round((click.y() - grabY) / freeH * 1000));
            if (dragRow >= 0) {
                ClickerConfig.invHudRowX[dragRow] = px;
                ClickerConfig.invHudRowY[dragRow] = py;
            } else {
                ClickerConfig.invHudFree = true;
                ClickerConfig.invHudX = px;
                ClickerConfig.invHudY = py;
            }
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    private static int clamp(long v) {
        return (int) Math.max(0, Math.min(1000, v));
    }

    /** Колёсико — больше/меньше. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        ClickerConfig.invHudScale = Math.max(50, Math.min(200, ClickerConfig.invHudScale + (verticalAmount > 0 ? 10 : -10)));
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        ClickerConfig.save();
        this.client.setScreen(parent);
    }
}
