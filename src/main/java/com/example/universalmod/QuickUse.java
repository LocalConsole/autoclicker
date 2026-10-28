package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import org.lwjgl.glfw.GLFW;

/**
 * Быстрое использование предмета по клавише: хорус, жемчуг эндера, яблоко, зелье и т.д.
 * Предмет используется напрямую (пакет «использовать предмет»), без взаимодействия с блоком под прицелом —
 * поэтому работает, даже если вы смотрите на верстак, люк, сундук, дверь и т.п.
 * Предмет берётся из хотбара, второй руки или основного инвентаря; после использования всё возвращается обратно.
 */
public class QuickUse {
    // Состояние текущего использования
    private static boolean active = false;
    private static Hand activeHand = Hand.MAIN_HAND;
    private static int prevSlot = -1;     // слот хотбара, который был выбран до использования
    private static int swappedFrom = -1;  // слот основного инвентаря (9..35), если предмет брали оттуда
    private static int ticksWaited = 0;

    public static void register() {
        // Опрос клавиш каждый кадр, чтобы не терять короткие нажатия между тиками
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.FOOD_BAR,
                Identifier.of(UniversalMod.MOD_ID, "quickuse_poll"),
                (context, tickCounter) -> poll(MinecraftClient.getInstance()));
    }

    public static void reset() {
        for (QuickUseBind b : QuickUseConfig.binds) {
            b.wasDown = false;
            b.frameDown = false;
            b.pending = false;
        }
        active = false;
        prevSlot = -1;
        swappedFrom = -1;
    }

    private static boolean isDown(long window, QuickUseBind b) {
        if (b.mouse) return GLFW.glfwGetMouseButton(window, b.keyCode) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(window, b.keyCode) == GLFW.GLFW_PRESS;
    }

    private static void poll(MinecraftClient client) {
        if (client == null || client.player == null) return;
        long window = client.getWindow().getHandle();
        boolean screenOpen = client.currentScreen != null;
        for (QuickUseBind b : QuickUseConfig.binds) {
            if (b.keyCode < 0) {
                b.frameDown = false;
                continue;
            }
            boolean raw = isDown(window, b);
            if (raw && !b.frameDown && !screenOpen) b.pending = true;
            b.frameDown = raw;
        }
    }

    /** Предмет по id: допускается "ender_pearl" и "minecraft:ender_pearl". null, если такого нет. */
    public static Item parse(String id) {
        if (id == null) return null;
        String s = id.trim().toLowerCase().replace(' ', '_');
        if (s.isEmpty()) return null;
        Identifier ident = Identifier.tryParse(s.contains(":") ? s : "minecraft:" + s);
        if (ident == null || !Registries.ITEM.containsId(ident)) return null;
        Item item = Registries.ITEM.get(ident);
        return item;
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) {
            reset();
            return;
        }
        long window = client.getWindow().getHandle();
        boolean screenOpen = client.currentScreen != null;

        // Продолжаем уже начатое использование (хорус/еда едятся несколько тиков)
        if (active) {
            ticksWaited++;
            if (player.isUsingItem() && ticksWaited < 200) {
                // Держим «использование», чтобы игра не прервала еду
                client.options.useKey.setPressed(true);
            } else {
                finish(client);
            }
        }

        for (QuickUseBind b : QuickUseConfig.binds) {
            boolean raw = b.keyCode >= 0 && isDown(window, b);
            if (screenOpen) {
                b.wasDown = raw;
                b.pending = false;
                continue;
            }
            boolean pressed = b.pending || (raw && !b.wasDown);
            b.pending = false;
            b.wasDown = raw;
            if (!pressed || active) continue;
            if (player.isSpectator()) continue;
            start(client, b);
        }
    }

    private static void start(MinecraftClient client, QuickUseBind b) {
        ClientPlayerEntity player = client.player;
        Item item = parse(b.item);
        if (item == null) return;

        ScreenHandler handler = player.playerScreenHandler;
        Hand hand;
        int swapFrom = -1;
        int selected = player.getInventory().getSelectedSlot();
        int oldSelected = selected;

        if (player.getOffHandStack().getItem() == item) {
            hand = Hand.OFF_HAND;
        } else {
            int slot = -1;
            // 1) Уже выбранный слот хотбара, 2) любой слот хотбара, 3) основной инвентарь
            if (player.getInventory().getStack(selected).getItem() == item) slot = selected;
            for (int i = 0; i < 9 && slot < 0; i++) {
                if (player.getInventory().getStack(i).getItem() == item) slot = i;
            }
            if (slot >= 0) {
                if (slot != selected) player.getInventory().setSelectedSlot(slot);
            } else {
                for (int i = 9; i < 36; i++) {
                    if (player.getInventory().getStack(i).getItem() == item) { swapFrom = i; break; }
                }
                if (swapFrom < 0) {
                    player.sendMessage(Text.literal(I18n.t("Нет предмета: ") + b.item), true);
                    return;
                }
                // Меняем предмет с выбранным слотом хотбара (кнопка = номер слота хотбара)
                client.interactionManager.clickSlot(handler.syncId, swapFrom, selected,
                        SlotActionType.SWAP, player);
            }
            hand = Hand.MAIN_HAND;
        }

        // Использование напрямую: пакет «использовать предмет», блок под прицелом не трогаем
        client.interactionManager.interactItem(player, hand);

        if (player.isUsingItem()) {
            // Нужно время (еда, хорус, лук...): держим слот и кнопку, пока не закончится
            active = true;
            activeHand = hand;
            prevSlot = (hand == Hand.MAIN_HAND && oldSelected != player.getInventory().getSelectedSlot()) ? oldSelected : -1;
            swappedFrom = swapFrom;
            ticksWaited = 0;
            client.options.useKey.setPressed(true);
        } else {
            // Мгновенное использование (жемчуг, фейерверк, зелье-взрыв): сразу возвращаем всё как было
            restore(client, oldSelected, swapFrom);
        }
    }

    private static void finish(MinecraftClient client) {
        int p = prevSlot;
        int sw = swappedFrom;
        active = false;
        prevSlot = -1;
        swappedFrom = -1;
        ticksWaited = 0;
        // Если игрок сам не держит ПКМ, отпускаем кнопку
        if (!UniversalMod.isRightOn() && !UniversalMod.isRightHold()
                && !isPhysicalUse(client)) {
            client.options.useKey.setPressed(false);
        }
        restore(client, p, sw);
    }

    private static boolean isPhysicalUse(MinecraftClient client) {
        long w = client.getWindow().getHandle();
        String tk = client.options.useKey.getBoundKeyTranslationKey();
        int code = net.minecraft.client.util.InputUtil.fromTranslationKey(tk).getCode();
        if (code < 0) return false;
        if (tk.startsWith("key.mouse")) return GLFW.glfwGetMouseButton(w, code) == GLFW.GLFW_PRESS;
        return GLFW.glfwGetKey(w, code) == GLFW.GLFW_PRESS;
    }

    /** Возвращает выбранный слот хотбара и, если предмет брали из инвентаря, меняет обратно. */
    private static void restore(MinecraftClient client, int oldSelected, int swapFrom) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.interactionManager == null) return;
        if (swapFrom >= 0) {
            int sel = player.getInventory().getSelectedSlot();
            client.interactionManager.clickSlot(player.playerScreenHandler.syncId, swapFrom, sel,
                    SlotActionType.SWAP, player);
        }
        if (oldSelected >= 0 && oldSelected < 9 && player.getInventory().getSelectedSlot() != oldSelected) {
            player.getInventory().setSelectedSlot(oldSelected);
        }
    }
}
