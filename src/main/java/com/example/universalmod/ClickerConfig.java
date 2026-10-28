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
    public static int targetOutline = 0;
    /** Автозаполнение по слотам: 0..8 — хотбар, 9 — вторая рука. По умолчанию везде выключено. */
    public static boolean[] refillSlots = new boolean[10];
    public static boolean sortInvButtons = true;
    public static int sortSpeed = 10;
    public static boolean zoom = true;
    public static boolean noHurtTilt = false;
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
        int targetOutline = 0;
        boolean[] refillSlots = new boolean[10];
        boolean sortInvButtons = true;
        int sortSpeed = 10;
        boolean zoom = true;
        boolean noHurtTilt = false;
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
            targetOutline = Math.max(0, Math.min(TargetOutline.NAMES.length - 1, d.targetOutline));
            frameSand = d.frameSand;
            frameGravel = d.frameGravel;
            frameGlowstone = d.frameGlowstone;
            framePowderSnow = d.framePowderSnow;
            frameRadius = d.frameRadius <= 0 ? 12 : Math.max(4, Math.min(32, d.frameRadius));
            invisParticle = Math.max(0, Math.min(InvisibleParticles.NAMES.length - 1, d.invisParticle));
            invisRadius = d.invisRadius <= 0 ? 16 : Math.max(4, Math.min(32, d.invisRadius));
            refillMin = d.refillMin <= 0 ? 1 : Math.max(1, Math.min(32, d.refillMin));
            sortInvButtons = d.sortInvButtons;
            sortSpeed = d.sortSpeed <= 0 ? 10 : Math.max(1, Math.min(20, d.sortSpeed));
            zoom = d.zoom;
            noHurtTilt = d.noHurtTilt;
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
        d.targetOutline = targetOutline;
        d.frameSand = frameSand;
        d.frameGravel = frameGravel;
        d.frameGlowstone = frameGlowstone;
        d.framePowderSnow = framePowderSnow;
        d.frameRadius = frameRadius;
        d.refillSlots = refillSlots.clone();
        d.sortInvButtons = sortInvButtons;
        d.sortSpeed = sortSpeed;
        d.zoom = zoom;
        d.noHurtTilt = noHurtTilt;
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
