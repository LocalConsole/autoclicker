package com.example.universalmod;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Материалы схемы: какие блоки нужны, сколько есть в инвентаре, сколько ещё не поставлено.
 * Выберите строку и нажмите блок справа (или введите id) — этот блок заменится во всей схеме.
 */
public class SchematicScreen extends Screen {
    private static final int ROW_H = 20;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private int first = 0;
    private int selected = -1;
    private TextFieldWidget idField;
    private final List<ItemStack> choices = new ArrayList<>();
    private int listX, listY, listW, listRows;
    private int gridX, gridY;

    private static class Row {
        Item item;
        final Set<Block> blocks = new LinkedHashSet<>();
        int need, missing, have;
    }

    public SchematicScreen(Screen parent) {
        super(Text.literal(I18n.t("Схема: материалы")));
        this.parent = parent;
    }

    /** Кнопка «Схема» в инвентаре и в меню паузы (под кнопкой Universal Mod). */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen
                    || screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(I18n.t("Схема")),
                                b -> client.setScreen(new SchematicScreen(screen)))
                        .dimensions(6, 84, 90, 20)
                        .tooltip(Tooltip.of(Text.literal(I18n.t("Материалы схемы и замена блоков"))))
                        .build());
            }
        });
    }

    private void collect() {
        rows.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        Map<Item, Row> map = new LinkedHashMap<>();
        Schematic s = SchematicTool.schem;
        if (s == null) return;
        boolean placed = mc.player != null && mc.world != null && SchematicTool.placedHere(mc);
        if (placed) {
            for (int i = 0; i < SchematicTool.entryCount(); i++) {
                BlockState st = SchematicTool.entryState(i);
                Row r = row(map, st);
                if (r == null) continue;
                BlockPos p = SchematicTool.entryPos(i);
                if (!mc.world.getBlockState(p).equals(st)) r.missing++;
            }
        } else {
            for (int b : s.blocks) {
                if (b == 0) continue;
                Row r = row(map, s.palette.get(b));
                if (r != null) r.missing++;
            }
        }
        if (mc.player != null) {
            for (Row r : map.values()) {
                for (int i = 0; i < 36; i++) {
                    ItemStack st = mc.player.getInventory().getStack(i);
                    if (st.isOf(r.item)) r.have += st.getCount();
                }
                if (mc.player.getOffHandStack().isOf(r.item)) r.have += mc.player.getOffHandStack().getCount();
            }
        }
        rows.addAll(map.values());
        rows.sort((a, b) -> Integer.compare(b.need, a.need));
    }

    private static Row row(Map<Item, Row> map, BlockState st) {
        if (st.isAir() || SchematicTool.secondaryPart(st)) return null;
        Item item = st.getBlock().asItem();
        if (item == Items.AIR) return null;
        Row r = map.computeIfAbsent(item, k -> {
            Row n = new Row();
            n.item = k;
            return n;
        });
        r.blocks.add(st.getBlock());
        r.need++;
        return r;
    }

    @Override
    protected void init() {
        collect();
        if (selected >= rows.size()) selected = -1;
        int cx = this.width / 2;
        listW = Math.min(250, cx - 20);
        listX = cx - listW - 8;
        listY = 30;
        listRows = Math.max(1, (this.height - 70) / ROW_H);
        first = Math.max(0, Math.min(first, Math.max(0, rows.size() - listRows)));

        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= rows.size()) break;
            Row r = rows.get(idx);
            String txt = new ItemStack(r.item).getName().getString();
            String nums = r.need + " | " + I18n.t("есть ") + r.have + " | " + I18n.t("нужно ") + r.missing;
            int y = listY + i * ROW_H;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                        selected = idx;
                        clearAndInit();
                    })
                    .dimensions(listX, y, listW, ROW_H - 1)
                    .tooltip(Tooltip.of(Text.literal(txt + "\n" + nums)))
                    .build());
        }

        // справа: чем заменить
        gridX = cx + 8;
        gridY = listY + 14;
        choices.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Set<Item> seen = new LinkedHashSet<>();
            for (int i = 0; i < 36; i++) {
                ItemStack st = mc.player.getInventory().getStack(i);
                if (st.getItem() instanceof BlockItem && seen.add(st.getItem())) choices.add(new ItemStack(st.getItem()));
            }
        }
        int cols = 8;
        for (int i = 0; i < choices.size(); i++) {
            ItemStack st = choices.get(i);
            int x = gridX + (i % cols) * 21, y = gridY + (i / cols) * 21;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> replaceWith(((BlockItem) st.getItem()).getBlock()))
                    .dimensions(x, y, 20, 20)
                    .tooltip(Tooltip.of(st.getName()))
                    .build());
        }
        int fy = gridY + Math.max(1, (choices.size() + cols - 1) / cols) * 21 + 16;
        idField = new TextFieldWidget(this.textRenderer, gridX, fy, 120, 18, Text.literal(""));
        idField.setMaxLength(100);
        idField.setPlaceholder(Text.literal(I18n.t("id блока, напр. stone")));
        addDrawableChild(idField);
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Заменить")), b -> replaceById())
                .dimensions(gridX + 124, fy - 1, 60, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> close())
                .dimensions(cx - 100, this.height - 26, 200, 20).build());
    }

    private void replaceById() {
        String t = idField.getText().trim().toLowerCase();
        if (t.isEmpty()) return;
        Identifier id = Identifier.tryParse(t.contains(":") ? t : "minecraft:" + t);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            idField.setText("");
            idField.setPlaceholder(Text.literal(I18n.t("Нет такого блока")));
            return;
        }
        replaceWith(Registries.BLOCK.get(id));
    }

    private void replaceWith(Block to) {
        if (selected < 0 || selected >= rows.size() || SchematicTool.schem == null) return;
        for (Block from : rows.get(selected).blocks) {
            if (from != to) SchematicTool.schem.replace(from, to);
        }
        SchematicTool.schematicChanged();
        selected = -1;
        clearAndInit();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, rows.size() - listRows);
        int nf = Math.max(0, Math.min(max, first - (int) Math.signum(verticalAmount)));
        if (nf != first) {
            first = nf;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
        Schematic s = SchematicTool.schem;
        if (s == null) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(I18n.t("Схема не выбрана: возьмите стрелу, Shift — режим 2, ЛКМ — выбрать схему")),
                    this.width / 2, 40, 0xFFFFAA55);
            return;
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(s.name + "  " + s.sx + "×" + s.sy + "×" + s.sz),
                listX, 18, 0xFFAAAAAA);
        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= rows.size()) break;
            Row r = rows.get(idx);
            int y = listY + i * ROW_H;
            if (idx == selected) context.fill(listX - 2, y - 1, listX + listW + 2, y + ROW_H, 0xFF3A7BD5);
            context.drawItem(new ItemStack(r.item), listX + 2, y + 1);
            String name = this.textRenderer.trimToWidth(new ItemStack(r.item).getName().getString(), listW - 120);
            context.drawTextWithShadow(this.textRenderer, Text.literal(name), listX + 22, y + 6, 0xFFFFFFFF);
            int col = r.missing == 0 ? 0xFF55FF55 : (r.have >= r.missing ? 0xFFFFFF55 : 0xFFFF5555);
            String nums = r.have + " / " + r.missing;
            context.drawTextWithShadow(this.textRenderer, Text.literal(nums),
                    listX + listW - 4 - this.textRenderer.getWidth(nums), y + 6, col);
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.t("есть / нужно ещё")),
                listX + listW - 4 - this.textRenderer.getWidth(I18n.t("есть / нужно ещё")), listY - 10, 0xFFAAAAAA);

        String hint = selected < 0 ? I18n.t("Выберите блок слева, чтобы заменить")
                : I18n.t("Заменить «") + new ItemStack(rows.get(selected).item).getName().getString() + I18n.t("» на:");
        context.drawTextWithShadow(this.textRenderer, Text.literal(this.textRenderer.trimToWidth(hint, this.width - gridX - 4)),
                gridX, listY, 0xFFFFFFFF);
        for (int i = 0; i < choices.size(); i++) {
            int x = gridX + (i % 8) * 21, y = gridY + (i / 8) * 21;
            context.drawItem(choices.get(i), x + 2, y + 2);
        }
        if (choices.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(I18n.t("В инвентаре нет блоков — введите id")),
                    gridX, gridY + 6, 0xFFAAAAAA);
        }
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
