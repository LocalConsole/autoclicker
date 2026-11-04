package com.example.universalmod;

import com.example.universalmod.mixin.MinecraftClientAccessor;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * Точная установка блоков — переписанная для нашего мода логика Accurate Block Placement
 * (автор Clayborn, форк Reborn от hschwar, лицензия MIT, https://github.com/hschwar/AccurateBlockPlacement-Reborn).
 * Код не скопирован: оригинал написан в Mojang-маппингах, здесь всё переписано под Yarn и сильно урезано
 * до главного приёма.
 *
 * В чём приём. Ваниль, пока зажата ПКМ, ставит блок раз в 4 тика и всегда туда, куда смотрит прицел
 * в этот момент. Поэтому при быстром ведении мышью часть клеток пропускается, а в одну и ту же клетку
 * прилетает по несколько попыток.
 *
 * Здесь установка привязана не к тику, а к лучу прицела: игра пересчитывает его каждый кадр, и сразу
 * после пересчёта мы смотрим, в какую клетку встанет блок. Новая клетка — ставим ровно один блок.
 * Та же самая — не делаем ничего. Получается один блок на клетку: ни пропусков, ни дублей.
 *
 * Пока режим ведёт установку, ванильная глушится (suppressVanilla), иначе она ставила бы блоки
 * параллельно и всё портила. Наши собственные вызовы проходят по флагу busy.
 *
 * Чего в этой версии нет по сравнению с оригиналом: очереди «догнать пропущенное во время задержки»,
 * реакции на движение мыши и десятка частных случаев (леса, компостер, костная мука, ступеньки).
 * Из частных случаев оставлен только самый нужный: блоки с интерфейсом (сундуки, печи) по правому
 * клику открываются, а не застраиваются, если не зажат Shift.
 */
public class AccuratePlace {
    /** Наш собственный вызов установки — его глушить нельзя. */
    public static boolean busy = false;
    /** Пока true, ванильная установка отключена: её ведём мы. */
    public static boolean suppressVanilla = false;
    /** Сколько раз сработала вставка в луч прицела. Ноль при включённом режиме — миксин не встал. */
    public static int hookHits = 0;

    private static BlockPos lastPlaced = null;
    private static boolean wasDown = false;

    public static void reset() {
        lastPlaced = null;
        suppressVanilla = false;
    }

    /** Вызывается сразу после того, как игра пересчитала прицел (раз в кадр). */
    public static void onPick(MinecraftClient client) {
        hookHits++;
        if (!ClickerConfig.accuratePlace || client == null || client.player == null
                || client.world == null || client.currentScreen != null) {
            wasDown = false;
            reset();
            return;
        }

        boolean down = client.options.useKey.isPressed();
        boolean fresh = down && !wasDown;
        wasDown = down;
        if (!down) { reset(); return; }

        // Работаем только с блоком в руке: еда, вёдра, яйца призыва — не наше дело
        ItemStack stack = client.player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem)) stack = client.player.getOffHandStack();
        if (!(stack.getItem() instanceof BlockItem) || client.player.isUsingItem()) { reset(); return; }

        if (!(client.crosshairTarget instanceof BlockHitResult bhr)
                || client.crosshairTarget.getType() != HitResult.Type.BLOCK) { reset(); return; }

        // Сундуки, печи, бочки и прочее с интерфейсом: без Shift правый клик должен их открывать
        if (client.world.getBlockState(bhr.getBlockPos()).getBlock() instanceof BlockWithEntity
                && !client.player.isSneaking()) { reset(); return; }

        // Куда встанет блок — соседняя клетка со стороны, по которой пришёлся луч
        BlockPos target = bhr.getBlockPos().offset(bhr.getSide());

        if (fresh) lastPlaced = null;          // кнопку нажали заново — история не нужна
        suppressVanilla = true;                // дальше установку ведём мы
        if (target.equals(lastPlaced)) return; // в эту клетку уже ставили, второй раз не нужно

        MinecraftClientAccessor acc = (MinecraftClientAccessor) client;
        busy = true;
        try {
            acc.setItemUseCooldown(0);
            acc.invokeDoItemUse();
        } finally {
            busy = false;
        }
        acc.setItemUseCooldown(0);

        // Запоминаем клетку, только если блок действительно встал
        if (!client.world.getBlockState(target).isAir()) lastPlaced = target;
    }
}
