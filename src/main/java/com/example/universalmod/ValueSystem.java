package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Система ценности. Предмету (в том числе серверному: своё имя, своя текстура/модель) назначается уровень
 * ценности 1–5 и слот (1–9 хотбара или вторая рука). Когда открыт сундук (или по кнопке ★ / клавише),
 * в слот перекладывается самый ценный подходящий предмет, а менее ценный уходит на его место.
 * Перекладка — одним обменом (как клавиша-цифра над слотом). Не вышло за N попыток — делается «руками»:
 * курсор мыши сам едет к слоту, берёт, кладёт и возвращается на место.
 */
public class ValueSystem {
    public static final int OFFHAND = 40;
    public static final int MAX_TIER = 5;
    public static final int[] TIER_COLORS = {0xFF9AA4AE, 0xFF5BD38A, 0xFF56A8F5, 0xFFB57BEE, 0xFFF2C94C, 0xFFFF5A5A};
    public static final String[] TIER_NAMES = {"—", "Обычная", "Хорошая", "Редкая", "Эпическая", "Легендарная"};

    public static class Rule {
        public String item = "";
        public String name = "";   // своё имя предмета ("" — без имени)
        public String model = "";  // модель/текстура (серверные предметы)
        public String cmd = "";    // custom_model_data
        public boolean exact = true; // true — только именно такой (имя+модель), false — любой с таким id
        public int tier = 1;
        public int slot = 0;       // 0–8 хотбар, 40 — вторая рука
        public boolean enabled = true;
        public String label = "";  // как показывать в меню
    }

    public static List<Rule> rules = new ArrayList<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod_values.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<List<Rule>>() {}.getType();

    public static KeyBinding markKey, takeKey;

    // ---------- Файл ----------

    public static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE)) {
            List<Rule> l = GSON.fromJson(r, TYPE);
            if (l != null) {
                for (Rule x : l) {
                    if (x.item == null) x.item = "";
                    if (x.name == null) x.name = "";
                    if (x.model == null) x.model = "";
                    if (x.cmd == null) x.cmd = "";
                    if (x.label == null) x.label = "";
                    x.tier = Math.max(1, Math.min(MAX_TIER, x.tier));
                }
                rules = l;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(rules, TYPE, w);
            }
            Files.move(tmp, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- Сравнение предметов ----------

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    public static String itemId(ItemStack st) {
        return Registries.ITEM.getId(st.getItem()).toString();
    }

    public static String nameOf(ItemStack st) {
        Text t = st.get(DataComponentTypes.CUSTOM_NAME);
        return t == null ? "" : t.getString();
    }

    public static String modelOf(ItemStack st) {
        String m = str(st.get(DataComponentTypes.ITEM_MODEL));
        return m.equals(itemId(st)) ? "" : m; // стандартная модель = id предмета
    }

    public static String cmdOf(ItemStack st) {
        return str(st.get(DataComponentTypes.CUSTOM_MODEL_DATA));
    }

    public static boolean matches(Rule r, ItemStack st) {
        if (!r.enabled || st.isEmpty() || !r.item.equals(itemId(st))) return false;
        if (!r.exact) return true;
        return r.name.equals(nameOf(st)) && r.model.equals(modelOf(st)) && r.cmd.equals(cmdOf(st));
    }

    /** Ценность предмета для данного слота (0 — не ценный). */
    public static int tier(ItemStack st, int slot) {
        int t = 0;
        for (Rule r : rules) if (r.slot == slot && r.tier > t && matches(r, st)) t = r.tier;
        return t;
    }

    /** Самое ценное правило для предмета (для значка и подсказки). */
    public static Rule best(ItemStack st) {
        if (st.isEmpty() || rules.isEmpty()) return null;
        // сначала дешёвая проверка id, имя/модель считаем только если id совпал
        String id = itemId(st);
        Rule b = null;
        String name = null, model = null, cmd = null;
        for (Rule r : rules) {
            if (!r.enabled || !r.item.equals(id) || (b != null && r.tier <= b.tier)) continue;
            if (r.exact) {
                if (name == null) {
                    name = nameOf(st);
                    model = modelOf(st);
                    cmd = cmdOf(st);
                }
                if (!r.name.equals(name) || !r.model.equals(model) || !r.cmd.equals(cmd)) continue;
            }
            b = r;
        }
        return b;
    }

    public static String slotName(int slot) {
        return slot == OFFHAND ? I18n.t("вторая рука") : I18n.t("слот ") + (slot + 1);
    }

    // ---------- Отметка: наведите на предмет, клавиша, потом цифра ----------

    private static ItemStack marking = ItemStack.EMPTY;
    public static long markingSince = 0;

    public static void register() {
        load();
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Rule r = best(stack);
            if (r == null) return;
            lines.add(Text.literal("★ " + I18n.t(TIER_NAMES[r.tier]) + " (" + r.tier + ") → " + slotName(r.slot))
                    .styled(s -> s.withColor(TIER_COLORS[r.tier] & 0xFFFFFF)));
        });
    }

    private static boolean isKey(KeyBinding kb, KeyInput input) {
        if (kb == null || kb.isUnbound()) return false;
        return KeyBindingHelper.getBoundKeyOf(kb).equals(InputUtil.fromKeyCode(input));
    }

    /** Нажатие клавиши в окне инвентаря/сундука. true — съели нажатие. */
    public static boolean onKey(HandledScreen<?> screen, Slot focused, KeyInput input) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!marking.isEmpty()) {
            int k = input.key();
            int slot = -1;
            if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_9) slot = k - GLFW.GLFW_KEY_1;
            else if (k == GLFW.GLFW_KEY_F) slot = OFFHAND;
            if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_BACKSPACE) {
                marking = ItemStack.EMPTY;
                say(mc, I18n.t("Отметка отменена"));
                return true;
            }
            if (slot < 0) return true;
            assign(mc, marking, slot);
            marking = ItemStack.EMPTY;
            return true;
        }
        if (isKey(markKey, input)) {
            if (focused == null || !focused.hasStack()) {
                say(mc, I18n.t("Наведите курсор на предмет"));
                return true;
            }
            marking = focused.getStack().copy();
            markingSince = System.currentTimeMillis();
            say(mc, I18n.t("Теперь цифра 1–9 — слот, F — вторая рука, Esc — отмена"));
            return true;
        }
        if (isKey(takeKey, input)) {
            start(mc, true);
            return true;
        }
        return false;
    }

    /** Клавиша отметки вне инвентаря: предмет в руке становится ценным для своего же слота (ещё раз — уровень выше). */
    public static void markHand(MinecraftClient mc) {
        if (mc.player == null) return;
        ItemStack st = mc.player.getMainHandStack();
        if (st.isEmpty()) {
            say(mc, I18n.t("Возьмите предмет в руку или отметьте его в инвентаре"));
            return;
        }
        assign(mc, st, mc.player.getInventory().getSelectedSlot());
    }

    public static boolean isMarking() {
        return !marking.isEmpty();
    }

    public static ItemStack markingStack() {
        return marking;
    }

    /** Создать правило или поднять уровень, если такое уже есть для этого слота. */
    public static void assign(MinecraftClient mc, ItemStack st, int slot) {
        for (Rule r : rules) {
            if (r.slot == slot && matches(r, st) && r.exact) {
                r.tier = r.tier % MAX_TIER + 1;
                save();
                say(mc, label(r) + ": " + I18n.t(TIER_NAMES[r.tier]) + " (" + r.tier + ") → " + slotName(slot));
                return;
            }
        }
        Rule r = new Rule();
        r.item = itemId(st);
        r.name = nameOf(st);
        r.model = modelOf(st);
        r.cmd = cmdOf(st);
        r.slot = slot;
        r.tier = 1;
        for (Rule o : rules) if (o.slot == slot) r.tier = Math.max(r.tier, Math.min(MAX_TIER, o.tier + 1));
        r.label = st.getName().getString();
        rules.add(r);
        save();
        say(mc, label(r) + ": " + I18n.t(TIER_NAMES[r.tier]) + " (" + r.tier + ") → " + slotName(slot)
                + I18n.t(". Уровень можно поменять в меню ценности"));
    }

    public static String label(Rule r) {
        if (!r.label.isEmpty()) return r.label;
        return r.item;
    }

    private static void say(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    // ---------- Перекладка ----------

    private static ScreenHandler handler = null;
    private static int openTicks = 0;
    private static boolean autoDone = false;
    private static boolean running = false;
    private static int moves = 0;
    private static int cooldown = 0;

    // текущая операция
    private static int jobSource = -1, jobTarget = -1, jobTargetSlotId = -1, jobTier = 0, wait = 0;
    private static int[] tries = new int[41];
    private static final Set<Integer> givenUp = new LinkedHashSet<>();

    // курсор
    private static int cursorStep = -1;
    private static double savedX, savedY;
    public static int screenX, screenY;

    /** Запуск перекладки (по кнопке/клавише или авто). */
    public static void start(MinecraftClient mc, boolean manual) {
        if (rules.isEmpty()) {
            if (manual) say(mc, I18n.t("Нет ценных предметов: наведите на предмет и нажмите клавишу отметки"));
            return;
        }
        running = true;
        moves = 0;
        tries = new int[41];
        givenUp.clear();
        jobSource = -1;
        cursorStep = -1;
        if (manual) say(mc, I18n.t("Забираю ценное..."));
    }

    public static boolean isRunning() {
        return running;
    }

    public static void tick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || !(mc.currentScreen instanceof HandledScreen<?> hs) || mc.currentScreen instanceof CreativeInventoryScreen) {
            if (cursorStep >= 0) restoreCursor(mc);
            handler = null;
            running = false;
            marking = ItemStack.EMPTY;
            return;
        }
        ScreenHandler h = hs.getScreenHandler();
        if (h != handler) {
            handler = h;
            openTicks = 0;
            autoDone = false;
            running = false;
        }
        openTicks++;
        boolean container = h != p.playerScreenHandler;
        if (ClickerConfig.valueAuto && container && !autoDone && openTicks >= 4) {
            autoDone = true;
            start(mc, false);
        }
        if (!running) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (cursorStep >= 0) {
            cursorTick(mc, h);
            return;
        }
        if (jobSource >= 0) {
            if (--wait > 0) return;
            // проверка: встал ли предмет
            ItemStack now = p.getInventory().getStack(jobTarget);
            if (tier(now, jobTarget) >= jobTier) {
                jobSource = -1;
                cooldown = Math.max(0, ClickerConfig.valueDelay);
                return;
            }
            tries[jobTarget]++;
            if (tries[jobTarget] > ClickerConfig.valueRetries) {
                if (ClickerConfig.valueCursor && jobTargetSlotId >= 0) {
                    cursorStep = 0;
                } else {
                    givenUp.add(jobTarget);
                    jobSource = -1;
                }
                return;
            }
            jobSource = -1; // пересчитаем и попробуем ещё раз
        }
        if (moves >= 40 || !plan(mc, h)) {
            running = false;
            return;
        }
        moves++;
        int button = jobTarget == OFFHAND ? 40 : jobTarget;
        mc.interactionManager.clickSlot(h.syncId, jobSource, button, SlotActionType.SWAP, p);
        wait = 4;
    }

    /** Найти одну лучшую перекладку. */
    private static boolean plan(MinecraftClient mc, ScreenHandler h) {
        PlayerInventory inv = mc.player.getInventory();
        Set<Integer> targets = new LinkedHashSet<>();
        for (Rule r : rules) if (r.enabled) targets.add(r.slot);
        int bestGain = 0;
        for (int t : targets) {
            if (givenUp.contains(t)) continue;
            int cur = tier(inv.getStack(t), t);
            for (Slot s : h.slots) {
                if (!s.hasStack()) continue;
                boolean own = s.inventory == inv;
                int idx = own ? s.getIndex() : -1;
                if (own && (targets.contains(idx) || idx >= 36)) continue; // не воруем из других ценных слотов и брони
                int tr = tier(s.getStack(), t);
                if (tr > cur && tr - cur > bestGain) {
                    bestGain = tr - cur;
                    jobSource = s.id;
                    jobTarget = t;
                    jobTier = tr;
                    jobTargetSlotId = -1;
                    for (Slot q : h.slots) {
                        if (q.inventory == inv && q.getIndex() == t) {
                            jobTargetSlotId = q.id;
                            break;
                        }
                    }
                }
            }
        }
        return bestGain > 0;
    }

    // ---------- Перекладка курсором ----------

    private static void moveCursor(MinecraftClient mc, ScreenHandler h, int slotId) {
        if (slotId < 0 || slotId >= h.slots.size()) return;
        Slot s = h.slots.get(slotId);
        var w = mc.getWindow();
        double k = w.getWidth() / (double) Math.max(1, w.getScaledWidth());
        GLFW.glfwSetCursorPos(w.getHandle(), (screenX + s.x + 8) * k, (screenY + s.y + 8) * k);
    }

    private static void restoreCursor(MinecraftClient mc) {
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), savedX, savedY);
        cursorStep = -1;
    }

    private static void cursorTick(MinecraftClient mc, ScreenHandler h) {
        ClientPlayerEntity p = mc.player;
        switch (cursorStep) {
            case 0 -> {
                double[] cx = new double[1], cy = new double[1];
                GLFW.glfwGetCursorPos(mc.getWindow().getHandle(), cx, cy);
                savedX = cx[0];
                savedY = cy[0];
                if (!h.getCursorStack().isEmpty()) { // в руке уже что-то есть — не рискуем
                    givenUp.add(jobTarget);
                    jobSource = -1;
                    cursorStep = -1;
                    return;
                }
                moveCursor(mc, h, jobSource);
            }
            case 1 -> mc.interactionManager.clickSlot(h.syncId, jobSource, 0, SlotActionType.PICKUP, p);
            case 2 -> moveCursor(mc, h, jobTargetSlotId);
            case 3 -> mc.interactionManager.clickSlot(h.syncId, jobTargetSlotId, 0, SlotActionType.PICKUP, p);
            case 4 -> moveCursor(mc, h, jobSource);
            case 5 -> {
                if (!h.getCursorStack().isEmpty()) mc.interactionManager.clickSlot(h.syncId, jobSource, 0, SlotActionType.PICKUP, p);
            }
            default -> {
                restoreCursor(mc);
                if (tier(p.getInventory().getStack(jobTarget), jobTarget) < jobTier) givenUp.add(jobTarget);
                jobSource = -1;
                cooldown = Math.max(0, ClickerConfig.valueDelay);
                return;
            }
        }
        cursorStep++;
        cooldown = 2; // чтобы было видно, как курсор едет
    }

    // ---------- Значки на слотах ----------

    /** Цветной уголок и номер уровня на ценных предметах; рамка на назначенных слотах. */
    public static void drawMarks(DrawContext ctx, ScreenHandler h, int x, int y) {
        screenX = x;
        screenY = y;
        if (!ClickerConfig.valueMarks || rules.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        PlayerInventory inv = mc.player.getInventory();
        Set<Integer> targets = new LinkedHashSet<>();
        for (Rule r : rules) if (r.enabled) targets.add(r.slot);
        var tr = mc.textRenderer;
        for (Slot s : h.slots) {
            int sx = x + s.x, sy = y + s.y;
            if (s.inventory == inv && targets.contains(s.getIndex())) {
                // назначенный слот — тонкая золотая рамка
                int c = 0x88F2C94C;
                ctx.fill(sx - 1, sy - 1, sx + 17, sy, c);
                ctx.fill(sx - 1, sy + 16, sx + 17, sy + 17, c);
                ctx.fill(sx - 1, sy, sx, sy + 16, c);
                ctx.fill(sx + 16, sy, sx + 17, sy + 16, c);
            }
            if (!s.hasStack()) continue;
            Rule r = best(s.getStack());
            if (r == null) continue;
            int col = TIER_COLORS[r.tier];
            ctx.fill(sx, sy, sx + 6, sy + 7, 0xCC000000);
            ctx.drawText(tr, Text.literal(String.valueOf(r.tier)), sx + 1, sy, col, false);
        }
        if (!marking.isEmpty()) {
            String t = I18n.t("Отметка: нажмите 1–9 или F, Esc — отмена");
            int w = tr.getWidth(t) + 26;
            int cx = ctx.getScaledWindowWidth() / 2 - w / 2;
            int ty = Math.max(2, y - 26);
            ctx.fill(cx - 1, ty - 1, cx + w + 1, ty + 19, 0xFFF2C94C);
            ctx.fill(cx, ty, cx + w, ty + 18, 0xF0161B21);
            ctx.drawItem(marking, cx + 2, ty + 1);
            ctx.drawTextWithShadow(tr, Text.literal(t), cx + 22, ty + 5, 0xFFFFFFFF);
        }
    }

    /** Подпись для подсказки в меню. */
    public static Text tierText(int tier) {
        return Text.literal("★" + tier + " " + I18n.t(TIER_NAMES[tier])).formatted(Formatting.BOLD);
    }
}
