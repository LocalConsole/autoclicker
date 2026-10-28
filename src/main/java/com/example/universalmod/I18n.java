package com.example.universalmod;

import java.util.HashMap;
import java.util.Map;

/** Перевод интерфейса мода: ru (исходный текст), uz, en. */
public class I18n {
    public static final String[] LANGS = {"ru", "uz", "en"};
    public static final String[] LANG_NAMES = {"Русский", "O'zbekcha", "English"};

    private static final Map<String, String[]> D = new HashMap<>(); // ru -> {uz, en}

    private static void a(String ru, String uz, String en) {
        D.put(ru, new String[]{uz, en});
    }

    public static String t(String ru) {
        String lang = ClickerConfig.language;
        if (lang == null || lang.equals("ru")) return ru;
        String[] v = D.get(ru);
        if (v == null) return ru;
        return lang.equals("uz") ? v[0] : v[1];
    }

    public static String langName() {
        for (int i = 0; i < LANGS.length; i++) {
            if (LANGS[i].equals(ClickerConfig.language)) return LANG_NAMES[i];
        }
        return LANG_NAMES[0];
    }

    public static void nextLang() {
        int idx = 0;
        for (int i = 0; i < LANGS.length; i++) {
            if (LANGS[i].equals(ClickerConfig.language)) idx = i;
        }
        ClickerConfig.language = LANGS[(idx + 1) % LANGS.length];
    }

    static {
        a("Выберите предмет", "Buyumni tanlang", "Choose an item");
        a("Поиск: хорус, жемчуг, яблоко...", "Qidiruv: xorus, marvarid, olma...", "Search: chorus, pearl, apple...");
        a("Отмена", "Bekor qilish", "Cancel");
        a("Ничего не найдено", "Hech narsa topilmadi", "Nothing found");
        a("Нажмите на предмет, чтобы выбрать", "Tanlash uchun buyumni bosing", "Click an item to select it");
        a("Выбрать предмет...", "Buyumni tanlash...", "Choose item...");
        a("Нажмите на поле предмета и выберите его из списка", "Buyum maydonini bosing va ro'yxatdan tanlang", "Click the item field and pick it from the list");
        a("Быстрое использование", "Tez ishlatish", "Quick use");
        a("Быстрое использование...", "Tez ishlatish...", "Quick use...");
        a("+ Добавить предмет", "+ Buyum qo'shish", "+ Add item");
        a("Удалить", "O'chirish", "Remove");
        a("Нет предмета: ", "Buyum yo'q: ", "Item not found: ");
        a("Клавиша использует предмет сразу, даже при наведении на верстак, люк и т.п.", "Tugma buyumni darhol ishlatadi, hatto ustaxona yoki lyukka qaratilgan bo'lsa ham", "The key uses the item instantly, even when aiming at a crafting table, trapdoor, etc.");
        a("Название предмета: ender_pearl, chorus_fruit, golden_apple...", "Buyum nomi: ender_pearl, chorus_fruit, golden_apple...", "Item id: ender_pearl, chorus_fruit, golden_apple...");
        a("Пока пусто. Добавьте первый предмет.", "Hozircha bo'sh. Birinchi buyumni qo'shing.", "Empty for now. Add your first item.");
        a("Поиск в контейнерах", "Konteynerlarda qidirish", "Container search");
        a("Зум", "Zoom", "Zoom");
        a("Зум (зажать)", "Zoom (bosib turish)", "Zoom (hold)");
        a("Кратность зума", "Zoom kuchi", "Zoom level");
        a("Плавный зум", "Silliq zoom", "Smooth zoom");
        a("Снижать чувствительность при зуме", "Zoomda sezgirlikni kamaytirish", "Lower sensitivity while zooming");
        a("Убрать наклон экрана при уроне", "Zarba olganda ekran og'ishini o'chirish", "Disable screen tilt on damage");
        a("Назначить", "Tayinlash", "Bind key");
        a("Сортировка", "Saralash", "Sorting");
        a("Кнопки сортировки в контейнере", "Konteynerda saralash tugmalari", "Sort buttons in container");
        a("Кнопки сортировки инвентаря", "Inventarda saralash tugmalari", "Sort buttons in inventory");
        a("Скорость сортировки", "Saralash tezligi", "Sorting speed");
        a(" кл./тик", " klik/tik", " clicks/tick");
        a("Сорт. контейнера: по горизонтали", "Konteyner: gorizontal saralash", "Sort container: horizontal");
        a("Сорт. контейнера: по вертикали", "Konteyner: vertikal saralash", "Sort container: vertical");
        a("Сорт. инвентаря: по горизонтали", "Inventar: gorizontal saralash", "Sort inventory: horizontal");
        a("Сорт. инвентаря: по вертикали", "Inventar: vertikal saralash", "Sort inventory: vertical");
        a("Сортировать по вертикали", "Vertikal saralash", "Sort vertically");
        a("Сортировать по горизонтали", "Gorizontal saralash", "Sort horizontally");
        a("Макрос ", "Makro ", "Macro ");
        a("Сообщение или /команда", "Xabar yoki /buyruq", "Message or /command");
        a("Повтор: ", "Takror: ", "Repeat: ");
        a("Зажим: ", "Bosib turish: ", "Hold: ");
        a("Интервал, мс", "Interval, ms", "Interval, ms");
        a("Запустить / остановить повтор", "Takrorni yoqish / to'xtatish", "Start / stop repeating");
        a("Автоповтор сообщения или команды", "Xabar yoki buyruqni avto takrorlash", "Auto-repeat the message or command");
        a("Повторять, только пока зажата клавиша", "Faqat tugma bosib turilganda takrorlash", "Repeat only while the key is held");
        a("Название предмета", "Buyum nomi", "Item name");
        a("Очистить", "Tozalash", "Clear");
        a("Запомнено контейнеров: ", "Eslab qolingan konteynerlar: ", "Remembered containers: ");
        a("Найдено: ", "Topildi: ", "Found: ");
        a("Одиночная игра: видны все контейнеры рядом", "Yakka o'yin: yaqin atrofdagi barcha konteynerlar ko'rinadi", "Singleplayer: all nearby containers are shown");
        a("Показаны только сундуки, которые были открыты вами", "Faqat siz ochgan sandiqlar ko'rsatiladi", "Only chests you have opened are shown");
        a("Память сохраняется после выхода и при смене ника", "Xotira chiqqandan keyin va nik o'zgarganda ham saqlanadi", "Memory is kept after quitting and when changing nickname");
        a("Подсветка контейнеров", "Konteynerlarni belgilash", "Container highlight");
        a("Поиск предмета...", "Buyum qidirish...", "Search item...");
        a("Радиус подсветки", "Belgilash radiusi", "Highlight radius");
        a(" бл.", " blok", " blocks");
        a("Фулбрайт", "To'liq yorug'lik", "Fullbright");
        a("Смена ника", "Nikni o'zgartirish", "Change nickname");
        a("Применить", "Qo'llash", "Apply");
        a("Ник: 3–16 символов, латиница, цифры и _", "Nik: 3–16 belgi, lotin harflari, raqamlar va _", "Nick: 3–16 chars, letters, digits and _");
        a("Не удалось сменить ник", "Nikni o'zgartirib bo'lmadi", "Failed to change nickname");
        a("Сейчас: ", "Hozir: ", "Current: ");
        a("Только для офлайн-серверов и одиночной игры", "Faqat oflayn serverlar va yakka o'yin uchun", "Offline servers and singleplayer only");
        a("Ник: ", "Nik: ", "Nick: ");
        a("Сохранённых ников нет", "Saqlangan niklar yo'q", "No saved nicknames");
        a("Аккаунт", "Akkaunt", "Account");
        a("Автокликер", "Avtokliker", "Autoclicker");
        a("Быстрые клики", "Tez kliklar", "Fast clicks");
        a("Блоки", "Bloklar", "Blocks");
        a("Шалкеры", "Shulkerlar", "Shulkers");
        a("Яркость", "Yorqinlik", "Brightness");
        a("Интерфейс", "Interfeys", "Interface");
        a("Управление", "Boshqaruv", "Controls");
        a("Язык", "Til", "Language");
        a("Сменить ник...", "Nikni o'zgartirish...", "Change nickname...");
        a("Открыть это меню", "Bu menyuni ochish", "Open this menu");
        a("Чат", "Chat", "Chat");
        a("Копирование строки (Ctrl+ЛКМ)", "Qatorni nusxalash (Ctrl+LKM)", "Copy line (Ctrl+LMB)");
        a("Кнопки после скриншота", "Skrinshotdan keyingi tugmalar", "Screenshot buttons");
        a("Открыть", "Ochish", "Open");
        a("Папка", "Papka", "Folder");
        a("Строка чата скопирована", "Chat qatori nusxalandi", "Chat line copied");
        a("Показ прочности", "Mustahkamlik ko'rsatkichi", "Durability display");
        a("Камера", "Kamera", "Camera");
        a("Осмотр камеры (зажать)", "Kamerani aylantirish (bosib turish)", "Free look (hold)");
        a("ВКЛ", "YOQILGAN", "ON");
        a("ВЫКЛ", "O'CHIRILGAN", "OFF");
        a("Перемещение", "Ko'chirish", "Moving");
        a("Зажмите предмет мышкой и перетащите. Esc — готово.", "Buyumni sichqoncha bilan ushlab torting. Esc — tayyor.", "Hold an item with the mouse and drag it. Esc — done.");
        a("Прочность на экране", "Ekrandagi mustahkamlik", "Durability on screen");
        a("Показ прочности", "Mustahkamlikni ko'rsatish", "Show durability");
        a("Броня", "Zirh", "Armor");
        a("Основная рука", "Asosiy qo'l", "Main hand");
        a("Вторая рука", "Ikkinchi qo'l", "Off hand");
        a("Цвета", "Ranglar", "Colors");
        a("Каждый предмет отдельно", "Har bir buyum alohida", "Each item separately");
        a("Блок X", "Blok X", "Block X");
        a("Блок Y", "Blok Y", "Block Y");
        a("Перетащить мышкой...", "Sichqoncha bilan surish...", "Drag with mouse...");
        a("Назад", "Orqaga", "Back");
        a("Формат: ", "Format: ", "Format: ");
        a("проценты", "foiz", "percent");
        a("цифры", "raqamlar", "numbers");
        a("Цифры: ", "Raqamlar: ", "Numbers: ");
        a("только 262", "faqat 262", "only 262");
        a("Блок: ", "Blok: ", "Block: ");
        a("вертикально", "vertikal", "vertical");
        a("горизонтально", "gorizontal", "horizontal");
        a("Скорость ЛКМ", "Chap tugma tezligi", "LMB speed");
        a("Скорость ПКМ", "O'ng tugma tezligi", "RMB speed");
        a("Автоклик ЛКМ", "Chap tugma avtoklik", "LMB autoclick");
        a("Автоклик ПКМ", "O'ng tugma avtoklik", "RMB autoclick");
        a("Зажать ЛКМ", "Chap tugmani bosib turish", "Hold LMB");
        a("Зажать ПКМ", "O'ng tugmani bosib turish", "Hold RMB");
        a("Быстрый ЛКМ", "Tez chap tugma", "Fast LMB");
        a("Кликов за тик ЛКМ", "Tikda chap tugma kliklari", "LMB clicks per tick");
        a("Быстрый ПКМ", "Tez o'ng tugma", "Fast RMB");
        a("Кликов за тик ПКМ", "Tikda o'ng tugma kliklari", "RMB clicks per tick");
        a("Быстрая установка", "Tez qo'yish", "Fast placing");
        a("Быстрое разрушение", "Tez buzish", "Fast breaking");
        a("Просмотр шалкера", "Shulker ko'rinishi", "Shulker preview");
        a("ЛКМ по кулдауну", "Chap tugma kuldaun bo'yicha", "LMB by cooldown");
        a("Клавиши...", "Tugmalar...", "Keys...");
        a("Макросы...", "Makroslar...", "Macros...");
        a("Показы еды...", "Ovqat ko'rsatkichlari...", "Food displays...");
        a("Прочность на экране (броня / руки)...", "Ekrandagi mustahkamlik (zirh / qo'l)...", "On-screen durability (armor / hands)...");
        a("Готово", "Tayyor", "Done");
        a("Шалкер: ", "Shulker: ", "Shulker: ");
        a("сумма по предметам", "buyumlar yig'indisi", "sum by item");
        a("по слотам", "uyalar bo'yicha", "by slots");
        a("Голод: +", "Ochlik: +", "Hunger: +");
        a("   Насыщение: +", "   To'yinish: +", "   Saturation: +");
        a("Насыщение: ", "To'yinish: ", "Saturation: ");
        a("Показы еды", "Ovqat ko'rsatkichlari", "Food displays");
        a("Насыщение числом", "To'yinish raqamda", "Saturation as number");
        a("Насыщение (золотая рамка)", "To'yinish (oltin ramka)", "Saturation (gold frame)");
        a("Предпросмотр еды в руке", "Qo'ldagi ovqat ko'rinishi", "Preview of held food");
        a("Значения еды в подсказке", "Ovqat qiymatlari maslahatda", "Food values in tooltip");
        a("Клавиши Universal Mod", "Universal Mod tugmalari", "Universal Mod keys");
        a(": > нажмите клавишу <", ": > tugmani bosing <", ": > press a key <");
        a("1 раз за нажатие", "Bosganda 1 marta", "Once per press");
        a("Повтор, пока зажато", "Bosib turilganda takrorlash", "Repeat while held");
        a("Авто: вкл/выкл клавишей", "Avto: tugma bilan yoq/o'ch", "Auto: toggle by key");
        a("не назначена", "belgilanmagan", "not assigned");
        a("Макрос", "Makros", "Macro");
        a("Текст", "Matn", "Text");
        a("Секунды", "Soniyalar", "Seconds");
        a("Миллисекунды", "Millisekundlar", "Milliseconds");
        a("Удалить макрос", "Makrosni o'chirish", "Delete macro");
        a("Клавиша: > нажмите клавишу/кнопку <", "Tugma: > tugma/knopkani bosing <", "Key: > press a key/button <");
        a("Клавиша: ", "Tugma: ", "Key: ");
        a("Режим: ", "Rejim: ", "Mode: ");
        a("Макрос: ", "Makros: ", "Macro: ");
        a("Сообщение или /команда:", "Xabar yoki /buyruq:", "Message or /command:");
        a("Интервал: секунды", "Interval: soniya", "Interval: seconds");
        a("миллисекунды", "millisekund", "milliseconds");
        a("Макросы", "Makroslar", "Macros");
        a("+ Добавить макрос", "+ Makros qo'shish", "+ Add macro");
        a("(пусто)", "(bo'sh)", "(empty)");
        a("зажим", "ushlash", "hold");
        a("авто", "avto", "auto");
        a("(выкл) ", "(o'chiq) ", "(off) ");
        a("Авто-макрос ", "Avto-makros ", "Auto macro ");
        a("Автокликер ЛКМ", "Chap tugma avtokliker", "LMB autoclicker");
        a("Автокликер ПКМ", "O'ng tugma avtokliker", "RMB autoclicker");
        a("Шалкер: режим", "Shulker: rejim", "Shulker: mode");
        a("Открыть настройки", "Sozlamalarni ochish", "Open settings");
        a("Быстрая установка блоков", "Bloklarni tez qo'yish", "Fast block placing");
        a("Быстрое разрушение блоков", "Bloklarni tez buzish", "Fast block breaking");
        a("ЛКМ по кулдауну оружия", "Chap tugma qurol kuldauni bo'yicha", "LMB by weapon cooldown");
        a("Клавиша (бинд):", "Tugma (bind):", "Key (bind):");
        a("Сбросить клавишу", "Tugmani olib tashlash", "Clear key");
        a("Автоповтор: ", "Avto-takror: ", "Auto-repeat: ");
        a("Автоматически повторять сообщение или команду", "Xabar yoki buyruqni avtomatik takrorlash", "Automatically repeat the message or command");
        a("Режим: клавиша вкл/выкл", "Rejim: tugma yoq/o'ch", "Mode: key toggles");
        a("Режим: пока зажата", "Rejim: bosib turilganda", "Mode: while held");
        a("Нажал клавишу — повтор включился, нажал ещё раз — выключился", "Tugma bosildi — takror yoqildi, yana bosildi — o'chdi", "Press the key to start repeating, press again to stop");
        a("Повтор идёт, только пока клавиша зажата", "Takror faqat tugma bosib turilganda ishlaydi", "Repeats only while the key is held down");
        a("Интервал повтора, мс:", "Takror intervali, ms:", "Repeat interval, ms:");
        a("Запустить сейчас", "Hozir ishga tushirish", "Start now");
        a("Остановить", "To'xtatish", "Stop");
        a("Слишком частый повтор может кикнуть за спам", "Juda tez takror spam uchun chiqarib yuborishi mumkin", "Repeating too fast can get you kicked for spam");
        a("Нет макросов. Добавьте первый слева.", "Makros yo'q. Birinchisini chapdan qo'shing.", "No macros yet. Add the first one on the left.");
        a("Удалить макрос", "Makrosni o'chirish", "Delete macro");
        a("Забрать всё из контейнера в инвентарь", "Konteynerdagi hamma narsani inventarga olish", "Take everything from the container to the inventory");
        a("Положить всё из инвентаря в контейнер", "Inventardagi hamma narsani konteynerga solish", "Put everything from the inventory into the container");
        a("Забрать все такие же предметы, как в руке", "Qo'ldagi bilan bir xil barcha narsalarni olish", "Take all items matching the one in your hand");
        a("Положить все такие же предметы, как в руке", "Qo'ldagi bilan bir xil barcha narsalarni solish", "Put all items matching the one in your hand");
        a("Возьмите предмет в руку", "Qo'lingizga narsa oling", "Hold an item in your hand");
        a("Автозаполнение", "Avto-to'ldirish", "Auto-refill");
        a("Автозаполнение хотбара", "Xotbarni avto-to'ldirish", "Hotbar auto-refill");
        a("Пополнять, если меньше", "Kam bo'lsa to'ldirish", "Refill when fewer than");
        a("Слот ", "Uyacha ", "Slot ");
        a("Невидимые блоки", "Ko'rinmas bloklar", "Invisible blocks");
        a("Показывать невидимые блоки", "Ko'rinmas bloklarni ko'rsatish", "Show invisible blocks");
        a("Барьер", "Barer", "Barrier");
        a("Структурная пустота", "Strukturaviy bo'shliq", "Structure void");
        a("Блок света", "Yorug'lik bloki", "Light block");
        a("Радиус показа", "Ko'rsatish radiusi", "Display radius");
        a("Частицы невидимости", "Ko'rinmaslik zarralari", "Invisibility particles");
        a("Частицы зелья невидимости", "Ko'rinmaslik iksiri zarralari", "Invisibility potion particles");
        a("Обычные", "Oddiy", "Default");
        a("Сердечки", "Yuraklar", "Hearts");
        a("Радость", "Quvonch", "Happy");
        a("Огонь", "Olov", "Flame");
        a("Звёздочки", "Yulduzchalar", "Sparkles");
        a("Крит", "Kritik", "Crit");
        a("Ноты", "Notalar", "Notes");
        a("Гнев", "G'azab", "Angry");
        a("Души", "Ruhlar", "Souls");
        a("Рамки блоков", "Blok ramkalari", "Block outlines");
        a("Показывать рамки блоков", "Blok ramkalarini ko'rsatish", "Show block outlines");
        a("Подозрительный песок", "Gumon qum", "Suspicious sand");
        a("Подозрительный гравий", "Gumon shag'al", "Suspicious gravel");
        a("Светокамень", "Yorug'tosh", "Glowstone");
        a("Рыхлый снег", "G'ovak qor", "Powder snow");
        a("Радиус рамок", "Ramka radiusi", "Outline radius");
        a("Контур блока", "Blok konturi", "Block outline");
        a("Контур блока под прицелом", "Nishondagi blok konturi", "Targeted block outline");
        a("Контур блока: переключить цвет", "Blok konturi: rangni almashtirish", "Block outline: cycle color");
        a("Выкл", "O'chiq", "Off");
        a("Светло-серый", "Och kulrang", "Light gray");
        a("Красный", "Qizil", "Red");
        a("Зелёный", "Yashil", "Green");
        a("Автозаполнение второй руки", "Ikkinchi qo'lni avto-to'ldirish", "Offhand auto-refill");
    }
}
