package com.example.universalmod;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

/** При смерти автоматически ставит метку в этом месте (красная, «Смерть»). Хранится только последняя. */
public class DeathMark {
    private static final Set<String> NAMES = Set.of("Смерть", "O'lim", "Death");
    private static boolean handled = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(DeathMark::tick);
    }

    private static void tick(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof DeathScreen)) {
            handled = false;
            return;
        }
        if (handled || !ClickerConfig.deathMark) return;
        handled = true;
        if (mc.player == null || mc.world == null) return;

        BlockPos p = mc.player.getBlockPos();
        String name = I18n.t("Смерть");
        Waypoints.list.removeIf(w -> NAMES.contains(w.name));
        Waypoints.list.add(new Waypoint(name, p.getX(), p.getY(), p.getZ(), 0, Waypoints.currentDim(mc)));
        Waypoints.save();
        mc.player.sendMessage(Text.literal(name + ": " + p.getX() + ", " + p.getY() + ", " + p.getZ()), false);
    }
}
