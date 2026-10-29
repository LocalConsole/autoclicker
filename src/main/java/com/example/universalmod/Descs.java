package com.example.universalmod;

import java.util.HashMap;
import java.util.Map;

/** Короткие описания к разделам (ключ с «§») и настройкам меню. ru / uz / en. */
public class Descs {
    private static final Map<String, String[]> M = new HashMap<>();

    private static void d(String key, String ru, String uz, String en) {
        M.put(key, new String[]{ru, uz, en});
    }

    public static String get(String key) {
        String[] v = M.get(key);
        if (v == null) return null;
        String l = ClickerConfig.language;
        if (l == null || l.equals("ru")) return v[0];
        return l.equals("uz") ? v[1] : v[2];
    }

    static {
        // ---------- Разделы ----------
        d("§Язык", "Язык интерфейса мода.", "Mod interfeysi tili.", "Mod interface language.");
        d("§Аккаунт", "Выбор и смена ника.", "Nikni tanlash va almashtirish.", "Pick and switch your nickname.");
        d("§Автокликер", "Автоматические клики мышью с заданной скоростью.", "Sichqoncha bilan avtomatik klik.", "Automatic mouse clicks at a set speed.");
        d("§Быстрые клики", "Несколько кликов за один тик, пока кнопка зажата.", "Tugma bosilganda bir tikda bir necha klik.", "Several clicks per tick while the button is held.");
        d("§Блоки", "Ускорение установки и разрушения блоков.", "Bloklarni qo'yish va buzishni tezlatish.", "Faster block placing and breaking.");
        d("§Шалкеры", "Содержимое шалкера в подсказке при наведении.", "Sichqoncha ustida shalker ichidagilar ko'rinadi.", "Shulker contents shown in the tooltip.");
        d("§Яркость", "Видно в темноте без факелов.", "Mash'alsiz qorong'ida ko'rish.", "See in the dark without torches.");
        d("§Камера", "Свободный осмотр и эффекты камеры.", "Erkin qarash va kamera effektlari.", "Free look and camera effects.");
        d("§Невидимые блоки", "Показывает барьеры, структурную пустоту и блоки света.", "To'siq, strukturaviy bo'shliq va yorug'lik bloklarini ko'rsatadi.", "Shows barriers, structure voids and light blocks.");
        d("§Рамки блоков", "Подсвечивает важные блоки рамкой.", "Muhim bloklarni ramka bilan ajratadi.", "Highlights important blocks with a frame.");
        d("§Контур блока", "Свой контур блока, на который наведён прицел.", "Nishon qaratilgan blokning o'z konturi.", "Custom outline for the block you look at.");
        d("§Частицы невидимости", "Вид частиц от зелья невидимости.", "Ko'rinmaslik iksiri zarralari ko'rinishi.", "Look of invisibility potion particles.");
        d("§Автозаполнение", "Пополняет слоты предметами из инвентаря, когда они кончаются.", "Slotlar tugaganda inventardan to'ldiradi.", "Refills slots from inventory when they run out.");
        d("§Перемещение предметов", "Быстрый перенос предметов в инвентаре.", "Inventarda buyumlarni tez ko'chirish.", "Fast item moving in inventory.");
        d("§Метки", "Свои метки в мире с расстоянием на экране.", "Dunyodagi o'z belgilaringiz va masofa.", "Your own world markers with distance on screen.");
        d("§Линейка", "Измеряет расстояние между точками.", "Nuqtalar orasidagi masofani o'lchaydi.", "Measures distance between points.");
        d("§Смена предмета в хотбаре", "Клавиши берут копьё, булаву или элитры из хотбара.", "Tugmalar xotbardan nayza, gurz yoki elitrani oladi.", "Keys grab a spear, mace or elytra from the hotbar.");
        d("§Подсветка хитбокса цели", "Хитбокс цели светится, когда оружие перезарядилось.", "Qurol tayyor bo'lganda nishon hitboxi yonadi.", "Target hitbox glows when your weapon is ready.");
        d("§Авто-кристалл", "После обсидиана сверху сам ставит кристалл Энда.", "Obsidian ustiga o'zi End kristalini qo'yadi.", "Places an End crystal on obsidian by itself.");
        d("§Моды", "Список установленных модов.", "O'rnatilgan modlar ro'yxati.", "List of installed mods.");
        d("§Сортировка", "Сортировка, перенос и выброс (✕) предметов в контейнере.", "Konteynerda saralash, ko'chirish va tashlash (✕).", "Sorting, moving and dropping (✕) items in containers.");
        d("§Зум", "Приближение камеры по зажатой клавише.", "Tugma bosilganda kamerani yaqinlashtirish.", "Zoom the camera while a key is held.");
        d("§Поиск в контейнерах", "Подсвечивает ближайшие контейнеры с нужным предметом.", "Kerakli buyumli yaqin konteynerlarni yoritadi.", "Highlights nearby containers with an item.");
        d("§Чат", "Удобства чата.", "Chat qulayliklari.", "Chat conveniences.");
        d("§Интерфейс", "Макросы, еда и прочность на экране.", "Makrolar, ovqat va mustahkamlik ekranda.", "Macros, food and durability on screen.");
        d("§Управление", "Клавиша, которая открывает это меню.", "Shu menyuni ochuvchi tugma.", "The key that opens this menu.");

        // ---------- Автокликер ----------
        d("Скорость ЛКМ", "Кликов левой кнопкой в секунду.", "Soniyasiga chap tugma kliklari.", "Left clicks per second.");
        d("Скорость ПКМ", "Кликов правой кнопкой в секунду.", "Soniyasiga o'ng tugma kliklari.", "Right clicks per second.");
        d("Автоклик ЛКМ", "Сам кликает левой кнопкой.", "Chap tugmani o'zi bosadi.", "Clicks the left button for you.");
        d("Автоклик ПКМ", "Сам кликает правой кнопкой.", "O'ng tugmani o'zi bosadi.", "Clicks the right button for you.");
        d("Зажать ЛКМ", "Держит левую кнопку нажатой (копать, бить).", "Chap tugmani bosib turadi (qazish, urish).", "Holds the left button (dig, attack).");
        d("Зажать ПКМ", "Держит правую кнопку нажатой.", "O'ng tugmani bosib turadi.", "Holds the right button.");
        d("ЛКМ по кулдауну", "Бьёт, как только оружие перезарядилось: по мобу, по блоку или в воздух.", "Qurol tayyor bo'lishi bilan uradi: mavjudotga, blokka yoki havoga.", "Hits as soon as the weapon is recharged: at a mob, a block or thin air.");

        // ---------- Быстрые клики ----------
        d("Быстрый ЛКМ", "Много ударов за тик при зажатой кнопке.", "Tugma bosilganda bir tikda ko'p zarba.", "Many attacks per tick while held.");
        d("Кликов за тик ЛКМ", "Сколько ударов делать за один тик.", "Bir tikda necha zarba.", "How many attacks per tick.");
        d("Быстрый ПКМ", "Много использований за тик при зажатой кнопке.", "Tugma bosilganda bir tikda ko'p ishlatish.", "Many uses per tick while held.");
        d("Кликов за тик ПКМ", "Сколько использований за один тик.", "Bir tikda necha ishlatish.", "How many uses per tick.");

        // ---------- Блоки ----------
        d("Быстрая установка", "Убирает задержку между установками блоков.", "Bloklarni qo'yish orasidagi kechikishni olib tashlaydi.", "Removes the delay between block placements.");
        d("Вода: быстрый ПКМ", "Если в руке ведро воды, ПКМ работает как быстрый клик (число кликов за тик берётся из быстрых кликов).", "Qo'lda suv chelagi bo'lsa, ONG tez klik bo'ladi (tikdagi kliklar soni tez kliklardan olinadi).", "With a water bucket in hand, right click acts as fast click (clicks per tick come from the fast click setting).");
        d("Быстрое разрушение", "Убирает задержку между разрушением блоков.", "Bloklarni buzish orasidagi kechikishni olib tashlaydi.", "Removes the delay between breaking blocks.");

        // ---------- Шалкеры ----------
        d("Просмотр шалкера", "Показывает содержимое шалкера в подсказке.", "Shalker ichidagilarni maslahatda ko'rsatadi.", "Shows shulker contents in the tooltip.");
        d("Шалкер: режим", "Сумма по предметам или по слотам.", "Buyumlar yig'indisi yoki slotlar bo'yicha.", "Totals per item or per slot.");
        d("Пометка «переименован на наковальне»", "Отмечает шалкеры, переименованные на наковальне.", "Sandonda nomi o'zgargan shalkerlarni belgilaydi.", "Marks shulkers renamed at an anvil.");

        // ---------- Яркость / Камера ----------
        d("Фулбрайт", "Максимальная яркость везде.", "Hamma joyda maksimal yorqinlik.", "Maximum brightness everywhere.");
        d("Осмотр камеры (зажать)", "Крутить камеру, не поворачивая персонажа.", "Qahramonni burmasdan kamerani aylantirish.", "Look around without turning the player.");
        d("Убрать наклон экрана при уроне", "Экран не качается при получении урона.", "Zarba olganda ekran chayqalmaydi.", "Screen does not tilt when you take damage.");

        // ---------- Невидимые блоки ----------
        d("Показывать невидимые блоки", "Главный выключатель показа невидимых блоков.", "Ko'rinmas bloklarni ko'rsatish asosiy tugmasi.", "Main switch for showing invisible blocks.");
        d("Барьер", "Показывать барьеры.", "To'siqlarni ko'rsatish.", "Show barriers.");
        d("Структурная пустота", "Показывать блоки структурной пустоты.", "Strukturaviy bo'shliq bloklarini ko'rsatish.", "Show structure void blocks.");
        d("Блок света", "Показывать блоки света.", "Yorug'lik bloklarini ko'rsatish.", "Show light blocks.");
        d("Радиус показа", "На каком расстоянии искать такие блоки.", "Bunday bloklarni qancha masofada qidirish.", "How far to look for such blocks.");
        d("Контур невидимых блоков", "Рисует контур вокруг невидимых блоков.", "Ko'rinmas bloklar atrofida kontur chizadi.", "Draws an outline around invisible blocks.");
        d("Цвет: барьер", "Цвет подсветки барьера.", "To'siq rangi.", "Barrier highlight color.");
        d("Цвет: структурная пустота", "Цвет подсветки структурной пустоты.", "Strukturaviy bo'shliq rangi.", "Structure void highlight color.");
        d("Цвет: блок света", "Цвет подсветки блока света.", "Yorug'lik bloki rangi.", "Light block highlight color.");

        // ---------- Рамки блоков ----------
        d("Показывать рамки блоков", "Главный выключатель рамок.", "Ramkalarning asosiy tugmasi.", "Main switch for frames.");
        d("Подозрительный песок", "Рамка у подозрительного песка.", "Shubhali qum ramkasi.", "Frame on suspicious sand.");
        d("Подозрительный гравий", "Рамка у подозрительного гравия.", "Shubhali shag'al ramkasi.", "Frame on suspicious gravel.");
        d("Светокамень", "Рамка у светокамня.", "Yorug'tosh ramkasi.", "Frame on glowstone.");
        d("Рыхлый снег", "Рамка у рыхлого снега.", "G'ovak qor ramkasi.", "Frame on powder snow.");
        d("Сплошной контур вместо частиц", "Сплошная линия вместо частиц.", "Zarralar o'rniga uzluksiz chiziq.", "Solid line instead of particles.");
        d("Цвет: подозрительный песок", "Цвет рамки песка.", "Qum ramkasi rangi.", "Sand frame color.");
        d("Цвет: подозрительный гравий", "Цвет рамки гравия.", "Shag'al ramkasi rangi.", "Gravel frame color.");
        d("Цвет: светокамень", "Цвет рамки светокамня.", "Yorug'tosh ramkasi rangi.", "Glowstone frame color.");
        d("Цвет: рыхлый снег", "Цвет рамки снега.", "Qor ramkasi rangi.", "Snow frame color.");
        d("Радиус рамок", "На каком расстоянии рисовать рамки.", "Ramkalarni qancha masofada chizish.", "How far to draw frames.");

        // ---------- Контур / частицы ----------
        d("Свой контур выбранного блока", "Своя рамка с ореолом вместо ванильного контура.", "Oddiy kontur o'rniga nurli o'z ramkasi.", "Own glowing frame instead of the vanilla outline.");
        d("Цвет контура", "Цвет контура блока под прицелом.", "Nishondagi blok konturi rangi.", "Color of the targeted block outline.");
        d("Частицы зелья невидимости", "Какие частицы видны вокруг невидимого.", "Ko'rinmasda qanday zarralar ko'rinadi.", "Which particles show around invisible players.");
        d("Частицы невидимости у мобов", "Показывать частицы и у мобов.", "Zarralarni mobda ham ko'rsatish.", "Show particles on mobs too.");

        // ---------- Автозаполнение ----------
        d("Автозаполнение хотбара", "Докладывает предметы в слоты хотбара.", "Xotbar slotlariga buyum qo'shadi.", "Tops up hotbar slots.");
        d("Автозаполнение второй руки", "Докладывает предмет во вторую руку.", "Ikkinchi qo'lga buyum qo'shadi.", "Tops up the offhand.");
        d("Пополнять, если меньше", "Порог: сколько штук осталось в слоте.", "Chegara: slotda necha dona qolgan.", "Threshold: items left in the slot.");

        // ---------- Перемещение ----------
        d("Колёсико над слотом переносит предметы", "Прокрутка над слотом двигает предметы.", "Slot ustida g'ildirak buyumlarni ko'chiradi.", "Scrolling over a slot moves items.");
        d("Shift + ЛКМ: провести по слотам", "Зажми Shift и проведи по слотам.", "Shift bosib slotlar ustidan yurgizing.", "Hold Shift and drag over slots.");
        d("Ctrl + Shift + клик: все такие же", "Переносит все одинаковые предметы.", "Barcha bir xil buyumlarni ko'chiradi.", "Moves every matching item.");

        // ---------- Метки / Линейка ----------
        d("Поставить метку здесь", "Клавиша ставит метку на твоём месте.", "Tugma turgan joyingizga belgi qo'yadi.", "Key drops a marker where you stand.");
        d("Меню меток...", "Список меток: правка и удаление.", "Belgilar ro'yxati: tahrirlash va o'chirish.", "Marker list: edit and delete.");
        d("Показывать метки на экране", "Метки и расстояние до них на экране.", "Belgilar va masofa ekranda.", "Markers and distances on screen.");
        d("Цвет новой метки", "Цвет, которым создаётся новая метка.", "Yangi belgi rangi.", "Color of newly created markers.");
        d("Радиус чанков спавна", "Размер зоны спавна для метки спавна.", "Spawn belgisi uchun zona o'lchami.", "Spawn zone size for the spawn marker.");
        d("Поставить точку линейки", "Клавиша ставит точку измерения.", "Tugma o'lchash nuqtasini qo'yadi.", "Key places a measuring point.");
        d("Очистить линейку", "Клавиша убирает все точки.", "Tugma barcha nuqtalarni o'chiradi.", "Key clears all points.");
        d("Показывать линейку", "Линия и расстояние между точками.", "Nuqtalar orasidagi chiziq va masofa.", "Line and distance between points.");
        d("Цвет линейки", "Цвет линии измерения.", "O'lchov chizig'i rangi.", "Measuring line color.");
        d("Стрелки внутрь (>—<)", "Стрелки на концах смотрят внутрь.", "Uchlardagi o'qlar ichkariga qaraydi.", "End arrows point inward.");

        // ---------- Смена предмета ----------
        d("Копьё: взять из хотбара", "Клавиша берёт копьё из хотбара.", "Tugma xotbardan nayzani oladi.", "Key takes the spear from the hotbar.");
        d("Копьё: бить ЛКМ по цели", "После взятия копья бьёт цель.", "Nayzani olgach nishonga uradi.", "Attacks the target after taking it.");
        d("Копьё: режим свапа (ждать цель)", "Ждёт цель, бьёт и возвращает слот.", "Nishonni kutadi, uradi, slotni qaytaradi.", "Waits for a target, hits, restores the slot.");
        d("Копьё: свап без цели (рывок)", "Свап копьём и без цели (рывок).", "Nishonsiz ham nayza bilan almashish (otilish).", "Swap with a spear even without a target (lunge).");
        d("Булава: взять из хотбара", "Клавиша берёт булаву из хотбара.", "Tugma xotbardan gurzni oladi.", "Key takes the mace from the hotbar.");
        d("Булава: бить ЛКМ по цели", "После взятия булавы бьёт цель.", "Gurzni olgach nishonga uradi.", "Attacks the target after taking it.");
        d("Булава: режим свапа (ждать цель)", "Ждёт цель, бьёт и возвращает слот.", "Nishonni kutadi, uradi, slotni qaytaradi.", "Waits for a target, hits, restores the slot.");
        d("Элитры: взять из хотбара", "Клавиша берёт элитры из хотбара.", "Tugma xotbardan elitrani oladi.", "Key takes elytra from the hotbar.");
        d("Элитры: сразу надеть", "Сразу надевает взятые элитры.", "Olingan elitrani darrov kiyadi.", "Equips the elytra right away.");
        d("Вернуть прошлый слот после свапа", "После свапа уходит на пустой слот хотбара; если пустых нет, возвращает прежний слот.", "Almashgach bo'sh hotbar slotiga o'tadi; bo'sh bo'lmasa, oldingi slotni qaytaradi.", "After a swap goes to an empty hotbar slot; if none, returns to the previous slot.");
        d("Ждать цель в прицеле", "Сколько секунд ждать цель.", "Nishonni necha soniya kutish.", "How many seconds to wait for a target.");
        d("Ждать перезарядку оружия", "Бьёт только после перезарядки оружия.", "Faqat qurol tayyor bo'lgach uradi.", "Hits only once the weapon is recharged.");

        // ---------- Хитбокс / кристалл / моды ----------
        d("Подсвечивать хитбокс, когда можно бить", "Хитбокс цели светится, когда удар готов.", "Zarba tayyor bo'lganda hitbox yonadi.", "Target hitbox glows when a hit is ready.");
        d("Цвет хитбокса", "Цвет подсветки хитбокса.", "Hitbox yoritish rangi.", "Hitbox highlight color.");
        d("Кристалл на обсидиан", "Ставит кристалл Энда на обсидиан сверху.", "Obsidian ustiga End kristalini qo'yadi.", "Places an End crystal on top of obsidian.");
        d("Список модов...", "Открывает список модов.", "Modlar ro'yxatini ochadi.", "Opens the mod list.");
        d("Кнопка «Моды» в меню", "Кнопка списка модов в главном меню.", "Asosiy menyuda modlar tugmasi.", "Mods button in the main menu.");

        // ---------- Сортировка ----------
        d("Кнопки сортировки в контейнере", "Кнопки сортировки в окне контейнера.", "Konteyner oynasida saralash tugmalari.", "Sort buttons in the container window.");
        d("Кнопки сортировки инвентаря", "Кнопки сортировки в инвентаре.", "Inventarda saralash tugmalari.", "Sort buttons in the inventory.");
        d("Скорость сортировки", "Сколько кликов делать за один тик.", "Bir tikda nechta klik.", "Clicks per tick while sorting.");
        d("Скорость выброса (✕)", "Сколько слотов выбрасывать за тик.", "Bir tikda nechta slot tashlash.", "Slots dropped per tick.");
        d("Сорт. контейнера: по горизонтали", "Клавиша: строки слева направо.", "Tugma: chapdan o'ngga qatorlar.", "Key: sort rows left to right.");
        d("Сорт. контейнера: по вертикали", "Клавиша: столбцы сверху вниз.", "Tugma: yuqoridan pastga ustunlar.", "Key: sort columns top to bottom.");
        d("Сорт. инвентаря: по горизонтали", "Клавиша: строки слева направо.", "Tugma: chapdan o'ngga qatorlar.", "Key: sort rows left to right.");
        d("Сорт. инвентаря: по вертикали", "Клавиша: столбцы сверху вниз.", "Tugma: yuqoridan pastga ustunlar.", "Key: sort columns top to bottom.");

        // ---------- Зум ----------
        d("Зум", "Главный выключатель зума.", "Zoomning asosiy tugmasi.", "Main zoom switch.");
        d("Зум (зажать)", "Клавиша: приближает, пока зажата.", "Tugma: bosib turilganda yaqinlashtiradi.", "Key: zooms while held.");
        d("Кратность зума", "Во сколько раз приближать.", "Necha marta yaqinlashtirish.", "How many times to magnify.");
        d("Плавный зум", "Приближение идёт плавно.", "Yaqinlashish silliq bo'ladi.", "Zoom eases in smoothly.");
        d("Снижать чувствительность при зуме", "Мышь медленнее при приближении.", "Yaqinlashganda sichqoncha sekinroq.", "Mouse is slower while zoomed.");

        // ---------- Поиск / чат / интерфейс ----------
        d("Подсветка контейнеров", "Подсвечивает найденные контейнеры.", "Topilgan konteynerlarni yoritadi.", "Highlights matching containers.");
        d("Поиск предмета...", "Окно поиска предмета в контейнерах.", "Konteynerlarda buyum qidirish oynasi.", "Item search window for containers.");
        d("Радиус подсветки", "На каком расстоянии искать контейнеры.", "Konteynerlarni qancha masofada qidirish.", "How far to look for containers.");
        d("Копирование строки (Ctrl+ЛКМ)", "Ctrl + ЛКМ по строке чата копирует её.", "Chat qatoriga Ctrl + LKM nusxalaydi.", "Ctrl + click on a chat line copies it.");
        d("Кнопки после скриншота", "Кнопки действий под сообщением о скриншоте.", "Skrinshot xabari ostida tugmalar.", "Action buttons after a screenshot.");
        d("Макросы...", "Окно настройки макросов.", "Makrolarni sozlash oynasi.", "Macro setup window.");
        d("Показы еды...", "Настройки показа еды на экране.", "Ekranda ovqat ko'rsatish sozlamalari.", "Food display settings.");
        d("Прочность на экране (броня / руки)...", "Прочность брони и предметов в руках.", "Zirh va qo'ldagi buyumlar mustahkamligi.", "Armor and held item durability.");
        d("Открыть это меню", "Клавиша открытия этого меню.", "Shu menyuni ochish tugmasi.", "Key that opens this menu.");
    }
}
