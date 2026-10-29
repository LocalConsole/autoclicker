package com.example.universalmod;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Лёгкий список установленных модов: название, версия, автор, описание, поиск. */
public class ModListScreen extends Screen {
    private static final int ROW_H = 34;

    private final Screen parent;
    private final List<ModContainer> all = new ArrayList<>();
    private List<ModContainer> shown = new ArrayList<>();
    private TextFieldWidget search;
    private int first = 0;
    private int listTop, listBottom;

    public ModListScreen(Screen parent) {
        super(Text.literal(I18n.t("Список модов")));
        this.parent = parent;
        for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
            if (m.getContainingMod().isPresent()) continue; // вложенные модули (jar-in-jar) не показываем
            if (m.getMetadata().getId().equals("java")) continue;
            all.add(m);
        }
        all.sort(Comparator.comparing(m -> m.getMetadata().getName().toLowerCase(Locale.ROOT)));
        shown = new ArrayList<>(all);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        search = new TextFieldWidget(this.textRenderer, cx - 150, 28, 300, 20, Text.literal(""));
        search.setMaxLength(64);
        search.setPlaceholder(Text.literal(I18n.t("Поиск мода")));
        search.setChangedListener(t -> filter(t));
        addDrawableChild(search);
        setInitialFocus(search);
        addDrawableChild(ButtonWidget.builder(Text.literal(I18n.t("Готово")), b -> this.close())
                .dimensions(cx - 100, this.height - 28, 200, 20).build());
        listTop = 56;
        listBottom = this.height - 34;
        filter(search.getText());
    }

    private void filter(String q) {
        String s = q == null ? "" : q.toLowerCase(Locale.ROOT).trim();
        shown = new ArrayList<>();
        for (ModContainer m : all) {
            ModMetadata md = m.getMetadata();
            if (s.isEmpty() || md.getName().toLowerCase(Locale.ROOT).contains(s)
                    || md.getId().toLowerCase(Locale.ROOT).contains(s)) {
                shown.add(m);
            }
        }
        first = 0;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom - listTop) / ROW_H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, shown.size() - visibleRows());
        first = Math.max(0, Math.min(max, first - (int) Math.signum(verticalAmount)));
        return true;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        int x = this.width / 2 - 150;
        int w = 300;
        int rows = visibleRows();
        for (int i = 0; i < rows; i++) {
            int idx = first + i;
            if (idx >= shown.size()) break;
            ModMetadata md = shown.get(idx).getMetadata();
            int y = listTop + i * ROW_H;
            context.fill(x, y, x + w, y + ROW_H - 2, 0x66000000);
            context.drawTextWithShadow(this.textRenderer, Text.literal(md.getName()), x + 6, y + 4, 0xFFFFFFFF);
            String ver = md.getVersion().getFriendlyString();
            int nameW = this.textRenderer.getWidth(md.getName());
            context.drawTextWithShadow(this.textRenderer, Text.literal(ver), x + 12 + nameW, y + 4, 0xFFAAAAAA);

            StringBuilder authors = new StringBuilder();
            for (Person p : md.getAuthors()) {
                if (authors.length() > 0) authors.append(", ");
                authors.append(p.getName());
            }
            String line2 = md.getId() + (authors.length() > 0 ? "  |  " + authors : "");
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(this.textRenderer.trimToWidth(line2, w - 12)), x + 6, y + 15, 0xFFFFD866);
            String desc = md.getDescription() == null ? "" : md.getDescription().replace('\n', ' ');
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(this.textRenderer.trimToWidth(desc, w - 12)), x + 6, y + 25, 0xFF888888);
        }
        String info = I18n.t("Модов: ") + shown.size();
        context.drawTextWithShadow(this.textRenderer, Text.literal(info), x, this.height - 52 + 12, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    /** Кнопка «Моды» в левом верхнем углу главного меню и меню паузы. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!ClickerConfig.modsButton) return;
            if (!(screen instanceof TitleScreen) && !(screen instanceof GameMenuScreen)) return;
            Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(I18n.t("Моды")),
                            b -> client.setScreen(new ModListScreen(screen)))
                    .dimensions(4, 4, 60, 20).build());
        });
    }
}
