package com.example.universalmod;

import com.example.universalmod.mixin.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Смена предмета в хотбаре: копьё, булава, элитры.
 *
 * Обычный режим: клавиша находит предмет в хотбаре и переключает на его слот. Дальше (если включено авто-действие):
 *  - элитры надеваются сразу (правый клик);
 *  - копьё / булава: удар ЛКМ сразу, если прицел на цели, иначе ждём цель до заданного времени (1–10 с), один удар.
 *
 * Режим свапа (копьё и булава): клавиша слот НЕ переключает. Мод запоминает, что предмет есть в хотбаре,
 * и запускает ожидание. Как только прицел оказывается на цели, в одном тике берём предмет и бьём, в следующем тике возвращаем прошлый слот.
 * У копья есть ещё свап без цели: то же самое, но цель не нужна (джеб для рывка Lunge).
 *
 * Почему это работает: перезарядка при смене предмета сбрасывается не сразу, а в следующем тике игрока.
 * Поэтому удар в тот же тик, что и смена слота, считается по зарядке прошлого предмета, а урон/эффекты берутся
 * у нового (attribute swap, MC-28289).
 */
public class HotbarSwap {
    public static final int SPEAR = 0, MACE = 1, ELYTRA = 2;
    public static final int MIN_WAIT = 1, MAX_WAIT = 10;

    /** Запас времени на перезарядку оружия (2 с), если включено «ждать перезарядку оружия». */
    private static final int COOLDOWN_EXTRA_TICKS = 40;

    private static int pendingKind = -1;   // -1: ничего не ждём
    private static int pendingSlot = -1;   // обычный режим: слот, на который переключились
    private static boolean swapMode = false;
    private static boolean needTarget = true;
    private static int ticksLeft = 0;
    private static int returnSlot = -1;    // слот, на который вернёмся в СЛЕДУЮЩЕМ тике после свапа

    public static void reset() {
        pendingKind = -1;
    }

    /** Сразу выполняет отложенный возврат слота (если он ещё не выполнен). */
    private static void flushReturn(MinecraftClient client) {
        if (returnSlot >= 0 && client.player != null) {
            client.player.getInventory().setSelectedSlot(returnSlot);
        }
        returnSlot = -1;
    }

    private static boolean matches(int kind, ItemStack s) {
        if (s.isEmpty()) return false;
        return switch (kind) {
            case SPEAR -> Registries.ITEM.getId(s.getItem()).getPath().endsWith("_spear");
            case MACE -> s.isOf(Items.MACE);
            // Сломанные элитры (остался 1 прочности) надеть нельзя
            case ELYTRA -> s.isOf(Items.ELYTRA) && s.getDamage() < s.getMaxDamage() - 1;
            default -> false;
        };
    }

    private static int findSlot(MinecraftClient client, int kind) {
        PlayerInventory inv = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (matches(kind, inv.getStack(i))) return i;
        }
        return -1;
    }

    private static boolean autoAction(int kind) {
        return switch (kind) {
            case SPEAR -> ClickerConfig.swapSpearAuto;
            case MACE -> ClickerConfig.swapMaceAuto;
            default -> ClickerConfig.swapElytraAuto;
        };
    }

    private static String name(int kind) {
        return switch (kind) {
            case SPEAR -> I18n.t("Копьё");
            case MACE -> I18n.t("Булава");
            default -> I18n.t("Элитры");
        };
    }

    private static int windowTicks() {
        return ClickerConfig.swapWait * 20 + (ClickerConfig.swapWaitCooldown ? COOLDOWN_EXTRA_TICKS : 0);
    }

    /** Нажата клавиша выбранного предмета. */
    public static void press(MinecraftClient client, int kind) {
        flushReturn(client);
        reset();
        if (client.player == null || client.interactionManager == null || client.currentScreen != null) return;
        if (client.player.isSpectator()) return;

        PlayerInventory inv = client.player.getInventory();
        int slot = findSlot(client, kind);
        if (slot < 0) {
            client.player.sendMessage(Text.literal(name(kind) + ": " + I18n.t("нет в хотбаре")), true);
            return;
        }

        // Режим свапа: слот не переключаем, только запускаем ожидание
        boolean lunge = kind == SPEAR && ClickerConfig.swapSpearLunge;
        boolean swap = lunge
                || (kind == SPEAR && ClickerConfig.swapSpearMode)
                || (kind == MACE && ClickerConfig.swapMaceMode);
        if (kind != ELYTRA && swap) {
            pendingKind = kind;
            swapMode = true;
            needTarget = !lunge;  // обычный свап ждёт цель в прицеле; без цели — только свап копья для рывка
            ticksLeft = windowTicks();
            client.player.sendMessage(Text.literal(name(kind) + ": "
                    + I18n.t("жду перезарядку")), true);
            trySwap(client); // если условия уже выполнены — сработает в этот же тик
            return;
        }

        if (kind == ELYTRA && client.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            // Если элитры уже надеты, правый клик поменял бы их обратно на нагрудник — поэтому только переключаем слот
            inv.setSelectedSlot(slot);
            return;
        }

        int prevSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        if (!autoAction(kind)) return;

        if (kind == ELYTRA) {
            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
            // Элитры надеты — в следующем тике возвращаем прошлый слот
            if (ClickerConfig.swapReturn && slot != prevSlot) returnSlot = prevSlot;
            return;
        }

        // Копьё / булава: ждём цель в прицеле
        pendingKind = kind;
        pendingSlot = slot;
        swapMode = false;
        needTarget = true;
        ticksLeft = windowTicks();
        // В этот же тик перезарядка ещё считается по прошлому предмету, так что если она полная и прицел на цели, удар уйдёт сразу
        tryHit(client);
    }

    public static void tick(MinecraftClient client) {
        // Свап: удар был в прошлом тике, теперь возвращаем слот
        if (returnSlot >= 0) flushReturn(client);
        if (pendingKind < 0) return;
        if (client.player == null || client.interactionManager == null) {
            reset();
            return;
        }
        // Обычный режим: слот сменили вручную — отмена
        if (!swapMode && client.player.getInventory().getSelectedSlot() != pendingSlot) {
            reset();
            return;
        }
        if (--ticksLeft < 0) {
            reset();
            return;
        }
        if (swapMode) trySwap(client);
        else tryHit(client);
    }

    private static boolean targetInCrosshair(MinecraftClient client) {
        HitResult h = client.crosshairTarget;
        if (h == null || h.getType() != HitResult.Type.ENTITY || !(h instanceof EntityHitResult ehr)) return false;
        return ehr.getEntity().isAttackable();
    }

    /** Обычный режим: бьём тем, что уже в руке. */
    private static void tryHit(MinecraftClient client) {
        if (client.currentScreen != null) return;
        if (!targetInCrosshair(client)) return;
        if (ClickerConfig.swapWaitCooldown && client.player.getAttackCooldownProgress(0.0f) < 1.0f) return;
        if (((MinecraftClientAccessor) client).invokeDoAttack()) {
            reset(); // удар ровно один
        }
    }

    /** Пустой слот хотбара: прошлый слот, если он пуст, иначе первый пустой; -1, если пустых нет. */
    private static int emptyHotbarSlot(PlayerInventory inv, int prev) {
        if (prev >= 0 && prev < 9 && inv.getStack(prev).isEmpty()) return prev;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isEmpty()) return i;
        }
        return -1;
    }

    /** Режим свапа: в один тик берём предмет, бьём и возвращаем прошлый слот. */
    private static void trySwap(MinecraftClient client) {
        if (client.currentScreen != null) return;
        if (client.player.isUsingItem()) {
            client.player.sendMessage(Text.literal(name(pendingKind) + ": используется предмет"), true);
            return;
        }
        if (needTarget && !targetInCrosshair(client)) {
            client.player.sendMessage(Text.literal(name(pendingKind) + ": нет цели в прицеле"), true);
            return;
        }
        // Перезарядка здесь — у предмета, который сейчас в руке (именно её и «одалживает» свап)
        float cd = client.player.getAttackCooldownProgress(0.0f);
        if (ClickerConfig.swapWaitCooldown && cd < 1.0f) {
            client.player.sendMessage(Text.literal(name(pendingKind) + ": перезарядка " + Math.round(cd * 100) + "%"), true);
            return;
        }

        int slot = findSlot(client, pendingKind);
        if (slot < 0) {
            reset();
            return;
        }
        PlayerInventory inv = client.player.getInventory();
        int prev = inv.getSelectedSlot();
        if (slot != prev) inv.setSelectedSlot(slot);

        // Снимаем задержку между ударами, иначе после недавнего промаха/клика игра проигнорирует удар
        ((MinecraftClientAccessor) client).setAttackCooldown(0);
        boolean ok = ((MinecraftClientAccessor) client).invokeDoAttack();
        client.player.sendMessage(Text.literal(name(pendingKind) + ": "
                + I18n.t(ok || !needTarget ? "свап выполнен" : "удар не прошёл")), true);

        // Тик 1: взяли предмет и ударили. Тик 2: возвращаем слот, на котором были (для копья и булавы одинаково)
        if (slot != prev) returnSlot = prev;
        // С целью — ровно один удар; без цели (рывок) результат клика не проверяем
        if (ok || !needTarget) reset();
    }
}
