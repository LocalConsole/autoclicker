package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClickerConfig {
    public static final int MIN_CPS = 1;
    public static final int MAX_CPS = 30;
    public static final int MIN_FAST = 1;
    public static final int MAX_FAST = 20;
    public static final int MIN_RADIUS = 4;
    public static final int MAX_RADIUS = 32;

    public static int leftCps = 10;
    public static int rightCps = 10;
    public static boolean fastPlace = false;
    public static boolean fastBreak = false;
    public static boolean fastLeftClick = false;
    public static boolean fastRightClick = false;
    public static int fastLeftCount = 4;
    public static int fastRightCount = 4;
    public static boolean shulkerPreview = true;
    public static boolean shulkerSum = true;
    public static boolean shulkerRenamedNote = true;
    public static boolean leftCooldown = false;
    public static boolean fullbright = false;
    public static boolean foodHud = true;
    public static boolean macrosEnabled = true;
    public static boolean chatCopy = true;
    public static boolean screenshotButtons = true;
    public static String nickname = "";
    public static List<String> nicknames = new ArrayList<>();
    public static String language = "ru"; // ru / uz / en
    public static boolean foodSaturation = true;
    public static boolean foodPreview = true;
    public static boolean foodTooltip = true;
    public static boolean foodSatNumber = true;
    public static boolean containerSearch = true;
    public static boolean autoLeft = false;
    public static boolean autoRight = false;
    public static boolean holdLeft = false;
    public static boolean holdRight = false;
    /** Авто-копалка (зажать ЛКМ): следить за прочностью инструмента. */
    public static boolean digEnabled = true;
    public static int digLowPercent = 10;
    public static int digLowAction = 1;
    public static int digBreakAction = 1;
    public static String searchQuery = "";
    public static boolean sortButtons = true;
    public static boolean refillHotbar = true;
    public static boolean refillOffhand = true;
    public static int refillMin = 1;
    public static boolean showInvisible = false;
    public static boolean invisBarrier = true;
    public static boolean invisVoid = true;
    public static boolean invisLight = true;
    public static int invisRadius = 16;
    public static int invisParticle = 0;
    public static boolean showFrames = false;
    public static boolean frameSand = true;
    public static boolean frameGravel = true;
    public static boolean frameGlowstone = true;
    public static boolean framePowderSnow = true;
    public static int frameRadius = 12;
    /** Свой контур блока, на который наведён прицел: 0 — светло-серый, 1 — красный, 2 — зелёный, 3 — синий. */
    public static boolean targetOutline = false;
    public static int targetOutlineColor = 0;
    public static boolean slotWheel = true;
    public static boolean slotDrag = true;
    public static boolean slotMatchAll = true;
    public static boolean modsButton = true;
    public static boolean wpShow = true;
    public static int wpNewColor = 0;
    public static boolean spawnKnown = false;
    public static int spawnX = 0;
    public static int spawnZ = 0;
    public static int spawnRadius = 2;
    public static boolean rulerShow = true;
    public static int rulerColor = 4;
    public static boolean rulerArrowsIn = false;
    public static boolean frameSolid = true;
    public static int frameSnowColor = 4;
    public static int frameSandColor = 2;
    public static int frameGravelColor = 1;
    public static int frameGlowColor = 7;
    public static int invisBarrierColor = 0;
    public static int invisVoidColor = 6;
    public static int invisLightColor = 3;
    public static boolean invisOutline = true;
    public static boolean invisMobs = true;
    /** Автозаполнение по слотам: 0..8 — хотбар, 9 — вторая рука. По умолчанию везде выключено. */
    public static boolean[] refillSlots = new boolean[10];
    public static boolean sortInvButtons = true;
    public static int sortSpeed = 10;
    public static int dropSpeed = 10;
    public static boolean zoom = true;
    public static boolean noHurtTilt = false;
    /** Смена предмета в хотбаре: авто-действие для каждого предмета, время ожидания цели (1–10 с). */
    public static boolean swapSpearAuto = true;
    public static boolean swapMaceAuto = true;
    public static boolean swapElytraAuto = true;
    public static int swapWait = 3;
    public static boolean swapWaitCooldown = true;
    /** Режим свапа: клавиша не переключает слот, а ждёт цель и в один тик берёт предмет, бьёт и возвращает слот. */
    public static boolean swapSpearMode = false;
    public static boolean swapMaceMode = false;
    /** Свап копьём без цели (рывок Lunge). */
    public static boolean swapSpearLunge = false;
    public static boolean swapReturn = true;
    /** Подсветка хитбокса цели в прицеле, когда оружие перезарядилось (цвет — из палитры меток). */
    public static boolean hitboxHighlight = true;
    /** Прочность брони игрока под прицелом. */
    public static boolean targetArmorHud = true;
    /** Эффекты зелий с таймером и число тотемов на экране. */
    public static boolean effectsHud = true;
    public static boolean effectsTotems = true;
    public static int effectsPos = 0;
    /** Метка при смерти и предупреждение о низкой прочности. */
    public static boolean deathMark = true;
    public static boolean durWarn = true;
    public static int durWarnPercent = 10;
    /** Свои координаты с пересчётом в соседнее измерение и счётчик предмета из руки. */
    public static boolean coordsHud = false;
    public static int coordsPos = 2;
    public static boolean itemCounter = true;
    /** Авто-кристалл: после установки обсидиана сверху сам ставится кристалл Энда из хотбара. */
    public static boolean autoCrystal = false;
    public static int hitboxColor = 3;
    public static float zoomLevel = 4.0f;
    public static boolean zoomSmooth = true;
    public static boolean zoomSens = true;
    public static int searchRadius = 10;
    public static boolean armorHud = true;
    public static boolean armorShowArmor = true;
    public static boolean armorShowMain = true;
    public static boolean armorShowOff = true;
    public static boolean armorPercent = true;
    public static boolean armorShowMax = true;
    public static boolean armorColors = true;
    public static boolean armorVertical = true;
    public static boolean armorSeparate = false;
    // Позиции каждого предмета по отдельности (тысячные доли экрана):
    // 0 шлем, 1 нагрудник, 2 поножи, 3 ботинки, 4 основная рука, 5 вторая рука
    public static int[] armorPosX = new int[6];
    public static int[] armorPosY = {300, 370, 440, 510, 580, 650};
    public static int armorX = 0;
    public static int armorY = 400; // в тысячных долях экрана (0..1000)

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod.json");

    private static class Data {
        int leftCps = 10;
        int rightCps = 10;
        boolean fastPlace = false;
        boolean fastBreak = false;
        boolean fastLeftClick = false;
        boolean fastRightClick = false;
        int fastLeftCount = 4;
        int fastRightCount = 4;
        boolean shulkerPreview = true;
        boolean shulkerSum = true;
        boolean shulkerRenamedNote = true;
        boolean leftCooldown = false;
        boolean fullbright = false;
        boolean foodHud = true;
        boolean macrosEnabled = true;
        boolean chatCopy = true;
        boolean screenshotButtons = true;
        String nickname = "";
        List<String> nicknames = new ArrayList<>();
        String language = "ru";
        boolean foodSaturation = true;
        boolean foodPreview = true;
        boolean foodTooltip = true;
        boolean foodSatNumber = true;
        boolean containerSearch = true;
        boolean autoLeft = false;
        boolean autoRight = false;
        boolean holdLeft = false;
        boolean holdRight = false;
        boolean digEnabled = true;
        int digLowPercent = 10;
        int digLowAction = 1;
        int digBreakAction = 1;
        String searchQuery = "";
        boolean sortButtons = true;
        boolean refillHotbar = true;
        boolean refillOffhand = true;
        int refillMin = 1;
        boolean showInvisible = false;
        boolean invisBarrier = true;
        boolean invisVoid = true;
        boolean invisLight = true;
        int invisRadius = 16;
        int invisParticle = 0;
        boolean showFrames = false;
        boolean frameSand = true;
        boolean frameGravel = true;
        boolean frameGlowstone = true;
        boolean framePowderSnow = true;
        int frameRadius = 12;
        boolean targetOutline = false;
        int targetOutlineColor = 0;
        boolean slotWheel = true;
        boolean slotDrag = true;
        boolean slotMatchAll = true;
        boolean modsButton = true;
        boolean wpShow = true;
        int wpNewColor = 0;
        boolean spawnKnown = false;
        int spawnX = 0;
        int spawnZ = 0;
        int spawnRadius = 2;
        boolean rulerShow = true;
        int rulerColor = 4;
        boolean rulerArrowsIn = false;
        boolean frameSolid = true;
        int frameSnowColor = 4;
        int frameSandColor = 2;
        int frameGravelColor = 1;
        int frameGlowColor = 7;
        int invisBarrierColor = 0;
        int invisVoidColor = 6;
        int invisLightColor = 3;
        boolean invisOutline = true;
        boolean invisMobs = true;
        boolean[] refillSlots = new boolean[10];
        boolean sortInvButtons = true;
        int sortSpeed = 10;
        int dropSpeed = 10;
        boolean zoom = true;
        boolean noHurtTilt = false;
        boolean swapSpearAuto = true;
        boolean swapMaceAuto = true;
        boolean swapElytraAuto = true;
        int swapWait = 3;
        boolean swapWaitCooldown = true;
        boolean swapSpearMode = false;
        boolean swapMaceMode = false;
        boolean swapSpearLunge = false;
        boolean swapReturn = true;
        boolean hitboxHighlight = true;
        boolean targetArmorHud = true;
        boolean effectsHud = true;
        boolean effectsTotems = true;
        int effectsPos = 0;
        boolean deathMark = true;
        boolean durWarn = true;
        int durWarnPercent = 10;
        boolean coordsHud = false;
        int coordsPos = 2;
        boolean itemCounter = true;
        boolean autoCrystal = false;
        int hitboxColor = 3;
        float zoomLevel = 4.0f;
        boolean zoomSmooth = true;
        boolean zoomSens = true;
        int searchRadius = 10;
        boolean armorHud = true;
        boolean armorShowArmor = true;
        boolean armorShowMain = true;
        boolean armorShowOff = true;
        boolean armorPercent = true;
        boolean armorShowMax = true;
        boolean armorColors = true;
        boolean armorVertical = true;
        boolean armorSeparate = false;
        int[] armorPosX = new int[6];
        int[] armorPosY = {300, 370, 440, 510, 580, 650};
        int armorX = 0;
        int armorY = 400;
    }

    // ---------- Профили настроек (копии файла настроек) ----------

    public static java.nio.file.Path profileFile(int slot) {
        return FILE.resolveSibling("universal_mod_profile_" + slot + ".json");
    }

    public static boolean profileExists(int slot) {
        return Files.exists(profileFile(slot));
    }

    /** Сохранить текущие настройки в профиль. */
    public static void saveProfile(int slot) {
        save();
        try {
            Files.copy(FILE, profileFile(slot), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Применить профиль; false, если такого профиля ещё нет. */
    public static boolean loadProfile(int slot) {
        if (!profileExists(slot)) return false;
        try {
            Files.copy(profileFile(slot), FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        load();
        return true;
    }

    public static void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(FILE)) {
            Data d = GSON.fromJson(r, Data.class);
            if (d == null) return;
            leftCps = clamp(d.leftCps);
            rightCps = clamp(d.rightCps);
            fastPlace = d.fastPlace;
            fastBreak = d.fastBreak;
            fastLeftClick = d.fastLeftClick;
            fastRightClick = d.fastRightClick;
            fastLeftCount = clampFast(d.fastLeftCount);
            fastRightCount = clampFast(d.fastRightCount);
            shulkerPreview = d.shulkerPreview;
            shulkerSum = d.shulkerSum;
            shulkerRenamedNote = d.shulkerRenamedNote;
            leftCooldown = d.leftCooldown;
            fullbright = d.fullbright;
            foodHud = d.foodHud;
            macrosEnabled = d.macrosEnabled;
            chatCopy = d.chatCopy;
            screenshotButtons = d.screenshotButtons;
            nickname = (d.nickname == null) ? "" : d.nickname;
            nicknames = new ArrayList<>();
            if (d.nicknames != null) {
                for (String n : d.nicknames) {
                    if (n != null && n.matches("[A-Za-z0-9_]{3,16}") && !nicknames.contains(n)) nicknames.add(n);
                }
            }
            // Ник из прошлой версии мода (хранился только один)
            if (nickname.matches("[A-Za-z0-9_]{3,16}") && !nicknames.contains(nickname)) nicknames.add(nickname);
            language = (d.language == null) ? "ru" : d.language;
            foodSaturation = d.foodSaturation;
            foodPreview = d.foodPreview;
            foodTooltip = d.foodTooltip;
            foodSatNumber = d.foodSatNumber;
            containerSearch = d.containerSearch;
            autoLeft = d.autoLeft;
            autoRight = d.autoRight;
            holdLeft = d.holdLeft;
            holdRight = d.holdRight;
            digEnabled = d.digEnabled;
            digLowPercent = d.digLowPercent <= 0 ? 10 : Math.max(1, Math.min(90, d.digLowPercent));
            digLowAction = Math.max(0, Math.min(2, d.digLowAction));
            digBreakAction = Math.max(0, Math.min(1, d.digBreakAction));
            searchQuery = d.searchQuery == null ? "" : d.searchQuery;
            sortButtons = d.sortButtons;
            refillHotbar = d.refillHotbar;
            refillOffhand = d.refillOffhand;
            if (d.refillSlots != null && d.refillSlots.length == 10) refillSlots = d.refillSlots.clone();
            showInvisible = d.showInvisible;
            invisBarrier = d.invisBarrier;
            invisVoid = d.invisVoid;
            invisLight = d.invisLight;
            showFrames = d.showFrames;
            frameSand = d.frameSand;
            frameGravel = d.frameGravel;
            frameGlowstone = d.frameGlowstone;
            framePowderSnow = d.framePowderSnow;
            frameRadius = d.frameRadius <= 0 ? 12 : Math.max(4, Math.min(32, d.frameRadius));
            targetOutline = d.targetOutline;
            slotWheel = d.slotWheel;
            slotDrag = d.slotDrag;
            slotMatchAll = d.slotMatchAll;
            modsButton = d.modsButton;
            wpShow = d.wpShow;
            wpNewColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.wpNewColor));
            spawnKnown = d.spawnKnown;
            spawnX = d.spawnX;
            spawnZ = d.spawnZ;
            spawnRadius = Math.max(0, Math.min(10, d.spawnRadius));
            rulerShow = d.rulerShow;
            rulerColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.rulerColor));
            rulerArrowsIn = d.rulerArrowsIn;
            frameSolid = d.frameSolid;
            frameSnowColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.frameSnowColor));
            frameSandColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.frameSandColor));
            frameGravelColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.frameGravelColor));
            frameGlowColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.frameGlowColor));
            invisBarrierColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.invisBarrierColor));
            invisVoidColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.invisVoidColor));
            invisLightColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.invisLightColor));
            invisOutline = d.invisOutline;
            invisMobs = d.invisMobs;
            targetOutlineColor = Math.max(0, Math.min(3, d.targetOutlineColor));
            invisParticle = Math.max(0, Math.min(InvisibleParticles.NAMES.length - 1, d.invisParticle));
            invisRadius = d.invisRadius <= 0 ? 16 : Math.max(4, Math.min(32, d.invisRadius));
            refillMin = d.refillMin <= 0 ? 1 : Math.max(1, Math.min(32, d.refillMin));
            sortInvButtons = d.sortInvButtons;
            sortSpeed = d.sortSpeed <= 0 ? 10 : Math.max(1, Math.min(20, d.sortSpeed));
            dropSpeed = d.dropSpeed <= 0 ? 10 : Math.max(1, Math.min(20, d.dropSpeed));
            zoom = d.zoom;
            noHurtTilt = d.noHurtTilt;
            swapSpearAuto = d.swapSpearAuto;
            swapMaceAuto = d.swapMaceAuto;
            swapElytraAuto = d.swapElytraAuto;
            swapWait = d.swapWait <= 0 ? 3 : Math.max(HotbarSwap.MIN_WAIT, Math.min(HotbarSwap.MAX_WAIT, d.swapWait));
            swapWaitCooldown = d.swapWaitCooldown;
            swapSpearMode = d.swapSpearMode;
            swapMaceMode = d.swapMaceMode;
            swapSpearLunge = d.swapSpearLunge;
            swapReturn = d.swapReturn;
            hitboxHighlight = d.hitboxHighlight;
            targetArmorHud = d.targetArmorHud;
            effectsHud = d.effectsHud;
            effectsTotems = d.effectsTotems;
            effectsPos = Math.max(0, Math.min(3, d.effectsPos));
            deathMark = d.deathMark;
            durWarn = d.durWarn;
            durWarnPercent = d.durWarnPercent <= 0 ? 10 : Math.max(1, Math.min(50, d.durWarnPercent));
            coordsHud = d.coordsHud;
            coordsPos = Math.max(0, Math.min(3, d.coordsPos));
            itemCounter = d.itemCounter;
            autoCrystal = d.autoCrystal;
            hitboxColor = Math.max(0, Math.min(Waypoints.COLORS.length - 1, d.hitboxColor));
            zoomLevel = d.zoomLevel < 1.2f ? 4.0f : Math.min(30f, d.zoomLevel);
            zoomSmooth = d.zoomSmooth;
            zoomSens = d.zoomSens;
            searchRadius = d.searchRadius <= 0 ? 10 : Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, d.searchRadius));
            armorHud = d.armorHud;
            armorShowArmor = d.armorShowArmor;
            armorShowMain = d.armorShowMain;
            armorShowOff = d.armorShowOff;
            armorPercent = d.armorPercent;
            armorShowMax = d.armorShowMax;
            armorColors = d.armorColors;
            armorVertical = d.armorVertical;
            armorSeparate = d.armorSeparate;
            if (d.armorPosX != null && d.armorPosX.length == 6 && d.armorPosY != null && d.armorPosY.length == 6) {
                for (int i = 0; i < 6; i++) {
                    armorPosX[i] = Math.max(0, Math.min(1000, d.armorPosX[i]));
                    armorPosY[i] = Math.max(0, Math.min(1000, d.armorPosY[i]));
                }
            }
            armorX = d.armorX;
            armorY = d.armorY;
            armorX = Math.max(0, Math.min(1000, armorX));
            armorY = Math.max(0, Math.min(1000, armorY));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        Data d = new Data();
        d.leftCps = leftCps;
        d.rightCps = rightCps;
        d.fastPlace = fastPlace;
        d.fastBreak = fastBreak;
        d.fastLeftClick = fastLeftClick;
        d.fastRightClick = fastRightClick;
        d.fastLeftCount = fastLeftCount;
        d.fastRightCount = fastRightCount;
        d.shulkerPreview = shulkerPreview;
        d.shulkerSum = shulkerSum;
        d.shulkerRenamedNote = shulkerRenamedNote;
        d.leftCooldown = leftCooldown;
        d.fullbright = fullbright;
        d.foodHud = foodHud;
        d.macrosEnabled = macrosEnabled;
        d.chatCopy = chatCopy;
        d.screenshotButtons = screenshotButtons;
        d.nickname = nickname;
        d.nicknames = new ArrayList<>(nicknames);
        d.language = language;
        d.foodSaturation = foodSaturation;
        d.foodPreview = foodPreview;
        d.foodTooltip = foodTooltip;
        d.foodSatNumber = foodSatNumber;
        d.containerSearch = containerSearch;
        d.autoLeft = autoLeft;
        d.autoRight = autoRight;
        d.holdLeft = holdLeft;
        d.holdRight = holdRight;
        d.digEnabled = digEnabled;
        d.digLowPercent = digLowPercent;
        d.digLowAction = digLowAction;
        d.digBreakAction = digBreakAction;
        d.searchQuery = searchQuery;
        d.sortButtons = sortButtons;
        d.refillHotbar = refillHotbar;
        d.refillOffhand = refillOffhand;
        d.refillMin = refillMin;
        d.showInvisible = showInvisible;
        d.invisBarrier = invisBarrier;
        d.invisVoid = invisVoid;
        d.invisLight = invisLight;
        d.invisRadius = invisRadius;
        d.invisParticle = invisParticle;
        d.showFrames = showFrames;
        d.frameSand = frameSand;
        d.frameGravel = frameGravel;
        d.frameGlowstone = frameGlowstone;
        d.framePowderSnow = framePowderSnow;
        d.frameRadius = frameRadius;
        d.targetOutline = targetOutline;
        d.slotWheel = slotWheel;
        d.slotDrag = slotDrag;
        d.slotMatchAll = slotMatchAll;
        d.modsButton = modsButton;
        d.wpShow = wpShow;
        d.wpNewColor = wpNewColor;
        d.spawnKnown = spawnKnown;
        d.spawnX = spawnX;
        d.spawnZ = spawnZ;
        d.spawnRadius = spawnRadius;
        d.rulerShow = rulerShow;
        d.rulerColor = rulerColor;
        d.rulerArrowsIn = rulerArrowsIn;
        d.frameSolid = frameSolid;
        d.frameSnowColor = frameSnowColor;
        d.frameSandColor = frameSandColor;
        d.frameGravelColor = frameGravelColor;
        d.frameGlowColor = frameGlowColor;
        d.invisBarrierColor = invisBarrierColor;
        d.invisVoidColor = invisVoidColor;
        d.invisLightColor = invisLightColor;
        d.invisOutline = invisOutline;
        d.invisMobs = invisMobs;
        d.targetOutlineColor = targetOutlineColor;
        d.refillSlots = refillSlots.clone();
        d.sortInvButtons = sortInvButtons;
        d.sortSpeed = sortSpeed;
        d.dropSpeed = dropSpeed;
        d.zoom = zoom;
        d.noHurtTilt = noHurtTilt;
        d.swapSpearAuto = swapSpearAuto;
        d.swapMaceAuto = swapMaceAuto;
        d.swapElytraAuto = swapElytraAuto;
        d.swapWait = swapWait;
        d.swapWaitCooldown = swapWaitCooldown;
        d.swapSpearMode = swapSpearMode;
        d.swapMaceMode = swapMaceMode;
        d.swapSpearLunge = swapSpearLunge;
        d.swapReturn = swapReturn;
        d.hitboxHighlight = hitboxHighlight;
        d.targetArmorHud = targetArmorHud;
        d.effectsHud = effectsHud;
        d.effectsTotems = effectsTotems;
        d.effectsPos = effectsPos;
        d.deathMark = deathMark;
        d.durWarn = durWarn;
        d.durWarnPercent = durWarnPercent;
        d.coordsHud = coordsHud;
        d.coordsPos = coordsPos;
        d.itemCounter = itemCounter;
        d.autoCrystal = autoCrystal;
        d.hitboxColor = hitboxColor;
        d.zoomLevel = zoomLevel;
        d.zoomSmooth = zoomSmooth;
        d.zoomSens = zoomSens;
        d.searchRadius = searchRadius;
        d.armorHud = armorHud;
        d.armorShowArmor = armorShowArmor;
        d.armorShowMain = armorShowMain;
        d.armorShowOff = armorShowOff;
        d.armorPercent = armorPercent;
        d.armorShowMax = armorShowMax;
        d.armorColors = armorColors;
        d.armorVertical = armorVertical;
        d.armorSeparate = armorSeparate;
        d.armorPosX = armorPosX.clone();
        d.armorPosY = armorPosY.clone();
        d.armorX = armorX;
        d.armorY = armorY;
        try (Writer w = Files.newBufferedWriter(FILE)) {
            GSON.toJson(d, w);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int clamp(int v) {
        return Math.max(MIN_CPS, Math.min(MAX_CPS, v));
    }

    private static int clampFast(int v) {
        if (v == 0) return 4; // старый конфиг без этого поля
        return Math.max(MIN_FAST, Math.min(MAX_FAST, v));
    }
}
