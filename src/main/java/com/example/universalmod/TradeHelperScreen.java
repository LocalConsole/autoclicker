package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Помощник: жители (поиск, библиотекарь), стол зачарований и наковальня — всё в одном меню.
 * Справа — найденные жители, у которых есть то, что вы ищете.
 */
public class TradeHelperScreen extends Screen {
    private static final int ACCENT = 0xFF5BD38A;
    private final Screen parent;
    private int px, py, pw, ph, listX, listY;
    private TextFieldWidget search, wants, enchant;

    public TradeHelperScreen(Screen parent) {
        super(Text.literal(I18n.t("Жители и зачарования")));
        this.parent = parent;
    }

    private static String onOff(boolean b) {
        return b ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ");
    }

    @Override
    protected void init() {
        pw = Math.min(520, this.width - 16);
        ph = this.height - 16;
        px = (this.width - pw) / 2;
        py = 8;
        int lw = Math.min(240, pw / 2 - 12);
        int x = px + 10, y = py + 34;

        search = field(x, y + 10, lw, ClickerConfig.villagerSearch, I18n.t("напр.: починка, острота 5, алмаз"),
                t -> ClickerConfig.villagerSearch = t);
        y += 40;
        wants = field(x, y + 10, lw, ClickerConfig.librarianWants, I18n.t("напр.: починка, бесконечность"),
                t -> ClickerConfig.librarianWants = t);
        y += 34;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Макс. цена книги: ") + ClickerConfig.librarianMaxPrice), b -> {
            int[] steps = {10, 15, 20, 25, 30, 40, 50, 64};
            int i = 0;
            while (i < steps.length && steps[i] <= ClickerConfig.librarianMaxPrice) i++;
            ClickerConfig.librarianMaxPrice = steps[i % steps.length];
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(x, y, lw, 20).tooltip(Tooltip.of(Text.literal(I18n.t("Дороже — не считается находкой")))).build());
        y += 30;
        enchant = field(x, y + 10, lw, ClickerConfig.enchantTarget, I18n.t("напр.: починка (пусто — кнопка 3)"),
                t -> ClickerConfig.enchantTarget = t);
        y += 36;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Трейды над головой: ") + onOff(ClickerConfig.villagerHeads)), b -> {
            ClickerConfig.villagerHeads = !ClickerConfig.villagerHeads;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(x, y, lw, 20).build());
        y += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Следующие уровни в торговле: ") + onOff(ClickerConfig.villagerNext)), b -> {
            ClickerConfig.villagerNext = !ClickerConfig.villagerNext;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(x, y, lw, 20).build());
        y += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Стол зачарований: ") + onOff(ClickerConfig.enchantHelper)), b -> {
            ClickerConfig.enchantHelper = !ClickerConfig.enchantHelper;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(x, y, lw, 20).build());
        y += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Наковальня: ") + onOff(ClickerConfig.anvilHelper)), b -> {
            ClickerConfig.anvilHelper = !ClickerConfig.anvilHelper;
            ClickerConfig.save();
            clearAndInit();
        }).dimensions(x, y, lw, 20).build());
        y += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Забыть всех жителей")), b -> {
            VillagerTrades.cache.clear();
            VillagerTrades.save();
        }).dimensions(x, y, lw, 20).build());

        listX = x + lw + 14;
        listY = py + 44;
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> close())
                .dimensions(this.width / 2 - 60, py + ph - 24, 120, 20).build());
    }

    private TextFieldWidget field(int x, int y, int w, String val, String ph, java.util.function.Consumer<String> set) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 18, Text.literal(""));
        f.setMaxLength(120);
        f.setText(val);
        f.setPlaceholder(Text.literal(ph));
        f.setChangedListener(t -> {
            set.accept(t);
            ClickerConfig.save();
        });
        addDrawableChild(f);
        return f;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF2E3742);
        ctx.fill(px, py, px + pw, py + ph, 0xF0161B21);
        ctx.fill(px, py, px + pw, py + 22, 0xFF1E2630);
        ctx.fill(px, py + 22, px + pw, py + 23, ACCENT);
        ctx.fill(listX - 4, listY - 14, px + pw - 8, py + ph - 30, 0xFF11151A);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        var tr = this.textRenderer;
        ctx.drawItem(new ItemStack(Items.EMERALD), px + 4, py + 3);
        ctx.drawTextWithShadow(tr, this.title, px + 24, py + 7, 0xFFFFFFFF);
        int lx = px + 10;
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Найти жителя, который продаёт:")), lx, search.getY() - 10, 0xFF9AA4AE);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Библиотекарь — ищу книги (через запятую):")), lx, wants.getY() - 10, 0xFF9AA4AE);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Стол зачарований — нужное зачарование:")), lx, enchant.getY() - 10, 0xFF9AA4AE);
        // подсказка про авто-перебор — под заголовком окна, чтобы не налезать на поля
        String rr = I18n.t("Авто-перебор: встаньте у кафедры, смотрите на жителя, клавиша «Библиотекарь: авто-перебор»");
        ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(rr, pw - 20)), px + 10, py + ph - 38, 0xFF6F7A86);

        // справа — найденные
        String q = ClickerConfig.villagerSearch;
        MinecraftClient mc = MinecraftClient.getInstance();
        int rx = listX, ry = listY, rw = px + pw - 12 - listX;
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Найденные жители")), rx, ry - 11, 0xFFFFFFFF);
        List<String> rows = new ArrayList<>();
        if (q.isBlank()) {
            rows.add(I18n.t("Введите, что ищете, слева."));
            rows.add(I18n.t("Жители запоминаются, когда вы"));
            rows.add(I18n.t("открываете с ними торговлю."));
            rows.add("");
            rows.add(I18n.t("Знаю жителей: ") + VillagerTrades.cache.size());
        } else {
            String world = Waypoints.worldKey(mc);
            for (Map.Entry<String, VillagerTrades.Villager> e : VillagerTrades.cache.entrySet()) {
                VillagerTrades.Villager v = e.getValue();
                if (!world.equals(v.world) || !VillagerTrades.matches(v, q)) continue;
                String what = "";
                for (VillagerTrades.Offer o : v.offers) {
                    String t = o.label + " " + o.item + " " + VillagerTrades.itemName(o.item);
                    if (SearchMatcher.matches(t, q)) {
                        what = (o.label.isEmpty() ? VillagerTrades.itemName(o.item) : o.label) + " — " + o.price + I18n.t(" изумр.");
                        break;
                    }
                }
                int dist = mc.player == null ? 0 : (int) Math.sqrt(Math.pow(v.x - mc.player.getX(), 2) + Math.pow(v.z - mc.player.getZ(), 2));
                rows.add("§e" + what);
                rows.add("§7  " + (int) v.x + " " + (int) v.y + " " + (int) v.z + "  (" + dist + I18n.t(" м") + ")");
            }
            if (rows.isEmpty()) rows.add(I18n.t("Никто из знакомых жителей это не продаёт"));
            else rows.add(0, I18n.t("Они подсвечены в мире золотой рамкой"));
        }
        int cy = ry;
        for (String r : rows) {
            if (cy > py + ph - 40) break;
            ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(r, rw)), rx, cy, 0xFFDDDDDD);
            cy += 10;
        }
    }

    @Override
    public void close() {
        ClickerConfig.save();
        if (this.client != null) this.client.setScreen(parent);
    }
}
