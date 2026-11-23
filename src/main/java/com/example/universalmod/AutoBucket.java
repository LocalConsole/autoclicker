package com.example.universalmod;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Вёдра за 4 тика:
 *  - Авто-лава: после удара по противнику (если он не горит и в хотбаре есть ведро лавы)
 *    1 тик — ведро в руку и взгляд под ноги цели, 2 тик — лава под цель, 3 тик — лава забирается обратно,
 *    4 тик — прежний слот и прежний взгляд.
 *  - Авто-вода (ватердроп): при быстром падении, когда земля уже близко, — то же самое водой под себя.
 *    Вода забирается, как только вы приземлились в неё.
 */
public class AutoBucket {
    private static int step = 0;          // 0 — ничего не делаем
    private static boolean water;         // какое ведро
    private static int prevSlot;
    private static float prevYaw, prevPitch;
    private static float aimYaw, aimPitch;
    private static int waitTicks = 0;
    private static long lastLava = -1000;
    private static long ticks = 0;
    private static Entity lavaTarget = null;

    public static boolean busy() {
        return step != 0;
    }

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient() || !ClickerConfig.autoLava) return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (player != mc.player || step != 0) return ActionResult.PASS;
            if (entity instanceof LivingEntity le && !le.isOnFire() && ticks - lastLava > 40) lavaTarget = entity;
            return ActionResult.PASS;
        });
    }

    public static void reset() {
        step = 0;
        lavaTarget = null;
    }

    private static int findHotbar(PlayerInventory inv, Item item) {
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isOf(item)) return i;
        return -1;
    }

    /** Повернуть взгляд на точку. */
    private static void aimAt(ClientPlayerEntity p, Vec3d at) {
        Vec3d d = at.subtract(p.getEyePos());
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        aimYaw = (float) (MathHelper.atan2(d.z, d.x) * MathHelper.DEGREES_PER_RADIAN) - 90f;
        aimPitch = (float) -(MathHelper.atan2(d.y, h) * MathHelper.DEGREES_PER_RADIAN);
        p.setYaw(aimYaw);
        p.setPitch(aimPitch);
    }

    private static boolean start(ClientPlayerEntity p, boolean isWater, Vec3d aim) {
        PlayerInventory inv = p.getInventory();
        int slot = findHotbar(inv, isWater ? Items.WATER_BUCKET : Items.LAVA_BUCKET);
        if (slot < 0) return false;
        water = isWater;
        prevSlot = inv.getSelectedSlot();
        prevYaw = p.getYaw();
        prevPitch = p.getPitch();
        // 1 тик: ведро в руку и взгляд в нужную точку (поворот уйдёт на сервер в конце тика)
        inv.setSelectedSlot(slot);
        aimAt(p, aim);
        step = 2;
        waitTicks = 0;
        return true;
    }

    public static void tick(MinecraftClient mc) {
        ticks++;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            reset();
            return;
        }
        if (step != 0) {
            run(mc, p);
            return;
        }
        if (mc.currentScreen != null || p.isSpectator()) {
            lavaTarget = null;
            return;
        }

        // ватердроп
        if (ClickerConfig.autoWater && !p.isCreative() && !p.getAbilities().flying && !p.isTouchingWater()
                && p.getVelocity().y < -0.5) {
            Vec3d from = new Vec3d(p.getX(), p.getY(), p.getZ());
            BlockHitResult ground = mc.world.raycast(new RaycastContext(from, from.add(0, -6, 0),
                    RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, p));
            if (ground.getType() == HitResult.Type.BLOCK && mc.world.getFluidState(ground.getBlockPos()).isEmpty()) {
                double fall = from.y - ground.getPos().y;
                double speed = -p.getVelocity().y;
                double reach = p.getBlockInteractionRange();
                Vec3d aim = Vec3d.ofCenter(ground.getBlockPos()).add(0, 0.5, 0);
                // ставим, когда земля в досягаемости и за 1–2 тика мы до неё долетим
                if (fall <= speed * 2.5 + 1.0 && p.getEyePos().distanceTo(aim) < reach - 0.2) {
                    if (start(p, true, aim)) return;
                }
            }
        }

        // авто-лава
        if (lavaTarget != null) {
            Entity t = lavaTarget;
            lavaTarget = null;
            if (!ClickerConfig.autoLava || !t.isAlive()) return;
            BlockPos feet = t.getBlockPos();
            if (!mc.world.getBlockState(feet).isReplaceable() || mc.world.getBlockState(feet.down()).isReplaceable()) return;
            Vec3d aim = new Vec3d(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
            if (p.getEyePos().distanceTo(aim) > p.getBlockInteractionRange() - 0.2) return;
            if (start(p, false, aim)) lastLava = ticks;
        }
    }

    private static void run(MinecraftClient mc, ClientPlayerEntity p) {
        PlayerInventory inv = p.getInventory();
        switch (step) {
            case 2 -> {
                // 2 тик: выливаем
                p.setYaw(aimYaw);
                p.setPitch(aimPitch);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                step = 3;
            }
            case 3 -> {
                // 3 тик: забираем обратно (воду — когда уже приземлились в неё)
                if (water && !(p.isTouchingWater() || p.isOnGround()) && ++waitTicks < 15) return;
                p.setYaw(aimYaw);
                p.setPitch(aimPitch);
                if (p.getMainHandStack().isOf(Items.BUCKET)) {
                    mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                    p.swingHand(Hand.MAIN_HAND);
                }
                step = 4;
            }
            default -> {
                // 4 тик: прежний слот и прежний взгляд
                inv.setSelectedSlot(prevSlot);
                p.setYaw(prevYaw);
                p.setPitch(prevPitch);
                step = 0;
            }
        }
    }
}
