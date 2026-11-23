package com.example.universalmod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Умный поиск по меню мода: разные окончания («контуры» = «контур»), опечатки («кантур»),
 * слова по смыслу («контур» находит рамки, подсветку, хитбокс), несколько слов сразу,
 * текущие значения (цвет «красный», «ВКЛ») и три языка.
 */
public final class SearchMatcher {
    private SearchMatcher() { }

    /** Группы слов по смыслу (основы слов). */
    private static final String[][] GROUPS = {
            {"контур", "рамк", "обвод", "подсвет", "хитбокс", "выдел", "outline", "frame", "hitbox", "highlight", "ramka", "chegara", "ajrat"},
            {"цвет", "оттен", "color", "colour", "rang"},
            {"красн", "red", "qizil"},
            {"оранж", "orange", "to'q sariq"},
            {"желт", "yellow", "sariq"},
            {"зелен", "green", "yashil"},
            {"голуб", "cyan", "zangori", "havorang"},
            {"син", "blue", "ko'k"},
            {"фиолет", "purple", "violet", "binafsha"},
            {"бел", "white", "oq"},
            {"брон", "прочн", "durab", "armor", "armour", "zirh", "mustahk"},
            {"шалкер", "shulker"},
            {"сундук", "контейнер", "chest", "container", "sandiq", "konteyner", "бочк", "barrel"},
            {"клик", "click", "klik", "нажат"},
            {"блок", "block", "blok"},
            {"метк", "точк", "waypoint", "marker", "belgi", "nuqta"},
            {"схем", "litemat", "постро", "строи", "стройк", "schemat", "sxema", "build", "qurish"},
            {"ценн", "дорог", "value", "valuable", "qiymat", "qimmat"},
            {"щит", "shield", "qalqon"},
            {"лав", "lava"},
            {"вод", "ватердроп", "water", "suv"},
            {"вед", "bucket", "chelak"},
            {"траект", "снаряд", "стрел", "лук", "арбалет", "трезуб", "жемчуг", "trajector", "projectile", "arrow", "bow", "pearl", "kamon", "marvarid"},
            {"булав", "mace", "gurz"},
            {"копь", "spear", "nayza"},
            {"элитр", "elytra"},
            {"зум", "приближ", "zoom", "yaqinlash"},
            {"ярк", "фулбрайт", "свет", "освещ", "fullbright", "bright", "yorug"},
            {"камер", "фрикам", "обзор", "camera", "freecam", "freelook", "kamera"},
            {"чат", "chat", "сообщ"},
            {"ник", "аккаунт", "nick", "account", "akkaunt"},
            {"язык", "language", "til"},
            {"сорт", "sort", "saral"},
            {"ед", "голод", "насыщ", "food", "hunger", "ovqat", "ochlik"},
            {"эффект", "тотем", "effect", "totem"},
            {"координ", "coord", "koordinat"},
            {"смерт", "death", "o'lim"},
            {"невидим", "барьер", "invis", "barrier", "ko'rinmas"},
            {"частиц", "particle", "zarra"},
            {"клавиш", "бинд", "кнопк", "key", "bind", "tugma"},
            {"инвентар", "хотбар", "слот", "inventory", "hotbar", "slot", "inventar"},
            {"авто", "auto", "avto"},
            {"пвп", "pvp", "бой", "удар", "атак", "attack", "hit", "jang"},
            {"кристалл", "обсидиан", "crystal", "kristall"},
            {"макрос", "macro", "makros"},
            {"линейк", "расстоян", "ruler", "distance", "masofa"},
            {"копа", "копат", "dig", "mine", "кирк", "pickaxe"},
            {"вкл", "on", "yoq"},
            {"выкл", "off", "o'ch"},
    };

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replace('ё', 'е').replace('‘', '\'').replace('’', '\'');
    }

    /** Основа слова: отбрасываем окончание («контуры» → «контур»). */
    private static String stem(String w) {
        if (w.length() <= 4) return w;
        return w.substring(0, Math.max(4, w.length() - 2));
    }

    private static List<String> words(String s) {
        List<String> out = new ArrayList<>();
        for (String w : norm(s).split("[^\\p{L}\\p{Nd}']+")) if (!w.isEmpty()) out.add(w);
        return out;
    }

    /** Все варианты для слова запроса: сама основа + слова той же смысловой группы. */
    private static Set<String> variants(String w) {
        Set<String> v = new LinkedHashSet<>();
        String st = stem(w);
        v.add(st);
        v.add(w); // целое слово — для опечаток
        for (String[] g : GROUPS) {
            boolean hit = false;
            for (String x : g) {
                if (st.startsWith(x) || (x.length() >= 3 && x.startsWith(st) && st.length() >= 3) || close(w, x)) {
                    hit = true;
                    break;
                }
            }
            if (hit) for (String x : g) v.add(x);
        }
        return v;
    }

    /** Опечатка в одну букву (для слов от 5 букв). */
    private static boolean close(String a, String b) {
        if (a.length() < 5 || b.length() < 5) return false;
        int n = Math.min(a.length(), b.length());
        a = a.substring(0, n);
        b = b.substring(0, n);
        int diff = 0;
        for (int i = 0; i < n; i++) if (a.charAt(i) != b.charAt(i) && ++diff > 1) return false;
        return true;
    }

    private static boolean wordIn(List<String> textWords, String text, Set<String> vars) {
        for (String v : vars) {
            if (v.contains(" ") || v.contains("'")) {
                if (text.contains(v)) return true;
                continue;
            }
            for (String t : textWords) {
                if (t.startsWith(v) || close(t, v)) return true;
            }
        }
        return false;
    }

    /** Подходит ли текст под запрос: каждое слово запроса должно найтись (с учётом смысла и опечаток). */
    public static boolean matches(String text, String query) {
        if (query == null || query.isBlank()) return true;
        String t = norm(text);
        String q = norm(query).trim();
        if (t.contains(q)) return true;
        List<String> tw = words(t);
        List<String> qw = words(q);
        if (qw.isEmpty()) return true;
        for (String w : qw) {
            if (!wordIn(tw, t, variants(w))) return false;
        }
        return true;
    }
}
