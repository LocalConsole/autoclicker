package com.example.universalmod;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Выбор предмета мышкой: сверху то, что лежит у вас в инвентаре, плюс популярное (хорус, жемчуг, яблоки...).
 * Строка поиска ищет среди всех предметов игры по обычному названию.
 */
public class ItemPickerScreen extends Screen {
    private static final int CELL = 20;

    private final Screen parent;
    private final Consumer<String> onPick;
    private TextFieldWidget search;
    private final List<ItemStack> shown = new ArrayList<>();
    private List<ItemStack> allItems;
    private int scroll = 0;
    private int gx, gy, gw, gh, cols;

    public ItemPickerScreen(Screen parent, Consumer<String> onPick) {
        super(Text.literal(I18n.t("Выберите предмет")));
        this.parent = parent;
        this.onPick = onPick;
    }

    private static String idOf(Item item) {
        return Registries.ITEM.getId(item).toString();
    }

    @Override
    protected void init() {
        if (allItems == null) {
            allItems = new ArrayList<>();
            for (Item item : Registries.ITEM) {
                if (item == Items.AIR) continue;
                allItems.add(new ItemStack(item));
            }
        }
        int cx = this.width / 2;
        search = new TextFieldWidget(this.textRenderer, cx - 120, 28, 240, 20, Text.literal(""));
        search.setMaxLength(48);
        search.setPlaceholder(Text.literal(I18n.t("Поиск: хорус, жемчуг, яблоко...")));
        search.setChangedListener(t -> {
            scroll = 0;
            refilter();
        });
        addDrawableChild(search);
        setInitialFocus(search);

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Отмена")), b -> this.close())
                .dimensions(cx - 100, this.height - 28, 200, 20).build());

        gw = Math.min(this.width - 24, 20 * CELL);
        cols = Math.max(1, gw / CELL);
        gw = cols * CELL;
        gx = (this.width - gw) / 2;
        gy = 56;
        gh = this.height - 56 - 54;
        refilter();
    }

    private void refilter() {
        shown.clear();
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            // Предметы из инвентаря, затем популярное
            Map<Item, ItemStack> m = new LinkedHashMap<>();
            if (this.client != null && this.client.player != null) {
                for (int i = 0; i < 36; i++) {
                    ItemStack s = this.client.player.getInventory().getStack(i);
                    if (!s.isEmpty()) m.putIfAbsent(s.getItem(), new ItemStack(s.getItem()));
                }
                ItemStack off = this.client.player.getOffHandStack();
                if (!off.isEmpty()) m.putIfAbsent(off.getItem(), new ItemStack(off.getItem()));
            }
            Item[] pop = {Items.ENDER_PEARL, Items.CHORUS_FRUIT, Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                    Items.TOTEM_OF_UNDYING, Items.FIREWORK_ROCKET, Items.WIND_CHARGE, Items.SNOWBALL,
                    Items.COOKED_BEEF, Items.GOLDEN_CARROT, Items.POTION, Items.SPLASH_POTION,
                    Items.SHIELD, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.ENDER_EYE};
            for (Item i : pop) m.putIfAbsent(i, new ItemStack(i));
            shown.addAll(m.values());
        } else {
            for (ItemStack s : allItems) {
                String name = s.getName().getString().toLowerCase(Locale.ROOT);
                String id = Registries.ITEM.getId(s.getItem()).getPath();
                if (name.contains(q) || id.contains(q.replace(' ', '_'))) shown.add(s);
            }
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int totalRows() {
        return (shown.size() + cols - 1) / cols;
    }

    private int visibleRows() {
        return Math.max(1, gh / CELL);
    }

    private int maxScroll() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int cellAt(double mx, double my) {
        if (mx < gx || mx >= gx + gw || my < gy || my >= gy + visibleRows() * CELL) return -1;
        int col = (int) ((mx - gx) / CELL);
        int row = (int) ((my - gy) / CELL) + scroll;
        int idx = row * cols + col;
        return idx >= 0 && idx < shown.size() ? idx : -1;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            int idx = cellAt(click.x(), click.y());
            if (idx >= 0) {
                onPick.accept(idToPick(shown.get(idx)));
                this.client.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    private static String idToPick(ItemStack s) {
        return idOf(s.getItem());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        context.fill(gx - 2, gy - 2, gx + gw + 2, gy + visibleRows() * CELL + 2, 0x66000000);
        int hover = cellAt(mouseX, mouseY);
        int rows = visibleRows();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (r + scroll) * cols + c;
                if (idx >= shown.size()) break;
                int x = gx + c * CELL;
                int y = gy + r * CELL;
                if (idx == hover) context.fill(x, y, x + CELL, y + CELL, 0x80FFFFFF);
                context.drawItem(shown.get(idx), x + 2, y + 2);
            }
        }

        // Название под курсором
        String info;
        if (hover >= 0) info = shown.get(hover).getName().getString();
        else if (shown.isEmpty()) info = I18n.t("Ничего не найдено");
        else info = I18n.t("Нажмите на предмет, чтобы выбрать");
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(this.textRenderer.trimToWidth(info, this.width - 20)),
                this.width / 2, this.height - 46, hover >= 0 ? 0xFFFFD866 : 0xFFAAAAAA);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
