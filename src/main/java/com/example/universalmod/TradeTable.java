package com.example.universalmod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Возможные трейды жителей по уровням (обычная Java-версия, без экспериментального «баланса торговли»).
 * Это не точный будущий трейд — его сервер выбирает случайно при повышении уровня, — а все варианты,
 * из которых он будет выбран. Запись: "сколько id [+ сколько id] > сколько id[*]" (* — с зачарованием).
 */
public final class TradeTable {
    private TradeTable() { }

    public static final String[] LEVEL_NAMES = {"", "Новичок", "Ученик", "Подмастерье", "Эксперт", "Мастер"};

    private static final Map<String, List<List<String>>> T = new HashMap<>();

    private static void p(String prof, List<String> l1, List<String> l2, List<String> l3, List<String> l4, List<String> l5) {
        T.put(prof, List.of(l1, l2, l3, l4, l5));
    }

    static {
        p("farmer",
                List.of("20 wheat > 1 emerald", "26 potato > 1 emerald", "22 carrot > 1 emerald", "15 beetroot > 1 emerald", "1 emerald > 6 bread"),
                List.of("6 pumpkin > 1 emerald", "1 emerald > 4 pumpkin_pie", "1 emerald > 4 apple"),
                List.of("1 emerald > 18 cookie", "4 melon > 1 emerald"),
                List.of("1 emerald > 1 cake", "1 emerald > 1 suspicious_stew"),
                List.of("3 emerald > 3 golden_carrot", "4 emerald > 3 glistering_melon_slice"));
        p("fisherman",
                List.of("20 string > 1 emerald", "10 coal > 1 emerald", "6 cod + 1 emerald > 6 cooked_cod", "3 emerald > 1 cod_bucket"),
                List.of("15 cod > 1 emerald", "6 salmon + 1 emerald > 6 cooked_salmon", "2 emerald > 1 campfire"),
                List.of("13 salmon > 1 emerald", "8 emerald > 1 fishing_rod*"),
                List.of("6 tropical_fish > 1 emerald"),
                List.of("4 pufferfish > 1 emerald", "1 oak_boat > 1 emerald"));
        p("shepherd",
                List.of("18 white_wool > 1 emerald", "18 brown_wool > 1 emerald", "18 black_wool > 1 emerald", "18 gray_wool > 1 emerald", "2 emerald > 1 shears"),
                List.of("12 white_dye > 1 emerald", "1 emerald > 1 white_wool", "1 emerald > 4 white_carpet"),
                List.of("12 yellow_dye > 1 emerald", "3 emerald > 1 white_bed"),
                List.of("12 brown_dye > 1 emerald", "3 emerald > 1 white_banner"),
                List.of("2 emerald > 3 painting"));
        p("fletcher",
                List.of("32 stick > 1 emerald", "1 emerald > 16 arrow", "1 emerald + 10 gravel > 10 flint"),
                List.of("26 flint > 1 emerald", "2 emerald > 1 bow"),
                List.of("14 string > 1 emerald", "3 emerald > 1 crossbow"),
                List.of("24 feather > 1 emerald", "7 emerald > 1 bow*"),
                List.of("8 tripwire_hook > 1 emerald", "8 emerald > 1 crossbow*", "2 emerald + 5 arrow > 5 tipped_arrow"));
        p("librarian",
                List.of("24 paper > 1 emerald", "9 emerald + 1 book > 1 bookshelf", "5 emerald + 1 book > 1 enchanted_book*"),
                List.of("4 book > 1 emerald", "1 emerald > 4 lantern", "5 emerald + 1 book > 1 enchanted_book*"),
                List.of("5 ink_sac > 1 emerald", "1 emerald > 4 glass", "5 emerald + 1 book > 1 enchanted_book*"),
                List.of("2 writable_book > 1 emerald", "5 emerald > 1 clock", "4 emerald > 1 compass", "5 emerald + 1 book > 1 enchanted_book*"),
                List.of("20 emerald > 1 name_tag"));
        p("cartographer",
                List.of("24 paper > 1 emerald", "7 emerald > 1 map"),
                List.of("11 glass_pane > 1 emerald", "13 emerald + 1 compass > 1 filled_map"),
                List.of("1 compass > 1 emerald", "14 emerald + 1 compass > 1 filled_map"),
                List.of("7 emerald > 1 item_frame", "3 emerald > 1 white_banner"),
                List.of("8 emerald > 1 globe_banner_pattern"));
        p("cleric",
                List.of("32 rotten_flesh > 1 emerald", "1 emerald > 2 redstone"),
                List.of("3 gold_ingot > 1 emerald", "1 emerald > 1 lapis_lazuli"),
                List.of("2 rabbit_foot > 1 emerald", "4 emerald > 1 glowstone"),
                List.of("4 turtle_scute > 1 emerald", "9 glass_bottle > 1 emerald", "5 emerald > 1 ender_pearl"),
                List.of("22 nether_wart > 1 emerald", "3 emerald > 1 experience_bottle"));
        p("armorer",
                List.of("15 coal > 1 emerald", "7 emerald > 1 iron_leggings", "4 emerald > 1 iron_boots", "5 emerald > 1 iron_helmet", "9 emerald > 1 iron_chestplate"),
                List.of("4 iron_ingot > 1 emerald", "36 emerald > 1 bell", "1 emerald > 1 chainmail_boots", "3 emerald > 1 chainmail_leggings"),
                List.of("1 lava_bucket > 1 emerald", "1 diamond > 1 emerald", "1 emerald > 1 chainmail_helmet", "4 emerald > 1 chainmail_chestplate", "5 emerald > 1 shield"),
                List.of("19 emerald > 1 diamond_leggings*", "13 emerald > 1 diamond_boots*"),
                List.of("13 emerald > 1 diamond_helmet*", "21 emerald > 1 diamond_chestplate*"));
        p("weaponsmith",
                List.of("15 coal > 1 emerald", "3 emerald > 1 iron_axe", "7 emerald > 1 iron_sword*"),
                List.of("4 iron_ingot > 1 emerald", "36 emerald > 1 bell"),
                List.of("24 flint > 1 emerald"),
                List.of("1 diamond > 1 emerald", "17 emerald > 1 diamond_axe*"),
                List.of("13 emerald > 1 diamond_sword*"));
        p("toolsmith",
                List.of("15 coal > 1 emerald", "1 emerald > 1 stone_axe", "1 emerald > 1 stone_shovel", "1 emerald > 1 stone_pickaxe", "1 emerald > 1 stone_hoe"),
                List.of("4 iron_ingot > 1 emerald", "36 emerald > 1 bell"),
                List.of("30 flint > 1 emerald", "1 emerald > 1 iron_axe*", "2 emerald > 1 iron_shovel*", "3 emerald > 1 iron_pickaxe*", "4 emerald > 1 diamond_hoe"),
                List.of("1 diamond > 1 emerald", "12 emerald > 1 diamond_axe*", "5 emerald > 1 diamond_shovel*"),
                List.of("13 emerald > 1 diamond_pickaxe*"));
        p("butcher",
                List.of("14 chicken > 1 emerald", "4 porkchop > 1 emerald", "7 rabbit > 1 emerald", "1 emerald > 1 rabbit_stew"),
                List.of("15 coal > 1 emerald", "1 emerald > 5 cooked_porkchop", "1 emerald > 8 cooked_chicken"),
                List.of("7 mutton > 1 emerald", "10 beef > 1 emerald"),
                List.of("10 dried_kelp_block > 1 emerald"),
                List.of("10 sweet_berries > 1 emerald"));
        p("leatherworker",
                List.of("6 leather > 1 emerald", "3 emerald > 1 leather_leggings", "7 emerald > 1 leather_chestplate"),
                List.of("9 flint > 1 emerald", "5 emerald > 1 leather_helmet", "4 emerald > 1 leather_boots"),
                List.of("4 rabbit_hide > 1 emerald", "7 emerald > 1 leather_chestplate"),
                List.of("4 turtle_scute > 1 emerald", "6 emerald > 1 leather_horse_armor"),
                List.of("6 emerald > 1 saddle", "5 emerald > 1 leather_helmet"));
        p("mason",
                List.of("10 clay_ball > 1 emerald", "1 emerald > 10 brick"),
                List.of("20 stone > 1 emerald", "1 emerald > 4 chiseled_stone_bricks"),
                List.of("16 granite > 1 emerald", "16 andesite > 1 emerald", "16 diorite > 1 emerald", "1 emerald > 4 polished_andesite", "1 emerald > 4 dripstone_block"),
                List.of("12 quartz > 1 emerald", "1 emerald > 1 white_terracotta", "1 emerald > 1 white_glazed_terracotta"),
                List.of("1 emerald > 1 quartz_pillar", "1 emerald > 1 quartz_block"));
    }

    /** Варианты трейдов для профессии и уровня (1–5), или пусто. */
    public static List<String> of(String profession, int level) {
        List<List<String>> l = T.get(profession);
        if (l == null || level < 1 || level > 5) return List.of();
        return l.get(level - 1);
    }

    /** Профессия из описания жителя (на разных версиях оно выглядит по-разному, поэтому ищем по имени). */
    public static String professionOf(String villagerData) {
        String s = villagerData.toLowerCase(java.util.Locale.ROOT);
        for (String k : T.keySet()) if (s.contains(":" + k) || s.contains("=" + k) || s.contains(" " + k)) return k;
        if (s.contains("nitwit")) return "nitwit";
        return "none";
    }

    public static int levelOf(String villagerData) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("level=(\\d)").matcher(villagerData);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** Разобранная запись: что отдаём (1–2 предмета) и что получаем. */
    public record Trade(int c1, String i1, int c2, String i2, int out, String outItem, boolean ench) { }

    public static Trade parse(String s) {
        String[] lr = s.split(">");
        String[] ins = lr[0].trim().split("\\+");
        String[] a = ins[0].trim().split(" ");
        int c2 = 0;
        String i2 = "";
        if (ins.length > 1) {
            String[] b = ins[1].trim().split(" ");
            c2 = Integer.parseInt(b[0]);
            i2 = b[1];
        }
        String[] o = lr[1].trim().split(" ");
        boolean ench = o[1].endsWith("*");
        return new Trade(Integer.parseInt(a[0]), a[1], c2, i2, Integer.parseInt(o[0]), ench ? o[1].substring(0, o[1].length() - 1) : o[1], ench);
    }
}
