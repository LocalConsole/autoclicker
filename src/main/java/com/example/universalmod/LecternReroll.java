package com.example.universalmod;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;

/**
 * Авто-перебор библиотекаря: сам ломает кафедру, ставит её обратно, ждёт, пока житель снова станет
 * библиотекарем, открывает торговлю и смотрит книгу. Повторяет, пока не выпадет нужная книга
 * по нужной цене (список и цена — в помощнике «Жители и зачарования»).
 * Как запустить: встаньте вплотную к кафедре (чтобы подбирать её), посмотрите на жителя-новичка
 * и нажмите клавишу «Библиотекарь: авто-перебор». Остановить — та же клавиша или Esc.
 */
public class LecternReroll {
    private enum St { IDLE, BREAK, WAIT_LOSE, PLACE, WAIT_JOB, OPEN, WAIT_OFFERS }

    private static St st = St.IDLE;
    private static VillagerEntity villager;
    private static BlockPos lectern;
    private static int timer, attempts;
    private static int lecternsBefore;
    private static String lastBook = "";

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "lectern_reroll"), (ctx, tc) -> hud(ctx));
    }

    public static boolean active() {
        return st != St.IDLE;
    }

    private static void say(MinecraftClient mc, String s, boolean bar) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), bar);
    }

    public static void stop(MinecraftClient mc, String why) {
        if (st == St.IDLE) return;
        st = St.IDLE;
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        if (why != null) say(mc, why, false);
    }

    /** Клавиша: старт/стоп. */
    public static void toggle(MinecraftClient mc) {
        if (st != St.IDLE) {
            stop(mc, I18n.t("Перебор остановлен. Попыток: ") + attempts);
            return;
        }
        if (mc.player == null || mc.world == null) return;
        if (ClickerConfig.librarianWants.isBlank()) {
            say(mc, I18n.t("Сначала напишите нужные книги в помощнике «Жители и зачарования»"), false);
            return;
        }
        if (!(mc.crosshairTarget instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof VillagerEntity v)) {
            say(mc, I18n.t("Посмотрите на жителя и нажмите ещё раз"), true);
            return;
        }
        // ближайшая кафедра рядом с жителем
        BlockPos best = null;
        double bd = 1e9;
        BlockPos c = v.getBlockPos();
        for (BlockPos p : BlockPos.iterate(c.add(-4, -2, -4), c.add(4, 2, 4))) {
            if (!mc.world.getBlockState(p).isOf(Blocks.LECTERN)) continue;
            double d = p.getSquaredDistance(mc.player.getEyePos());
            if (d < bd) {
                bd = d;
                best = p.toImmutable();
            }
        }
        if (best == null) {
            say(mc, I18n.t("Рядом с жителем нет кафедры"), false);
            return;
        }
        if (Math.sqrt(bd) > mc.player.getBlockInteractionRange() - 0.5) {
            say(mc, I18n.t("Подойдите к кафедре ближе"), false);
            return;
        }
        String data = String.valueOf(v.getVillagerData());
        if (TradeTable.levelOf(data) > 1) {
            say(mc, I18n.t("Житель уже не новичок — его трейды больше не меняются"), false);
            return;
        }
        villager = v;
        lectern = best;
        attempts = 0;
        lastBook = "";
        st = St.OPEN; // сначала проверим, что он продаёт сейчас
        timer = 0;
        say(mc, I18n.t("Авто-перебор запущен. Ищу: ") + ClickerConfig.librarianWants + I18n.t(" (до ") + ClickerConfig.librarianMaxPrice + I18n.t(" изумр.)"), false);
    }

    private static void look(ClientPlayerEntity p, Vec3d at) {
        Vec3d d = at.subtract(p.getEyePos());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        p.setYaw((float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90f);
        p.setPitch((float) -(MathHelper.atan2(d.y, h) * MathHelper.DEGREES_PER_RADIAN));
    }

    private static int count(PlayerInventory inv) {
        int n = 0;
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(Items.LECTERN)) n += inv.getStack(i).getCount();
        return n;
    }

    private static boolean selectLectern(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.LECTERN)) {
                inv.setSelectedSlot(i);
                return true;
            }
        }
        for (int i = 9; i < 36; i++) {
            if (inv.getStack(i).isOf(Items.LECTERN)) {
                int hot = inv.getSelectedSlot();
                mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, i, hot, SlotActionType.SWAP, mc.player);
                return true;
            }
        }
        return false;
    }

    private static void selectAxe(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isIn(ItemTags.AXES)) {
                inv.setSelectedSlot(i);
                return;
            }
        }
    }

    private static String profession() {
        return TradeTable.professionOf(String.valueOf(villager.getVillagerData()));
    }

    public static void tick(MinecraftClient mc) {
        if (st == St.IDLE) return;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || villager == null || !villager.isAlive() || villager.isRemoved()) {
            stop(mc, I18n.t("Перебор остановлен: житель пропал"));
            return;
        }
        if (mc.currentScreen instanceof GameMenuScreen) {
            stop(mc, I18n.t("Перебор остановлен. Попыток: ") + attempts);
            return;
        }
        if (attempts >= ClickerConfig.rerollMax) {
            stop(mc, I18n.t("Перебор остановлен: достигнут предел попыток ") + attempts);
            return;
        }
        timer++;
        switch (st) {
            case BREAK -> {
                if (mc.currentScreen != null) mc.player.closeHandledScreen();
                if (mc.world.getBlockState(lectern).isAir()) {
                    mc.interactionManager.cancelBlockBreaking();
                    st = St.WAIT_LOSE;
                    timer = 0;
                    return;
                }
                if (timer == 1) {
                    lecternsBefore = count(p.getInventory());
                    selectAxe(p.getInventory());
                }
                look(p, Vec3d.ofCenter(lectern));
                if (timer == 1) mc.interactionManager.attackBlock(lectern, Direction.UP);
                else mc.interactionManager.updateBlockBreakingProgress(lectern, Direction.UP);
                p.swingHand(Hand.MAIN_HAND);
                if (timer > 200) stop(mc, I18n.t("Не получается сломать кафедру"));
            }
            case WAIT_LOSE -> {
                // ждём, пока подберётся кафедра и житель потеряет работу
                boolean picked = count(p.getInventory()) > lecternsBefore || p.isCreative();
                // не подобралась (инвентарь полон, далеко), но запасные кафедры есть — работаем дальше ими
                if (!picked && timer > 30 && count(p.getInventory()) > 0) picked = true;
                if (picked && !"librarian".equals(profession())) {
                    st = St.PLACE;
                    timer = 0;
                } else if (timer > 100) {
                    stop(mc, picked ? I18n.t("Житель не теряет работу — рядом есть другая кафедра?")
                            : I18n.t("Кафедра не подобралась — встаньте к ней вплотную"));
                }
            }
            case PLACE -> {
                if (!mc.world.getBlockState(lectern).isReplaceable()) {
                    st = St.WAIT_JOB;
                    timer = 0;
                    return;
                }
                if (timer == 1 && !selectLectern(mc)) {
                    stop(mc, I18n.t("Нет кафедры в инвентаре"));
                    return;
                }
                if (timer < 3) return; // даём серверу переложить предмет
                BlockPos below = lectern.down();
                Vec3d hit = new Vec3d(lectern.getX() + 0.5, lectern.getY(), lectern.getZ() + 0.5);
                look(p, hit);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, below, false));
                p.swingHand(Hand.MAIN_HAND);
                if (timer > 40) stop(mc, I18n.t("Не получается поставить кафедру"));
            }
            case WAIT_JOB -> {
                if ("librarian".equals(profession())) {
                    st = St.OPEN;
                    timer = 0;
                } else if (timer > 400) {
                    stop(mc, I18n.t("Житель не берёт кафедру 20 секунд — он может её не видеть"));
                }
            }
            case OPEN -> {
                if (timer < 5) return;
                look(p, villager.getEyePos());
                if (!(mc.currentScreen instanceof MerchantScreen)) {
                    mc.interactionManager.interactEntity(p, villager, Hand.MAIN_HAND);
                }
                st = St.WAIT_OFFERS;
                timer = 0;
            }
            case WAIT_OFFERS -> {
                if (mc.currentScreen instanceof MerchantScreen ms) {
                    TradeOfferList list = ms.getScreenHandler().getRecipes();
                    if (list == null || list.isEmpty()) return;
                    attempts++;
                    lastBook = "";
                    for (TradeOffer o : list) {
                        ItemStack sell = o.getSellItem();
                        if (!sell.isOf(Items.ENCHANTED_BOOK)) continue;
                        String label = VillagerTrades.enchantText(sell);
                        int price = o.getDisplayedFirstBuyItem().getCount();
                        lastBook = label + " (" + price + ")";
                        if (VillagerTrades.wanted(label) && price <= ClickerConfig.librarianMaxPrice) {
                            st = St.IDLE;
                            say(mc, "§a§l★ " + I18n.t("НАШЁЛ за ") + attempts + I18n.t(" попыток: ") + label + " — " + price + I18n.t(" изумр."), false);
                            p.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                            return;
                        }
                    }
                    if (TradeTable.levelOf(String.valueOf(villager.getVillagerData())) > 1) {
                        stop(mc, I18n.t("Житель уже торговал — его трейды больше не поменяются"));
                        return;
                    }
                    mc.player.closeHandledScreen();
                    st = St.BREAK;
                    timer = 0;
                } else if (timer > 60) {
                    st = St.OPEN; // окно не открылось — пробуем ещё раз
                    timer = 0;
                }
            }
            default -> { }
        }
    }

    private static void hud(DrawContext ctx) {
        if (st == St.IDLE) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        String step = switch (st) {
            case BREAK -> I18n.t("ломаю кафедру");
            case WAIT_LOSE -> I18n.t("жду, пока житель потеряет работу");
            case PLACE -> I18n.t("ставлю кафедру");
            case WAIT_JOB -> I18n.t("жду, пока житель станет библиотекарем");
            default -> I18n.t("смотрю книгу");
        };
        String l1 = I18n.t("Перебор библиотекаря — попытка ") + (attempts + 1) + ": " + step;
        String l2 = lastBook.isEmpty() ? I18n.t("Ищу: ") + ClickerConfig.librarianWants : I18n.t("Было: ") + lastBook;
        String l3 = I18n.t("Стоп — та же клавиша или Esc");
        int w = Math.max(tr.getWidth(l1), Math.max(tr.getWidth(l2), tr.getWidth(l3))) + 10;
        int x = (ctx.getScaledWindowWidth() - w) / 2, y = ctx.getScaledWindowHeight() / 2 + 24;
        ctx.fill(x - 1, y - 1, x + w + 1, y + 33, 0xFF5BD38A);
        ctx.fill(x, y, x + w, y + 32, 0xE0161B21);
        ctx.drawTextWithShadow(tr, Text.literal(l1), x + 5, y + 2, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(l2, w - 10)), x + 5, y + 12, 0xFFB57BEE);
        ctx.drawTextWithShadow(tr, Text.literal(l3), x + 5, y + 22, 0xFF6F7A86);
    }
}
