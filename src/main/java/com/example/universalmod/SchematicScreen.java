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
    private boolean placedMode = false;

    private static class Row {
        Item item;
        final Set<Block> blocks = new LinkedHashSet<>();
        int need, missing, have, placed, wrong;
    }

    public SchematicScreen(Screen parent) {
        super(Text.literal(I18n.t("Схема: материалы")));
        this.parent = parent;
    }

    /** Кнопка «Схема» в инвентаре и в меню паузы (под кнопкой Universal Mod). */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            boolean inv = screen instanceof InventoryScreen || screen instanceof CreativeInventoryScreen;
            if (inv) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Universal Mod"),
                                b -> client.setScreen(new ClickerScreen(screen)))
                        .dimensions(6, 60, 90, 20).build());
            }
            if (inv || screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(I18n.t("Схемы")),
                                b -> client.setScreen(new SchematicListScreen(screen)))
                        .dimensions(6, 84, 90, 20)
                        .tooltip(Tooltip.of(Text.literal(I18n.t("Выбор схемы, превью, материалы"))))
                        .build());
                if (SchematicTool.schem != null) {
                    Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(I18n.t("Материалы")),
                                    b -> client.setScreen(new SchematicScreen(screen)))
                            .dimensions(6, 108, 90, 20)
                            .tooltip(Tooltip.of(Text.literal(I18n.t("Сколько каких блоков нужно и замена"))))
                            .build());
                }
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
        placedMode = placed;
        if (placed) {
            for (int i = 0; i < SchematicTool.entryCount(); i++) {
                BlockState st = SchematicTool.entryState(i);
                Row r = row(map, st);
                if (r == null) continue;
                BlockPos p = SchematicTool.entryPos(i);
                BlockState have = mc.world.getBlockState(p);
                if (have.equals(st)) {
                    r.placed++;
                } else {
                    r.missing++;
                    if (!have.isReplaceable() && have.getBlock() != st.getBlock()) r.wrong++;
                }
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

    // макет
    private int px, py, pw, ph;       // общая панель
    private int rightX, rightW;
    private static final int ACCENT = 0xFF56A8F5;
    private static final int GRID_COLS = 5;
    private static final int COL_W = 44;
    private static final String[] COLS = {"Всего", "Стоит", "Осталось", "Есть", "Нехватка"};
    private static final int CELL = 22;

    @Override
    protected void init() {
        collect();
        if (selected >= rows.size()) selected = -1;
        pw = Math.min(660, this.width - 16);
        ph = this.height - 16;
        px = (this.width - pw) / 2;
        py = 8;
        listX = px + 8;
        listW = Math.max(160, pw - 8 - GRID_COLS * CELL - 24);
        listY = py + 44;
        listRows = Math.max(1, (py + ph - 34 - listY) / ROW_H);
        first = Math.max(0, Math.min(first, Math.max(0, rows.size() - listRows)));
        rightX = listX + listW + 12;
        rightW = px + pw - 8 - rightX;

        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= rows.size()) break;
            Row r = rows.get(idx);
            String txt = new ItemStack(r.item).getName().getString();
            String nums = I18n.t("Всего в схеме: ") + r.need
                    + (placedMode ? "\n" + I18n.t("Уже стоит: ") + r.placed : "")
                    + (r.wrong > 0 ? "\n" + I18n.t("На месте другой блок: ") + r.wrong : "")
                    + "\n" + I18n.t("Есть в инвентаре: ") + r.have
                    + "\n" + I18n.t("Ещё поставить: ") + r.missing;
            int y = listY + i * ROW_H;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> {
                        selected = selected == idx ? -1 : idx;
                        clearAndInit();
                    })
                    .dimensions(listX, y, listW - 6, ROW_H - 2)
                    .tooltip(Tooltip.of(Text.literal(txt + "\n" + nums)))
                    .build());
        }

        // справа: чем заменить
        gridX = rightX + Math.max(0, (rightW - GRID_COLS * CELL) / 2);
        gridY = listY + 16;
        choices.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            Set<Item> seen = new LinkedHashSet<>();
            for (int i = 0; i < 36; i++) {
                ItemStack st = mc.player.getInventory().getStack(i);
                if (st.getItem() instanceof BlockItem && seen.add(st.getItem())) choices.add(new ItemStack(st.getItem()));
            }
        }
        for (int i = 0; i < choices.size(); i++) {
            ItemStack st = choices.get(i);
            int x = gridX + (i % GRID_COLS) * CELL, y = gridY + (i / GRID_COLS) * CELL;
            addDrawableChild(ButtonWidget.builder(Text.literal(""), b -> replaceWith(((BlockItem) st.getItem()).getBlock()))
                    .dimensions(x, y, CELL - 2, CELL - 2)
                    .tooltip(Tooltip.of(st.getName()))
                    .build());
        }
        int fy = gridY + Math.max(1, (choices.size() + GRID_COLS - 1) / GRID_COLS) * CELL + 18;
        int fw = Math.max(60, rightW - 66);
        idField = new TextFieldWidget(this.textRenderer, rightX, fy, fw, 18, Text.literal(""));
        idField.setMaxLength(100);
        idField.setPlaceholder(Text.literal(I18n.t("id блока, напр. stone")));
        addDrawableChild(idField);
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Заменить")), b -> replaceById())
                .dimensions(rightX + fw + 4, fy - 1, 62, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("← Схемы")),
                        b -> this.client.setScreen(new SchematicListScreen(parent)))
                .dimensions(px + 8, py + ph - 26, 90, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> close())
                .dimensions(this.width / 2 - 80, py + ph - 26, 160, 20).build());
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

    /** Панели рисуем под кнопками. */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        frame(ctx, px, py, pw, ph, 0xF0161B21);
        // шапка
        ctx.fill(px, py, px + pw, py + 30, 0xFF1E2630);
        ctx.fill(px, py + 30, px + pw, py + 31, ACCENT);
        // левая и правая области
        frame(ctx, listX - 3, listY - 14, listW + 3, listRows * ROW_H + 16, 0xFF11151A);
        frame(ctx, rightX - 3, listY - 14, rightW + 6, ph - (listY - py) - 20, 0xFF11151A);
    }

    private static void frame(DrawContext ctx, int x, int y, int w, int h, int bg) {
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2E3742);
        ctx.fill(x, y, x + w, y + h, bg);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        var tr = this.textRenderer;
        Schematic s = SchematicTool.schem;

        // шапка: значок, название, размер, общий прогресс
        context.drawItem(new ItemStack(SchematicTool.toolItem()), px + 6, py + 7);
        context.drawTextWithShadow(tr, this.title, px + 26, py + 5, 0xFFFFFFFF);
        if (s == null) {
            context.drawTextWithShadow(tr, Text.literal(I18n.t("Схема не выбрана")), px + 26, py + 17, 0xFFFFAA55);
            context.drawCenteredTextWithShadow(tr,
                    Text.literal(I18n.t("Возьмите стрелу → Shift до режима 2 → ЛКМ выбирает схему")),
                    this.width / 2, listY + 20, 0xFF9AA4AE);
            return;
        }
        String sub = s.name + "   " + s.sx + "×" + s.sy + "×" + s.sz;
        context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(sub, pw - 170)), px + 26, py + 17, 0xFF9AA4AE);
        int needAll = 0, missAll = 0;
        for (Row r : rows) {
            needAll += r.need;
            missAll += r.missing;
        }
        int bw = 130, bx = px + pw - bw - 10;
        SchematicTool.bar(context, bx, py + 17, bw, 7, needAll == 0 ? 1f : (needAll - missAll) / (float) needAll, 0xFF5BD38A);
        String prog = (needAll - missAll) + " / " + needAll;
        context.drawTextWithShadow(tr, Text.literal(prog), bx + bw - tr.getWidth(prog), py + 6, 0xFFDDDDDD);

        // список материалов: таблица
        context.drawTextWithShadow(tr, Text.literal(I18n.t("Материалы")), listX, listY - 11, 0xFFFFFFFF);
        int colRight = listX + listW - 10;
        for (int c = 0; c < COLS.length; c++) {
            String h = I18n.t(COLS[c]);
            int cx = colRight - (COLS.length - 1 - c) * COL_W;
            context.drawTextWithShadow(tr, Text.literal(h), cx - tr.getWidth(h), listY - 11, 0xFF9AA4AE);
        }
        if (rows.isEmpty()) {
            context.drawTextWithShadow(tr, Text.literal(I18n.t("Схема пустая")), listX + 4, listY + 4, 0xFF9AA4AE);
        }
        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= rows.size()) break;
            Row r = rows.get(idx);
            int x = listX, y = listY + i * ROW_H, w = listW - 6, h = ROW_H - 2;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
            int bg = idx == selected ? 0xFF24405F : hover ? 0xFF262E38 : (i % 2 == 0 ? 0xFF1A2027 : 0xFF161B21);
            context.fill(x, y, x + w, y + h, bg);
            if (idx == selected) context.fill(x, y, x + 2, y + h, ACCENT);
            context.fill(x + 3, y + 1, x + 19, y + 17, 0xFF0E1216);
            context.drawItem(new ItemStack(r.item), x + 3, y + 1);

            int lack = Math.max(0, r.missing - r.have);
            int status = r.missing == 0 ? 0xFF5BD38A : (lack == 0 ? 0xFFF2C94C : 0xFFFF6B6B);
            int nameW = colRight - (COLS.length - 1) * COL_W - COL_W - x - 26;
            String name = tr.trimToWidth(new ItemStack(r.item).getName().getString(), Math.max(20, nameW));
            context.drawTextWithShadow(tr, Text.literal(name), x + 23, y + 2, 0xFFFFFFFF);
            // полоска «сколько уже стоит»
            float done = r.need == 0 ? 1f : (r.need - r.missing) / (float) r.need;
            int barW = Math.max(20, nameW);
            context.fill(x + 23, y + 13, x + 23 + barW, y + 15, 0xFF2A313A);
            context.fill(x + 23, y + 13, x + 23 + Math.round(barW * done), y + 15, status);

            String[] vals = {
                    String.valueOf(r.need),
                    placedMode ? String.valueOf(r.placed) : "—",
                    String.valueOf(r.missing),
                    String.valueOf(r.have),
                    lack == 0 ? "✔" : String.valueOf(lack)
            };
            int[] cols = {
                    0xFFDDDDDD,
                    0xFF5BD38A,
                    r.missing == 0 ? 0xFF5BD38A : 0xFFF2C94C,
                    r.have > 0 ? 0xFFDDDDDD : 0xFF6F7A86,
                    lack == 0 ? 0xFF5BD38A : 0xFFFF6B6B
            };
            for (int c = 0; c < vals.length; c++) {
                int cx = colRight - (COLS.length - 1 - c) * COL_W;
                context.drawTextWithShadow(tr, Text.literal(vals[c]), cx - tr.getWidth(vals[c]), y + 5, cols[c]);
            }
            if (r.wrong > 0) {
                // на месте стоит другой блок — красная точка
                context.fill(x + 15, y + 1, x + 19, y + 5, 0xFFFF3030);
            }
        }
        // полоса прокрутки
        if (rows.size() > listRows) {
            int trackH = listRows * ROW_H - 2;
            int th = Math.max(12, trackH * listRows / rows.size());
            int ty = listY + (trackH - th) * first / Math.max(1, rows.size() - listRows);
            context.fill(listX + listW - 4, listY, listX + listW - 1, listY + trackH, 0xFF1E242B);
            context.fill(listX + listW - 4, ty, listX + listW - 1, ty + th, 0xFF56606C);
        }

        // правая часть
        String hint;
        if (selected < 0) {
            hint = I18n.t("Выберите блок слева");
        } else {
            hint = I18n.t("Заменить на:");
            ItemStack cur = new ItemStack(rows.get(selected).item);
            context.drawItem(cur, rightX + rightW - 18, listY - 13);
        }
        context.drawTextWithShadow(tr, Text.literal(hint), rightX, listY - 11, selected < 0 ? 0xFF9AA4AE : 0xFFFFFFFF);
        for (int i = 0; i < choices.size(); i++) {
            int x = gridX + (i % GRID_COLS) * CELL, y = gridY + (i / GRID_COLS) * CELL;
            boolean hover = mouseX >= x && mouseX < x + CELL - 2 && mouseY >= y && mouseY < y + CELL - 2;
            int border = selected < 0 ? 0xFF2E3742 : hover ? ACCENT : 0xFF3A4552;
            context.fill(x, y, x + CELL - 2, y + CELL - 2, border);
            context.fill(x + 1, y + 1, x + CELL - 3, y + CELL - 3, hover && selected >= 0 ? 0xFF2A3F57 : 0xFF0E1216);
            context.drawItem(choices.get(i), x + 2, y + 2);
        }
        if (choices.isEmpty()) {
            context.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(I18n.t("В инвентаре нет блоков"), rightW)),
                    rightX, gridY + 4, 0xFF9AA4AE);
        }
        context.drawTextWithShadow(tr, Text.literal(I18n.t("или по id:")), rightX, idField.getY() - 11, 0xFF9AA4AE);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
