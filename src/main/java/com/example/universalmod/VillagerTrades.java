package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Трейды жителей:
 *  - запоминаем трейды жителя, когда вы открываете с ним торговлю, и показываем их значками над головой;
 *  - поиск: «у кого острота 5?» — нужные жители подсвечиваются и видны издалека;
 *  - помощник библиотекаря: сразу видно, выпала ли нужная книга и по нормальной ли цене;
 *  - в окне торговли — возможные трейды следующих уровней (догадка по таблице игры).
 */
public class VillagerTrades {
    public static class Offer {
        public String item = "";   // id того, что продаёт житель
        public int count = 1;
        public int price = 0;      // изумрудов (или первого предмета)
        public String priceItem = "";
        public String label = "";  // для книг — зачарования
        public boolean disabled;
    }

    public static class Villager {
        public String world = "", dim = "";
        public double x, y, z;
        public String profession = "none";
        public int level = 1;
        public List<Offer> offers = new ArrayList<>();
        public long seen;
    }

    public static Map<String, Villager> cache = new HashMap<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod_villagers.json");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Type TYPE = new TypeToken<Map<String, Villager>>() {}.getType();

    public static void register() {
        load();
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "villager_trades"), (ctx, tc) -> renderHeads(ctx));
    }

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            Map<String, Villager> m = GSON.fromJson(r, TYPE);
            if (m != null) cache = m;
        } catch (Exception ignored) {
        }
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(cache, TYPE, w);
        } catch (Exception ignored) {
        }
    }

    // ---------- Снимок трейдов при открытии торговли ----------

    private static Entity lastTarget;
    private static TradeOfferList lastList;
    private static boolean announced;

    /** Текст зачарований книги/предмета. */
    public static String enchantText(ItemStack st) {
        StringBuilder sb = new StringBuilder();
        try {
            ItemEnchantmentsComponent c = st.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (c == null || c.isEmpty()) c = st.get(DataComponentTypes.ENCHANTMENTS);
            if (c != null) {
                for (RegistryEntry<Enchantment> e : c.getEnchantments()) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(Enchantment.getName(e, c.getLevel(e)).getString());
                }
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        // запоминаем, на кого смотрели перед открытием окна торговли
        if (mc.currentScreen == null) {
            lastTarget = mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof VillagerEntity v ? v : null;
            lastList = null;
            announced = false;
            return;
        }
        if (!(mc.currentScreen instanceof MerchantScreen ms) || !(lastTarget instanceof VillagerEntity v)) return;
        TradeOfferList list = ms.getScreenHandler().getRecipes();
        if (list == null || list.isEmpty() || list == lastList) return;
        lastList = list;
        Villager e = new Villager();
        e.world = Waypoints.worldKey(mc);
        e.dim = Waypoints.currentDim(mc);
        e.x = v.getX();
        e.y = v.getY();
        e.z = v.getZ();
        String data = String.valueOf(v.getVillagerData());
        e.profession = TradeTable.professionOf(data);
        e.level = TradeTable.levelOf(data);
        e.seen = System.currentTimeMillis();
        for (TradeOffer o : list) {
            Offer of = new Offer();
            ItemStack sell = o.getSellItem();
            of.item = Registries.ITEM.getId(sell.getItem()).toString();
            of.count = sell.getCount();
            ItemStack buy = o.getDisplayedFirstBuyItem();
            of.price = buy.getCount();
            of.priceItem = Registries.ITEM.getId(buy.getItem()).toString();
            of.label = enchantText(sell);
            of.disabled = o.isDisabled();
            e.offers.add(of);
        }
        cache.put(v.getUuidAsString(), e);
        save();
        if (!announced) {
            announced = true;
            librarianCheck(mc, e);
        }
    }

    /** Библиотекарь: нужная ли книга и подходит ли цена. */
    private static void librarianCheck(MinecraftClient mc, Villager e) {
        if (!"librarian".equals(e.profession) || ClickerConfig.librarianWants.isBlank() || LecternReroll.active()) return;
        for (Offer o : e.offers) {
            if (o.label.isEmpty() || !o.item.contains("enchanted_book")) continue;
            if (wanted(o.label) && o.price <= ClickerConfig.librarianMaxPrice) {
                mc.player.sendMessage(Text.literal("§a§l★ " + I18n.t("НАШЁЛ: ") + o.label + " — " + o.price + I18n.t(" изумр.")), false);
                mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                return;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (Offer o : e.offers) if (!o.label.isEmpty() && o.item.contains("enchanted_book")) sb.append(o.label).append(" (").append(o.price).append(") ");
        if (sb.length() > 0) mc.player.sendMessage(Text.literal("§7" + I18n.t("Библиотекарь: ") + sb + I18n.t("— не то, меняйте кафедру")), true);
    }

    /** Подходит ли книга под список «ищу» (через запятую). */
    public static boolean wanted(String label) {
        for (String w : ClickerConfig.librarianWants.split(",")) {
            if (!w.isBlank() && SearchMatcher.matches(label, w.trim())) return true;
        }
        return false;
    }

    /** Подходит ли житель под строку поиска. */
    public static boolean matches(Villager v, String q) {
        if (q == null || q.isBlank()) return false;
        for (Offer o : v.offers) {
            String text = o.label + " " + o.item.replace("minecraft:", "").replace('_', ' ') + " " + itemName(o.item);
            if (SearchMatcher.matches(text, q)) return true;
        }
        return false;
    }

    public static String itemName(String id) {
        try {
            Identifier i = Identifier.tryParse(id);
            return i == null ? id : new ItemStack(Registries.ITEM.get(i)).getName().getString();
        } catch (Throwable t) {
            return id;
        }
    }

    public static ItemStack stackOf(String id, int count) {
        Identifier i = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
        return i == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(i), Math.max(1, count));
    }

    // ---------- Над головами ----------

    private static void renderHeads(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden || mc.currentScreen != null) return;
        if (!ClickerConfig.villagerHeads && ClickerConfig.villagerSearch.isBlank()) return;
        String world = Waypoints.worldKey(mc), dim = Waypoints.currentDim(mc);
        var cam = mc.gameRenderer.getCamera();
        var cp = ContainerSearch.cameraPos(cam, mc);
        org.joml.Quaternionf q = cam.getRotation();
        org.joml.Vector3f right = new org.joml.Vector3f(1, 0, 0).rotate(q);
        org.joml.Vector3f up = new org.joml.Vector3f(0, 1, 0).rotate(q);
        org.joml.Vector3f fwd = new org.joml.Vector3f(0, 0, -1).rotate(q);
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        double aspect = mc.getWindow().getFramebufferWidth() / (double) Math.max(1, mc.getWindow().getFramebufferHeight());
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        var tr = mc.textRenderer;
        String query = ClickerConfig.villagerSearch;

        // живые жители рядом: позиция берётся у самого жителя (они ходят)
        Map<String, Entity> alive = new HashMap<>();
        for (Entity e : mc.world.getEntities()) if (e instanceof VillagerEntity) alive.put(e.getUuidAsString(), e);

        for (Map.Entry<String, Villager> en : cache.entrySet()) {
            Villager v = en.getValue();
            if (!world.equals(v.world) || !dim.equals(v.dim)) continue;
            Entity live = alive.get(en.getKey());
            double vx = live != null ? live.getX() : v.x, vy = live != null ? live.getY() : v.y, vz = live != null ? live.getZ() : v.z;
            boolean hit = matches(v, query);
            double dx = vx - mc.player.getX(), dy = vy - mc.player.getY(), dz = vz - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (!hit && (!ClickerConfig.villagerHeads || dist > 24 || live == null)) continue;
            if (hit && dist > 256) continue;
            double wx = vx - cp.x, wy = vy + 2.4 - cp.y, wz = vz - cp.z;
            double cx = wx * right.x + wy * right.y + wz * right.z;
            double cy = wx * up.x + wy * up.y + wz * up.z;
            double cz = wx * fwd.x + wy * fwd.y + wz * fwd.z;
            if (cz < 0.3) continue;
            int sx = (int) ((cx / (cz * tanHalf * aspect) * 0.5 + 0.5) * sw);
            int sy = (int) ((0.5 - cy / (cz * tanHalf) * 0.5) * sh);
            if (sx < -60 || sx > sw + 60 || sy < -40 || sy > sh) continue;

            List<Offer> show = new ArrayList<>();
            for (Offer o : v.offers) if (!o.item.endsWith(":emerald")) show.add(o);
            if (show.isEmpty()) show = v.offers;
            int n = Math.min(5, show.size());
            int w = n * 18 + 4, h = 20;
            String head = I18n.t(TradeTable.LEVEL_NAMES[Math.max(1, Math.min(5, v.level))]);
            String extra = null;
            for (Offer o : show) if (!o.label.isEmpty()) { extra = o.label + "  " + o.price; break; }
            if (hit) {
                for (Offer o : v.offers) {
                    String t = o.label + " " + o.item;
                    if (SearchMatcher.matches(t + " " + itemName(o.item), query)) {
                        extra = (o.label.isEmpty() ? itemName(o.item) : o.label) + "  " + o.price + I18n.t(" изумр.") + "  " + (int) dist + I18n.t(" м");
                        break;
                    }
                }
            }
            int ew = extra == null ? 0 : tr.getWidth(extra) + 6;
            w = Math.max(w, ew);
            int x = sx - w / 2, y = sy - h - (extra != null ? 10 : 0);
            int border = hit ? 0xFFF2C94C : 0xFF2E3742;
            ctx.fill(x - 1, y - 1, x + w + 1, y + h + (extra != null ? 10 : 0) + 1, border);
            ctx.fill(x, y, x + w, y + h + (extra != null ? 10 : 0), 0xD0161B21);
            for (int i = 0; i < n; i++) {
                Offer o = show.get(i);
                ItemStack st = stackOf(o.item, o.count);
                int ix = x + 2 + i * 18, iy = y + 2;
                ctx.drawItem(st, ix, iy);
                ctx.drawStackOverlay(tr, st, ix, iy);
                if (o.disabled) ctx.fill(ix, iy + 7, ix + 16, iy + 9, 0xCCFF3030);
            }
            if (extra != null) {
                ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(extra, w - 4)), x + 3, y + h, hit ? 0xFFF2C94C : 0xFFDDDDDD);
            }
            ctx.drawTextWithShadow(tr, Text.literal(head), sx - tr.getWidth(head) / 2, y - 10, 0xFF9AA4AE);
        }
    }

    // ---------- Панель в окне торговли ----------

    /** Возможные трейды следующих уровней — справа от окна торговли. */
    public static void drawMerchantPanel(DrawContext ctx, MerchantScreen screen, int gx, int gy, int gw) {
        if (!ClickerConfig.villagerNext || !(lastTarget instanceof VillagerEntity v)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        var tr = mc.textRenderer;
        String data = String.valueOf(v.getVillagerData());
        String prof = TradeTable.professionOf(data);
        int level = TradeTable.levelOf(data);
        if (level >= 5) return;
        List<String> next = new ArrayList<>();
        int shownLevels = 0;
        for (int l = level + 1; l <= 5 && shownLevels < 2; l++, shownLevels++) {
            next.add("#" + l);
            next.addAll(TradeTable.of(prof, l));
        }
        if (next.size() <= 1) return;
        int x = gx + gw + 4, y = gy;
        int w = 150;
        if (x + w > ctx.getScaledWindowWidth() - 2) return; // нет места — не рисуем поверх окна
        int h = 14;
        for (String s : next) h += s.startsWith("#") ? 12 : 18;
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF2E3742);
        ctx.fill(x, y, x + w, y + h, 0xF0161B21);
        ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Дальше может быть:")), x + 4, y + 3, 0xFFFFFFFF);
        int cy = y + 14;
        for (String s : next) {
            if (s.startsWith("#")) {
                int l = Integer.parseInt(s.substring(1));
                ctx.drawTextWithShadow(tr, Text.literal(I18n.t(TradeTable.LEVEL_NAMES[l])), x + 4, cy + 2, 0xFFF2C94C);
                cy += 12;
                continue;
            }
            TradeTable.Trade t = TradeTable.parse(s);
            int ix = x + 4;
            ItemStack a = stackOf(t.i1(), t.c1());
            ctx.drawItem(a, ix, cy);
            ctx.drawStackOverlay(tr, a, ix, cy);
            ix += 18;
            if (t.c2() > 0) {
                ItemStack b = stackOf(t.i2(), t.c2());
                ctx.drawItem(b, ix, cy);
                ctx.drawStackOverlay(tr, b, ix, cy);
                ix += 18;
            }
            ctx.drawTextWithShadow(tr, Text.literal("→"), ix + 2, cy + 4, 0xFF9AA4AE);
            ix += 12;
            ItemStack o = stackOf(t.outItem(), t.out());
            ctx.drawItem(o, ix, cy);
            ctx.drawStackOverlay(tr, o, ix, cy);
            String name = tr.trimToWidth(itemName("minecraft:" + t.outItem()) + (t.ench() ? " ✦" : ""), x + w - ix - 22);
            ctx.drawTextWithShadow(tr, Text.literal(name), ix + 19, cy + 4, t.ench() ? 0xFFB57BEE : 0xFFDDDDDD);
            cy += 18;
        }
    }
}
