package com.example.universalmod;

import com.example.universalmod.mixin.InteractionManagerAccessor;
import com.example.universalmod.mixin.MinecraftClientAccessor;
import com.example.universalmod.mixin.SimpleOptionAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class UniversalMod implements ClientModInitializer {
    public static final String MOD_ID = "universal_mod";

    private static KeyBinding toggleLeftKey;
    private static KeyBinding toggleRightKey;
    private static KeyBinding holdLeftKey;
    private static KeyBinding holdRightKey;
    private static KeyBinding settingsKey;
    private static KeyBinding fastPlaceKey;
    private static KeyBinding fastBreakKey;
    private static KeyBinding fastLeftClickKey;
    private static KeyBinding fastRightClickKey;
    private static KeyBinding cooldownKey;
    private static KeyBinding shulkerPreviewKey;
    private static KeyBinding shulkerModeKey;
    private static KeyBinding fullbrightKey;
    private static KeyBinding foodHudKey;
    private static KeyBinding armorHudKey;
    private static KeyBinding macrosKey;
    private static KeyBinding freelookKey;
    private static KeyBinding searchKey;
    private static KeyBinding zoomKey;
    private static KeyBinding refillHotbarKey, refillOffhandKey, invisibleKey, framesKey, targetOutlineKey, modListKey, wpAddKey, wpMenuKey, rulerKey, rulerClearKey;
    private static KeyBinding sortContH, sortContV, sortInvH, sortInvV;
    private static KeyBinding swapSpearKey, swapMaceKey, swapElytraKey, hitboxKey, crystalKey, trapKey;
    private static boolean zoomWasHeld = false;
    private static Perspective prevPerspective = Perspective.FIRST_PERSON;

    // Автоклик по CPS
    private static boolean leftOn = false;
    private static boolean rightOn = false;
    // Зажать кнопку
    private static boolean leftHold = false;
    private static boolean rightHold = false;
    private static double leftAcc = 0;
    private static double rightAcc = 0;

    private static KeyBinding[] allKeys;
    private static final String[] KEY_NAMES = {
            "Автокликер ЛКМ", "Автокликер ПКМ", "Зажать ЛКМ", "Зажать ПКМ",
            "Быстрый ЛКМ", "Быстрый ПКМ", "Быстрая установка", "Быстрое разрушение",
            "ЛКМ по кулдауну", "Просмотр шалкера", "Шалкер: режим", "Фулбрайт", "Открыть настройки", "Показы еды", "Показ прочности", "Макросы", "Осмотр камеры (зажать)", "Поиск в контейнерах", "Зум (зажать)",
            "Сорт. контейнера: по горизонтали", "Сорт. контейнера: по вертикали", "Сорт. инвентаря: по горизонтали", "Сорт. инвентаря: по вертикали",
            "Автозаполнение хотбара", "Автозаполнение второй руки", "Невидимые блоки", "Рамки блоков"
    };

    public static KeyBinding[] keys() { return allKeys; }
    public static String[] keyNames() {
        String[] r = new String[KEY_NAMES.length];
        for (int i = 0; i < r.length; i++) r[i] = I18n.t(KEY_NAMES[i]);
        return r;
    }

    public static boolean isLeftOn() { return leftOn; }
    public static boolean isRightOn() { return rightOn; }
    public static boolean isLeftHold() { return leftHold; }
    public static boolean isRightHold() { return rightHold; }

    public static void setLeftOn(boolean on) { leftOn = on; leftAcc = 0; if (!on) release(true); ClickerConfig.autoLeft = on; ClickerConfig.save(); }
    public static void setRightOn(boolean on) { rightOn = on; rightAcc = 0; if (!on) release(false); ClickerConfig.autoRight = on; ClickerConfig.save(); }
    public static void setLeftHold(boolean on) { leftHold = on; if (!on) release(true); ClickerConfig.holdLeft = on; ClickerConfig.save(); }
    public static void setRightHold(boolean on) { rightHold = on; if (!on) release(false); ClickerConfig.holdRight = on; ClickerConfig.save(); }

    private static void release(boolean left) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) return;
        // Отпускаем кнопку, только если её не держит ни автоклик, ни "зажать"
        if (left && !leftOn && !leftHold) client.options.attackKey.setPressed(false);
        if (!left && !rightOn && !rightHold) client.options.useKey.setPressed(false);
    }

    @Override
    public void onInitializeClient() {
        ClickerConfig.load();
        MacroConfig.load();
        Waypoints.load();
        // Состояние автокликера и «зажать» помним между запусками и заходами в мир
        leftOn = ClickerConfig.autoLeft;
        rightOn = ClickerConfig.autoRight;
        leftHold = ClickerConfig.holdLeft;
        rightHold = ClickerConfig.holdRight;

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));

        // По умолчанию все клавиши не назначены — назначаются в Управлении
        toggleLeftKey = register("key.universal_mod.left", category);
        toggleRightKey = register("key.universal_mod.right", category);
        holdLeftKey = register("key.universal_mod.holdleft", category);
        holdRightKey = register("key.universal_mod.holdright", category);
        settingsKey = register("key.universal_mod.settings", category);
        fastPlaceKey = register("key.universal_mod.fastplace", category);
        fastBreakKey = register("key.universal_mod.fastbreak", category);
        fastLeftClickKey = register("key.universal_mod.fastleftclick", category);
        fastRightClickKey = register("key.universal_mod.fastrightclick", category);
        cooldownKey = register("key.universal_mod.cooldown", category);
        shulkerPreviewKey = register("key.universal_mod.shulkerpreview", category);
        shulkerModeKey = register("key.universal_mod.shulkermode", category);
        fullbrightKey = register("key.universal_mod.fullbright", category);
        foodHudKey = register("key.universal_mod.foodhud", category);
        armorHudKey = register("key.universal_mod.armorhud", category);
        macrosKey = register("key.universal_mod.macros", category);
        freelookKey = register("key.universal_mod.freelook", category);
        searchKey = register("key.universal_mod.search", category);
        zoomKey = register("key.universal_mod.zoom", category);
        sortContH = register("key.universal_mod.sortch", category);
        sortContV = register("key.universal_mod.sortcv", category);
        sortInvH = register("key.universal_mod.sortih", category);
        sortInvV = register("key.universal_mod.sortiv", category);
        refillHotbarKey = register("key.universal_mod.refillhotbar", category);
        refillOffhandKey = register("key.universal_mod.refilloffhand", category);
        invisibleKey = register("key.universal_mod.invisible", category);
        framesKey = register("key.universal_mod.frames", category);
        targetOutlineKey = register("key.universal_mod.targetoutline", category);
        modListKey = register("key.universal_mod.modlist", category);
        wpAddKey = register("key.universal_mod.wpadd", category);
        wpMenuKey = register("key.universal_mod.wpmenu", category);
        rulerKey = register("key.universal_mod.ruler", category);
        rulerClearKey = register("key.universal_mod.rulerclear", category);
        swapSpearKey = register("key.universal_mod.swapspear", category);
        swapMaceKey = register("key.universal_mod.swapmace", category);
        swapElytraKey = register("key.universal_mod.swapelytra", category);
        hitboxKey = register("key.universal_mod.hitbox", category);
        crystalKey = register("key.universal_mod.autocrystal", category);
        trapKey = register("key.universal_mod.trapuse", category);

        allKeys = new KeyBinding[]{
                toggleLeftKey, toggleRightKey, holdLeftKey, holdRightKey,
                fastLeftClickKey, fastRightClickKey, fastPlaceKey, fastBreakKey,
                cooldownKey, shulkerPreviewKey, shulkerModeKey, fullbrightKey, settingsKey,
                foodHudKey, armorHudKey, macrosKey, freelookKey, searchKey, zoomKey,
                sortContH, sortContV, sortInvH, sortInvV,
                refillHotbarKey, refillOffhandKey, invisibleKey, framesKey, targetOutlineKey, modListKey, wpAddKey, wpMenuKey, rulerKey, rulerClearKey,
                swapSpearKey, swapMaceKey, swapElytraKey, hitboxKey, crystalKey, trapKey
        };

        WorldOutlines.register();
        AutoCrystal.register();
        Ruler.register();
        Waypoints.register();
        ModListScreen.register();
        ShulkerTooltip.register();
        FoodHud.register();
        ArmorHud.register();
        TargetArmorHud.register();
        EffectsHud.register();
        DeathMark.register();
        DurabilityWarn.register();
        CoordsHud.register();
        ItemCounter.register();
        TrapUse.register();
        ContainerSearch.register();
        Zoom.register();
        InventorySort.register();
        MacroManager.register();
        AutoRefill.registerHud();

        ClientTickEvents.START_CLIENT_TICK.register(UniversalMod::onTick);

        // Применяем сохранённый ник при запуске игры
        ClientLifecycleEvents.CLIENT_STARTED.register(c -> {
            if (NicknameScreen.valid(ClickerConfig.nickname)) NicknameScreen.apply(ClickerConfig.nickname);
        });
        // Возвращаем обычную яркость при выходе, чтобы 1600% не попало в options.txt
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> {
            restoreGamma(c);
            restoreTilt(c);
            Zoom.restore(c);
        });

        // Кнопка настроек в меню паузы (слева, под кнопками других модов)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Кнопка смены ника: в главном меню — над самой верхней кнопкой, в списке серверов — слева сверху
            if (screen instanceof TitleScreen) {
                ClickableWidget top = null;
                for (ClickableWidget w : Screens.getButtons(screen)) {
                    if (w.getWidth() >= 150 && (top == null || w.getY() < top.getY())) top = w;
                }
                int bx = top != null ? top.getX() : scaledWidth / 2 - 100;
                int bw = top != null ? top.getWidth() : 200;
                int by = top != null ? top.getY() - 24 : 20;
                Screens.getButtons(screen).add(
                        ButtonWidget.builder(Text.literal(I18n.t("Ник: ") + NicknameScreen.current()),
                                        b -> client.setScreen(new NicknameScreen(screen)))
                                .dimensions(bx, by, bw, 20)
                                .build());
            } else if (screen instanceof MultiplayerScreen) {
                Screens.getButtons(screen).add(
                        ButtonWidget.builder(Text.literal(I18n.t("Ник: ") + NicknameScreen.current()),
                                        b -> client.setScreen(new NicknameScreen(screen)))
                                .dimensions(6, 6, 110, 20)
                                .build());
            }
            if (screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(
                        ButtonWidget.builder(Text.literal("Universal Mod"),
                                        b -> client.setScreen(new ClickerScreen(screen)))
                                .dimensions(6, 60, 90, 20)
                                .build());
            }
        });
    }

    private static KeyBinding register(String name, KeyBinding.Category category) {
        return register(name, category, GLFW.GLFW_KEY_UNKNOWN);
    }

    private static KeyBinding register(String name, KeyBinding.Category category, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(
                new KeyBinding(name, InputUtil.Type.KEYSYM, defaultKey, category));
    }

    /** Осмотр: пока зажата клавиша — вид от третьего лица, мышь крутит камеру вокруг игрока. */
    private static void updateFreelook(MinecraftClient client) {
        boolean want = freelookKey.isPressed() && client.currentScreen == null;
        if (want && !FreeLook.active) {
            FreeLook.active = true;
            FreeLook.camYaw = client.player.getYaw();
            FreeLook.camPitch = client.player.getPitch();
            prevPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        } else if (!want && FreeLook.active) {
            FreeLook.active = false;
            client.options.setPerspective(prevPerspective);
        }
    }

    private static void msg(MinecraftClient client, String name, boolean on) {
        client.player.sendMessage(Text.literal(name + ": " + (on ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ"))), true);
    }

    private static final double FULLBRIGHT_GAMMA = 16.0;
    private static double originalGamma = 0.5;

    public static void toggleFullbright() {
        ClickerConfig.fullbright = !ClickerConfig.fullbright;
        ClickerConfig.save();
    }

    private static void restoreGamma(MinecraftClient client) {
        if (client == null || client.options == null) return;
        if (client.options.getGamma().getValue() > 1.0) {
            setGamma(client, originalGamma);
        }
    }

    @SuppressWarnings("unchecked")
    private static void setGamma(MinecraftClient client, double v) {
        ((SimpleOptionAccessor<Double>) (Object) client.options.getGamma()).setRawValue(v);
    }

    private static void applyFullbright(MinecraftClient client) {
        if (client.options == null) return;
        double cur = client.options.getGamma().getValue();
        if (ClickerConfig.fullbright) {
            if (cur != FULLBRIGHT_GAMMA) {
                if (cur <= 1.0) originalGamma = cur;
                setGamma(client, FULLBRIGHT_GAMMA);
            }
        } else if (cur > 1.0) {
            setGamma(client, originalGamma);
        }
    }

    // Наклон экрана при уроне: на время работы функции обнуляем ванильную настройку «Наклон при уроне»
    private static double originalTilt = 1.0;
    private static boolean tiltOverridden = false;

    @SuppressWarnings("unchecked")
    private static void applyNoHurtTilt(MinecraftClient client) {
        if (client.options == null) return;
        SimpleOption<Double> opt = client.options.getDamageTiltStrength();
        double cur = opt.getValue();
        if (ClickerConfig.noHurtTilt) {
            if (!tiltOverridden) {
                originalTilt = cur;
                tiltOverridden = true;
            }
            if (cur != 0.0) ((SimpleOptionAccessor<Double>) (Object) opt).setRawValue(0.0);
        } else if (tiltOverridden) {
            ((SimpleOptionAccessor<Double>) (Object) opt).setRawValue(originalTilt);
            tiltOverridden = false;
        }
    }

    private static void restoreTilt(MinecraftClient client) {
        if (client == null || client.options == null || !tiltOverridden) return;
        ((SimpleOptionAccessor<Double>) (Object) client.options.getDamageTiltStrength()).setRawValue(originalTilt);
        tiltOverridden = false;
    }

    private static void onTick(MinecraftClient client) {
        applyFullbright(client);
        applyNoHurtTilt(client);

        if (client.player == null) {
            FreeLook.active = false;
            Zoom.held = false;
            MacroManager.reset();
            AutoRefill.reset();
            HotbarSwap.reset();
            AutoDig.reset();
            AutoCrystal.reset();
            InvisibleBlocks.reset();
            BlockFrames.reset();
            return;
        }

        MacroManager.tick(client);
        InventorySort.tick(client);
        AutoRefill.tick(client);
        HotbarSwap.tick(client);
        AutoDig.tick(client);
        AutoCrystal.tick(client);
        InvisibleBlocks.tick(client);
        BlockFrames.tick(client);
        updateFreelook(client);
        Zoom.held = ClickerConfig.zoom && zoomKey.isPressed() && client.currentScreen == null;
        if (zoomWasHeld && !Zoom.held && Zoom.dirty) {
            Zoom.dirty = false;
            ClickerConfig.save();
        }
        zoomWasHeld = Zoom.held;

        while (toggleLeftKey.wasPressed()) {
            setLeftOn(!leftOn);
            msg(client, I18n.t("Автокликер ЛКМ"), leftOn);
        }
        while (toggleRightKey.wasPressed()) {
            setRightOn(!rightOn);
            msg(client, I18n.t("Автокликер ПКМ"), rightOn);
        }
        while (holdLeftKey.wasPressed()) {
            setLeftHold(!leftHold);
            msg(client, I18n.t("Зажать ЛКМ"), leftHold);
        }
        while (holdRightKey.wasPressed()) {
            setRightHold(!rightHold);
            msg(client, I18n.t("Зажать ПКМ"), rightHold);
        }
        while (fastPlaceKey.wasPressed()) {
            ClickerConfig.fastPlace = !ClickerConfig.fastPlace;
            ClickerConfig.save();
            msg(client, I18n.t("Быстрая установка блоков"), ClickerConfig.fastPlace);
        }
        while (fastBreakKey.wasPressed()) {
            ClickerConfig.fastBreak = !ClickerConfig.fastBreak;
            ClickerConfig.save();
            msg(client, I18n.t("Быстрое разрушение блоков"), ClickerConfig.fastBreak);
        }
        while (fastLeftClickKey.wasPressed()) {
            ClickerConfig.fastLeftClick = !ClickerConfig.fastLeftClick;
            ClickerConfig.save();
            msg(client, I18n.t("Быстрый ЛКМ"), ClickerConfig.fastLeftClick);
        }
        while (fastRightClickKey.wasPressed()) {
            ClickerConfig.fastRightClick = !ClickerConfig.fastRightClick;
            ClickerConfig.save();
            msg(client, I18n.t("Быстрый ПКМ"), ClickerConfig.fastRightClick);
        }
        while (cooldownKey.wasPressed()) {
            ClickerConfig.leftCooldown = !ClickerConfig.leftCooldown;
            ClickerConfig.save();
            msg(client, I18n.t("ЛКМ по кулдауну оружия"), ClickerConfig.leftCooldown);
        }
        while (shulkerPreviewKey.wasPressed()) {
            ClickerConfig.shulkerPreview = !ClickerConfig.shulkerPreview;
            ClickerConfig.save();
            msg(client, I18n.t("Просмотр шалкера"), ClickerConfig.shulkerPreview);
        }
        while (shulkerModeKey.wasPressed()) {
            ClickerConfig.shulkerSum = !ClickerConfig.shulkerSum;
            ClickerConfig.save();
            client.player.sendMessage(Text.literal(I18n.t("Шалкер: ")
                    + (ClickerConfig.shulkerSum ? I18n.t("сумма по предметам") : I18n.t("по слотам"))), true);
        }
        while (fullbrightKey.wasPressed()) {
            toggleFullbright();
            msg(client, I18n.t("Фулбрайт"), ClickerConfig.fullbright);
        }
        while (foodHudKey.wasPressed()) {
            client.setScreen(new FoodHudScreen(client.currentScreen));
        }
        while (armorHudKey.wasPressed()) {
            client.setScreen(new ArmorHudScreen(client.currentScreen));
        }
        while (macrosKey.wasPressed()) {
            client.setScreen(new MacroScreen(client.currentScreen));
        }
        while (framesKey.wasPressed()) {
            ClickerConfig.showFrames = !ClickerConfig.showFrames;
            ClickerConfig.save();
            msg(client, I18n.t("Рамки блоков"), ClickerConfig.showFrames);
        }
        while (targetOutlineKey.wasPressed()) {
            ClickerConfig.targetOutline = !ClickerConfig.targetOutline;
            ClickerConfig.save();
            msg(client, I18n.t("Контур блока"), ClickerConfig.targetOutline);
        }
        while (invisibleKey.wasPressed()) {
            ClickerConfig.showInvisible = !ClickerConfig.showInvisible;
            ClickerConfig.save();
            msg(client, I18n.t("Невидимые блоки"), ClickerConfig.showInvisible);
        }
        while (refillHotbarKey.wasPressed()) {
            ClickerConfig.refillHotbar = !ClickerConfig.refillHotbar;
            ClickerConfig.save();
            msg(client, I18n.t("Автозаполнение хотбара"), ClickerConfig.refillHotbar);
        }
        while (refillOffhandKey.wasPressed()) {
            ClickerConfig.refillOffhand = !ClickerConfig.refillOffhand;
            ClickerConfig.save();
            msg(client, I18n.t("Автозаполнение второй руки"), ClickerConfig.refillOffhand);
        }
        while (crystalKey.wasPressed()) {
            ClickerConfig.autoCrystal = !ClickerConfig.autoCrystal;
            ClickerConfig.save();
            msg(client, I18n.t("Авто-кристалл"), ClickerConfig.autoCrystal);
        }
        while (trapKey.wasPressed()) {
            ClickerConfig.trapUse = !ClickerConfig.trapUse;
            ClickerConfig.save();
            msg(client, I18n.t("Жемчуг и хорус в ловушке"), ClickerConfig.trapUse);
        }
        while (hitboxKey.wasPressed()) {
            ClickerConfig.hitboxHighlight = !ClickerConfig.hitboxHighlight;
            ClickerConfig.save();
            msg(client, I18n.t("Подсветка хитбокса цели"), ClickerConfig.hitboxHighlight);
        }
        while (swapSpearKey.wasPressed()) {
            HotbarSwap.press(client, HotbarSwap.SPEAR);
        }
        while (swapMaceKey.wasPressed()) {
            HotbarSwap.press(client, HotbarSwap.MACE);
        }
        while (swapElytraKey.wasPressed()) {
            HotbarSwap.press(client, HotbarSwap.ELYTRA);
        }
        while (searchKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new ContainerSearchScreen(null));
            }
        }
        while (rulerKey.wasPressed()) {
            Ruler.press(client);
        }
        while (rulerClearKey.wasPressed()) {
            Ruler.clear(client);
        }
        while (wpAddKey.wasPressed()) {
            Waypoints.addHere(client);
        }
        while (wpMenuKey.wasPressed()) {
            client.setScreen(new WaypointScreen(client.currentScreen));
        }
        while (modListKey.wasPressed()) {
            client.setScreen(new ModListScreen(client.currentScreen));
        }
        while (settingsKey.wasPressed()) {
            if (client.currentScreen == null) {
                client.setScreen(new ClickerScreen(null));
            }
        }

        // Пока открыто любое меню — ничего не делаем
        if (client.currentScreen != null) {
            return;
        }

        // Зажать кнопку
        if (leftHold) client.options.attackKey.setPressed(true);
        if (rightHold) client.options.useKey.setPressed(true);

        // Быстрая установка: убираем задержку между установками блоков (в ваниле 4 тика)
        if (ClickerConfig.fastPlace && client.options.useKey.isPressed()) {
            ((MinecraftClientAccessor) client).setItemUseCooldown(0);
        }
        // Быстрое разрушение: убираем задержку между блоками (в ваниле 5 тиков)
        if (ClickerConfig.fastBreak && client.interactionManager != null
                && client.options.attackKey.isPressed()) {
            ((InteractionManagerAccessor) client.interactionManager).setBlockBreakingCooldown(0);
        }

        // Быстрый ПКМ / ЛКМ: пока кнопка зажата, делаем несколько кликов за тик
        if (client.interactionManager != null && !client.player.isUsingItem()) {
            MinecraftClientAccessor acc = (MinecraftClientAccessor) client;
            boolean waterBucket = ClickerConfig.waterFast
                    && (client.player.getMainHandStack().isOf(net.minecraft.item.Items.WATER_BUCKET)
                    || client.player.getOffHandStack().isOf(net.minecraft.item.Items.WATER_BUCKET));
            if ((ClickerConfig.fastRightClick || waterBucket) && client.options.useKey.isPressed()) {
                for (int i = 1; i < ClickerConfig.fastRightCount; i++) {
                    acc.setItemUseCooldown(0);
                    acc.invokeDoItemUse();
                }
                acc.setItemUseCooldown(0);
            }
            if (ClickerConfig.fastLeftClick && client.options.attackKey.isPressed()) {
                for (int i = 1; i < ClickerConfig.fastLeftCount; i++) {
                    acc.invokeDoAttack();
                }
            }
        }

        // Автоклик по CPS
        if (leftOn) {
            if (ClickerConfig.leftCooldown) {
                // Бьём, как только оружие перезарядилось: по мобу, по блоку или в воздух — без разницы
                KeyBinding key = client.options.attackKey;
                net.minecraft.util.hit.HitResult h = client.crosshairTarget;
                boolean ready = client.player.getAttackCooldownProgress(0.0f) >= 1.0f;
                if (client.currentScreen != null) {
                    if (!leftHold) key.setPressed(false);
                } else if (h instanceof net.minecraft.util.hit.EntityHitResult ehr && ehr.getEntity().isAttackable()) {
                    if (!leftHold) key.setPressed(false);
                    if (ready && !client.player.isUsingItem()) {
                        ((MinecraftClientAccessor) client).invokeDoAttack();
                    }
                } else if (h != null && h.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
                    key.setPressed(true);   // блок: копаем, кнопка зажата
                } else if (ready) {
                    // воздух: обычный клик, когда оружие готово
                    KeyBinding.onKeyPressed(InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey()));
                    key.setPressed(true);
                } else if (!leftHold) {
                    key.setPressed(false);
                }
            } else {
                leftAcc = autoClickLeft(client, ClickerConfig.leftCps, leftAcc, leftHold);
            }
        }
        if (rightOn) {
            rightAcc = autoClick(client.options.useKey, ClickerConfig.rightCps, rightAcc, rightHold);
        }
    }

    /** Автоклик ЛКМ: по мобу бьёт напрямую, иначе обычный клик (блок, воздух). */
    private static double autoClickLeft(MinecraftClient client, int cps, double acc, boolean alsoHeld) {
        KeyBinding key = client.options.attackKey;
        if (client.currentScreen != null) {
            if (!alsoHeld) key.setPressed(false);
            return acc;
        }
        net.minecraft.util.hit.HitResult h = client.crosshairTarget;
        if (h instanceof net.minecraft.util.hit.EntityHitResult ehr && ehr.getEntity().isAttackable()
                && !client.player.isUsingItem()) {
            acc += cps / 20.0;
            int clicks = (int) acc;
            acc -= clicks;
            for (int i = 0; i < clicks; i++) {
                ((MinecraftClientAccessor) client).invokeDoAttack();
            }
            if (!alsoHeld) key.setPressed(false);
            return acc;
        }
        return autoClick(key, cps, acc, alsoHeld);
    }

    /** Возвращает обновлённый аккумулятор. */
    private static double autoClick(KeyBinding key, int cps, double acc, boolean alsoHeld) {
        // Игра тикает 20 раз в секунду, накапливаем дробные клики
        acc += cps / 20.0;
        int clicks = (int) acc;
        acc -= clicks;

        if (clicks > 0) {
            InputUtil.Key bound = InputUtil.fromTranslationKey(key.getBoundKeyTranslationKey());
            for (int i = 0; i < clicks; i++) {
                KeyBinding.onKeyPressed(bound);
            }
            key.setPressed(true);
        } else if (!alsoHeld) {
            key.setPressed(false);
        }
        return acc;
    }
}
