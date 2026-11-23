package com.example.universalmod;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Comparator;
import java.util.List;

/** Меню системы ценности: уровни, слоты, совпадение, настройки перекладки. */
public class ValueScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int ACCENT = 0xFFF2C94C;

    private final Screen parent;
    private int first = 0;
    private int px, py, pw, ph, listY, listRows;
    private int colTier, colSlot, colMatch, colDel;
    private List<OrderedText> help;

    public ValueScreen(Screen parent) {
        super(Text.literal(I18n.t("Ценность предметов")));
        this.parent = parent;
    }

    private static String onOff(boolean b) {
        return b ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ");
    }

    @Override
    protected void init() {
        ValueSystem.rules.sort(Comparator.comparingInt((ValueSystem.Rule r) -> r.slot).thenComparingInt(r -> -r.tier));
        pw = Math.min(600, this.width - 16);
        ph = this.height - 16;
        px = (this.width - pw) / 2;
        py = 8;

        String key = ValueSystem.markKey == null || ValueSystem.markKey.isUnbound() ? I18n.t("(назначьте клавишу «Ценность: отметить»)")
                : KeyBindingHelper.getBoundKeyOf(ValueSystem.markKey).getLocalizedText().getString();
        help = this.textRenderer.wrapLines(Text.literal(I18n.t("В инвентаре или сундуке наведите на предмет, нажмите ") + key
                + I18n.t(", затем цифру слота 1–9 (F — вторая рука). Ещё раз — уровень выше. Чем выше уровень, тем ценнее: в слот всегда кладётся самое ценное.")),
                pw - 40);

        // строка настроек
        int sy = py + 34 + help.size() * 10;
        int n = 5, gap = 4, bw = (pw - 16 - gap * (n - 1)) / n, bx = px + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Авто: ") + onOff(ClickerConfig.valueAuto)), b -> {
            ClickerConfig.valueAuto = !ClickerConfig.valueAuto;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(bx, sy, bw, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Сразу при открытии сундука, шалкера, эндер-сундука перекладывать ценное в его слоты")))).build());
        bx += bw + gap;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Повторов: ") + ClickerConfig.valueRetries), b -> {
            ClickerConfig.valueRetries = (ClickerConfig.valueRetries + 1) % 11;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(bx, sy, bw, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Сколько раз повторить, если предмет не встал в слот (сервер не принял). Нажмите — больше, после 10 — снова 0")))).build());
        bx += bw + gap;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Пауза: ") + ClickerConfig.valueDelay + I18n.t(" т.")), b -> {
            ClickerConfig.valueDelay = (ClickerConfig.valueDelay + 1) % 11;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(bx, sy, bw, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Пауза между перекладками в тиках (20 тиков = 1 секунда). Больше — безопаснее на строгих серверах")))).build());
        bx += bw + gap;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Курсором: ") + onOff(ClickerConfig.valueCursor)), b -> {
            ClickerConfig.valueCursor = !ClickerConfig.valueCursor;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(bx, sy, bw, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Если все повторы не помогли — курсор мыши сам берёт предмет, кладёт в слот и возвращается на место")))).build());
        bx += bw + gap;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Значки: ") + onOff(ClickerConfig.valueMarks)), b -> {
            ClickerConfig.valueMarks = !ClickerConfig.valueMarks;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(bx, sy, bw, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Цифра уровня в углу ценных предметов и золотая рамка у назначенных слотов")))).build());

        // список
        listY = sy + 40;
        listRows = Math.max(1, (py + ph - 34 - listY) / ROW_H);
        first = Math.max(0, Math.min(first, Math.max(0, ValueSystem.rules.size() - listRows)));
        int right = px + pw - 10;
        colDel = right - 20;
        colMatch = colDel - 4 - 64;
        colSlot = colMatch - 4 - 74;
        colTier = colSlot - 6 - 110;
        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= ValueSystem.rules.size()) break;
            ValueSystem.Rule r = ValueSystem.rules.get(idx);
            int y = listY + i * ROW_H + 1;
            addDrawableChild(ButtonWidget.builder(Text.literal("−"), b -> {
                r.tier = Math.max(1, r.tier - 1);
                ValueSystem.save();
                clearAndInit();
            }).dimensions(colTier, y, 16, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
                r.tier = Math.min(ValueSystem.MAX_TIER, r.tier + 1);
                ValueSystem.save();
                clearAndInit();
            }).dimensions(colTier + 94, y, 16, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(ValueSystem.slotName(r.slot)), b -> {
                r.slot = r.slot == ValueSystem.OFFHAND ? 0 : (r.slot == 8 ? ValueSystem.OFFHAND : r.slot + 1);
                ValueSystem.save();
                clearAndInit();
            }).dimensions(colSlot, y, 74, 18).tooltip(Tooltip.of(Text.literal(I18n.t("Куда класть. Нажмите — следующий слот")))).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(r.exact ? I18n.t("Точно") : I18n.t("Любой")), b -> {
                r.exact = !r.exact;
                ValueSystem.save();
                clearAndInit();
            }).dimensions(colMatch, y, 64, 18).tooltip(Tooltip.of(Text.literal(r.exact
                    ? I18n.t("Только именно этот предмет: то же имя и та же текстура (для серверных предметов)")
                    : I18n.t("Любой предмет этого вида, с любым именем и текстурой")))).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> {
                ValueSystem.rules.remove(r);
                ValueSystem.save();
                clearAndInit();
            }).dimensions(colDel, y, 20, 18).tooltip(Tooltip.of(Text.literal(I18n.t("Убрать из ценных")))).build());
        }

        int fy = py + ph - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("+ Предмет из руки")), b -> {
            if (this.client == null || this.client.player == null) return;
            ItemStack st = this.client.player.getMainHandStack();
            if (st.isEmpty()) return;
            ValueSystem.assign(this.client, st, this.client.player.getInventory().getSelectedSlot());
            clearAndInit();
        }).dimensions(px + 8, fy, 150, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Предмет из основной руки станет ценным для этого же слота")))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> close())
                .dimensions(px + pw - 128, fy, 120, 20).build());
    }

    private static ItemStack icon(ValueSystem.Rule r) {
        Identifier id = Identifier.tryParse(r.item);
        ItemStack st = new ItemStack(id == null ? Items.BARRIER : Registries.ITEM.get(id));
        try {
            if (!r.model.isEmpty()) {
                Identifier m = Identifier.tryParse(r.model);
                if (m != null) st.set(DataComponentTypes.ITEM_MODEL, m);
            }
        } catch (Throwable ignored) {
        }
        return st;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, ValueSystem.rules.size() - listRows);
        int nf = Math.max(0, Math.min(max, first - (int) Math.signum(verticalAmount)));
        if (nf != first) {
            first = nf;
            clearAndInit();
        }
        return true;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF2E3742);
        ctx.fill(px, py, px + pw, py + ph, 0xF0161B21);
        ctx.fill(px, py, px + pw, py + 22, 0xFF1E2630);
        ctx.fill(px, py + 22, px + pw, py + 23, ACCENT);
        int top = listY - 14, bottom = listY + listRows * ROW_H + 2;
        ctx.fill(px + 7, top - 1, px + pw - 7, bottom + 1, 0xFF2E3742);
        ctx.fill(px + 8, top, px + pw - 8, bottom, 0xFF11151A);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var tr = this.textRenderer;
        ctx.drawItem(new ItemStack(Items.NETHER_STAR), px + 4, py + 3);
        ctx.drawTextWithShadow(tr, this.title, px + 24, py + 7, 0xFFFFFFFF);
        String cnt = I18n.t("Предметов: ") + ValueSystem.rules.size();
        ctx.drawTextWithShadow(tr, Text.literal(cnt), px + pw - 8 - tr.getWidth(cnt), py + 7, 0xFF9AA4AE);
        int hy = py + 28;
        for (OrderedText l : help) {
            ctx.drawTextWithShadow(tr, l, px + 10, hy, 0xFFB8C0C8);
            hy += 10;
        }

        // заголовки столбцов
        int hy2 = listY - 11;
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Предмет")), px + 14, hy2, 0xFF9AA4AE);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Уровень")), colTier + 55 - tr.getWidth(I18n.t("Уровень")) / 2, hy2, 0xFF9AA4AE);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Слот")), colSlot + 37 - tr.getWidth(I18n.t("Слот")) / 2, hy2, 0xFF9AA4AE);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Какой")), colMatch + 32 - tr.getWidth(I18n.t("Какой")) / 2, hy2, 0xFF9AA4AE);

        if (ValueSystem.rules.isEmpty()) {
            ctx.drawCenteredTextWithShadow(tr, Text.literal(I18n.t("Пока нет ценных предметов")), this.width / 2, listY + 10, 0xFF9AA4AE);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(I18n.t("Отметьте предмет клавишей в инвентаре или нажмите «+ Предмет из руки»")),
                    this.width / 2, listY + 22, 0xFF6F7A86);
        }
        int prevSlot = -99;
        for (int i = 0; i < listRows; i++) {
            int idx = first + i;
            if (idx >= ValueSystem.rules.size()) break;
            ValueSystem.Rule r = ValueSystem.rules.get(idx);
            int y = listY + i * ROW_H;
            int x = px + 10;
            boolean hover = mouseX >= x && mouseX < px + pw - 10 && mouseY >= y && mouseY < y + ROW_H;
            if (hover) ctx.fill(x, y, colTier - 4, y + ROW_H - 1, 0x22FFFFFF);
            if (r.slot != prevSlot && i > 0) ctx.fill(x, y - 1, px + pw - 10, y, 0xFF2E3742); // разделитель групп слотов
            prevSlot = r.slot;
            int col = ValueSystem.TIER_COLORS[r.tier];
            ctx.fill(x, y + 1, x + 2, y + ROW_H - 2, col);
            ctx.fill(x + 4, y + 2, x + 22, y + 20, 0xFF0E1216);
            ctx.drawItem(icon(r), x + 5, y + 3);
            int nameW = colTier - 8 - (x + 26);
            String name = tr.trimToWidth(ValueSystem.label(r), nameW);
            ctx.drawTextWithShadow(tr, Text.literal(name), x + 26, y + 3, 0xFFFFFFFF);
            String sub = r.exact ? (r.name.isEmpty() && r.model.isEmpty() ? r.item : I18n.t("особый предмет сервера")) : I18n.t("любой ") + r.item;
            ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(sub, nameW)), x + 26, y + 12, 0xFF6F7A86);
            // уровень между − и +
            String t = "★" + r.tier + " " + I18n.t(ValueSystem.TIER_NAMES[r.tier]);
            t = tr.trimToWidth(t, 74);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(t), colTier + 55, y + 6, col);
        }
        // полоса прокрутки
        int total = ValueSystem.rules.size();
        if (total > listRows) {
            int trackH = listRows * ROW_H;
            int th = Math.max(12, trackH * listRows / total);
            int ty = listY + (trackH - th) * first / Math.max(1, total - listRows);
            ctx.fill(px + pw - 7, listY, px + pw - 5, listY + trackH, 0xFF1E242B);
            ctx.fill(px + pw - 7, ty, px + pw - 5, ty + th, 0xFF56606C);
        }
    }

    @Override
    public void close() {
        ValueSystem.save();
        ClickerConfig.save();
        if (this.client != null) this.client.setScreen(parent);
    }
}
