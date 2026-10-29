package com.example.universalmod;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Авто-кристалл: когда вы ставите обсидиан, а в хотбаре лежит кристалл Энда, кристалл сам встаёт сверху на этот
 * обсидиан. Взрывать нужно самому. Кристалл берётся и ставится в один тик, после чего возвращается прежний слот,
 * так что в руке остаётся обсидиан и можно продолжать ставить.
 */
public class AutoCrystal {
    private static BlockPos pending = null;
    private static int age = 0;

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient() || !ClickerConfig.autoCrystal) return ActionResult.PASS;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || player != mc.player) return ActionResult.PASS;
            if (!player.getStackInHand(hand).isOf(Items.OBSIDIAN)) return ActionResult.PASS;

            BlockPos clicked = hit.getBlockPos();
            BlockPos place = world.getBlockState(clicked).isReplaceable() ? clicked : clicked.offset(hit.getSide());
            // Если там уже стоял обсидиан, то новый блок не ставится — кристалл не нужен
            if (world.getBlockState(place).isOf(Blocks.OBSIDIAN)) return ActionResult.PASS;
            pending = place.toImmutable();
            age = 0;
            return ActionResult.PASS;
        });
    }

    public static void reset() {
        pending = null;
    }

    public static void tick(MinecraftClient client) {
        if (pending == null) return;
        if (client.player == null || client.world == null || client.interactionManager == null
                || !ClickerConfig.autoCrystal) {
            pending = null;
            return;
        }
        // Ждём один тик, чтобы обсидиан успел поставиться
        if (age++ < 1) return;
        BlockPos pos = pending;
        pending = null;

        if (client.currentScreen != null || client.player.isSpectator()) return;
        if (!client.world.getBlockState(pos).isOf(Blocks.OBSIDIAN)) return;

        // Те же условия, что и у ванильного кристалла: сверху воздух и свободно 1x2 блока
        BlockPos up = pos.up();
        if (!client.world.getBlockState(up).isAir()) return;
        Box box = new Box(up.getX(), up.getY(), up.getZ(), up.getX() + 1.0, up.getY() + 2.0, up.getZ() + 1.0);
        if (!client.world.getOtherEntities(null, box).isEmpty()) return;

        PlayerInventory inv = client.player.getInventory();
        int crystal = -1;
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).isOf(Items.END_CRYSTAL)) {
                crystal = i;
                break;
            }
        }
        if (crystal < 0) return;

        Vec3d hitPos = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        if (client.player.getEyePos().squaredDistanceTo(hitPos) > 4.5 * 4.5) return;

        int prev = inv.getSelectedSlot();
        boolean switched = crystal != prev;
        if (switched) inv.setSelectedSlot(crystal);
        BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, pos, false);
        ActionResult r = client.interactionManager.interactBlock(client.player, Hand.MAIN_HAND, bhr);
        if (r.isAccepted()) client.player.swingHand(Hand.MAIN_HAND);
        if (switched) inv.setSelectedSlot(prev);
    }
}
