package com.example.universalmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.lwjgl.glfw.GLFW;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Мини-Litematica. Инструмент — стрела в основной руке. Короткое нажатие Shift меняет режим:
 *  1. Выделение: ПКМ — первая точка, ЛКМ — вторая, Ctrl+Shift — сохранить схему.
 *  2. Размещение: ПКМ — поставить схему туда, куда смотрите; ЛКМ — следующая схема;
 *     Ctrl+ПКМ — повернуть на 90°; Ctrl+ЛКМ — убрать схему.
 *  3. Вставка (только креатив): Shift+ПКМ — поставить все блоки схемы.
 *     Если сервер даёт /fill и /setblock — командами, иначе блоки ставятся «руками» в радиусе досягаемости.
 *  4. Постройка из инвентаря: ПКМ — вкл/выкл. Блоки берутся из инвентаря, камера сама наводится,
 *     блоки ставятся автоматически в радиусе досягаемости.
 * Всё работает только на клиенте обычными кликами/командами, поэтому подходит и для серверов.
 */
public class SchematicTool {
    // ---------- Состояние ----------
    public static int mode = 1;
    private static BlockPos pos1, pos2;

    public static Schematic schem;
    public static BlockPos anchor;
    public static int rot = 0;
    private static String dim = "";

    /** Все непустые блоки размещённой схемы в мировых координатах. */
    private static long[] ePos = new long[0];
    private static BlockState[] eState = new BlockState[0];

    // результаты проверки мира (пересчитываются раз в 10 тиков)
    private static final List<BlockPos> rMissing = new ArrayList<>();
    private static final List<BlockPos> rWrong = new ArrayList<>();
    private static final List<BlockPos> rWrongState = new ArrayList<>();
    private static final List<BlockState> rMissingState = new ArrayList<>();
    private static final List<BlockState> rWrongWant = new ArrayList<>();
    private static final List<BlockState> rWrongStateWant = new ArrayList<>();
    /** Блоки рядом с игроком, которые надо поставить (для постройки). */
    private static final List<Integer> near = new ArrayList<>();
    private static int doneCount = 0, totalCount = 0;

    // постройка
    public static boolean builderOn = false;   // режим 4
    private static boolean creativeBuild = false; // режим 3 без команд
    private static int pendingIdx = -1;
    private static BlockHitResult pendingHit;
    private static int pendingSlot = -1;
    private static float pendingYaw, pendingPitch;
    private static int cooldown = 0;
    private static final Map<Long, Long> tried = new HashMap<>();
    public static final Set<Item> lacking = new LinkedHashSet<>();
    /** Что ставится сейчас (для значка на экране). */
    private static Item placingItem = null;

    // вставка командами
    private static final ArrayDeque<String> commands = new ArrayDeque<>();
    private static int commandsTotal = 0;

    // ввод
    private static boolean sneakWas = false, sneakUsed = false, comboWas = false;
    private static long sneakStart = 0;
    private static long ticks = 0;

    private static final int RENDER_RADIUS = 48;
    private static final int RENDER_LIMIT = 6000;
    private static final Path STATE_FILE = FabricLoader.getInstance().getConfigDir().resolve("universal_mod_schematic.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Saved {
        String file;
        int x, y, z, rot, mode = 1;
        String dim = "";
        boolean placed;
    }

    public static void register() {
        loadState();
        ClientTickEvents.START_CLIENT_TICK.register(SchematicTool::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(SchematicTool::renderWorld);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
                Identifier.of(UniversalMod.MOD_ID, "schematic"), (ctx, tc) -> renderHud(ctx));
    }

    public static boolean holdingTool(MinecraftClient mc) {
        return ClickerConfig.schemTool && mc.player != null && mc.player.getMainHandStack().isOf(Items.ARROW);
    }

    // ---------- Клавиши (назначаются в настройках) ----------

    public static void keyNextMode(MinecraftClient mc) {
        if (mc.player != null) nextMode(mc);
    }

    public static void keySave(MinecraftClient mc) {
        if (mc.player != null && mc.world != null) saveSelection(mc);
    }

    public static void keyRotate(MinecraftClient mc) {
        if (mc.player == null) return;
        if (schem == null) {
            say(mc, I18n.t("Схема не выбрана"));
            return;
        }
        rot = (rot + 1) % 4;
        rebuild();
        saveState();
        say(mc, I18n.t("Поворот: ") + rot * 90 + "°");
    }

    public static void keyMenu(MinecraftClient mc) {
        mc.setScreen(new SchematicScreen(mc.currentScreen));
    }

    /** В режиме 3 — вставить, иначе вкл/выкл автопостройку (режим 4). */
    public static void keyBuild(MinecraftClient mc) {
        if (mc.player == null) return;
        if (mode == 3) {
            if (mc.player.isCreative()) paste(mc);
            else say(mc, I18n.t("Режим 3 работает только в креативе"));
            return;
        }
        if (!placedHere(mc)) {
            say(mc, I18n.t("Сначала поставьте схему (режим 2)"));
            return;
        }
        if (mode != 4) {
            mode = 4;
            builderOn = true;
        } else {
            builderOn = !builderOn;
        }
        lacking.clear();
        saveState();
        say(mc, I18n.t("Автопостройка: ") + (builderOn ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ")) + I18n.t(" (держите стрелу)"));
    }

    private static boolean key(MinecraftClient mc, int k) {
        return GLFW.glfwGetKey(mc.getWindow().getHandle(), k) == GLFW.GLFW_PRESS;
    }

    private static boolean ctrl(MinecraftClient mc) {
        return key(mc, GLFW.GLFW_KEY_LEFT_CONTROL) || key(mc, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    private static void say(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), true);
    }

    private static void chat(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    // ---------- Тик ----------

    private static void tick(MinecraftClient mc) {
        ticks++;
        if (mc.player == null || mc.world == null) {
            builderOn = false;
            creativeBuild = false;
            pendingIdx = -1;
            commands.clear();
            return;
        }
        boolean free = mc.currentScreen == null;
        boolean holding = holdingTool(mc) && free;
        boolean sneak = mc.options.sneakKey.isPressed();
        boolean ctrl = free && ctrl(mc);

        int attacks = 0, uses = 0;
        if (holding) {
            while (mc.options.attackKey.wasPressed()) attacks++;
            while (mc.options.useKey.wasPressed()) uses++;
            // стрелой ничего не ломаем и не используем
            mc.options.attackKey.setPressed(false);
            mc.options.useKey.setPressed(false);
        }

        // короткое нажатие Shift — следующий режим
        if (sneak && !sneakWas) {
            sneakStart = ticks;
            sneakUsed = false;
        }
        if (sneak && (attacks > 0 || uses > 0 || ctrl)) sneakUsed = true;
        if (!sneak && sneakWas && holding && !sneakUsed && ticks - sneakStart <= 6) nextMode(mc);
        sneakWas = sneak;

        // Ctrl+Shift — сохранить выделение
        boolean combo = ctrl && sneak;
        if (combo && !comboWas && holding && mode == 1) saveSelection(mc);
        comboWas = combo;

        if (holding) {
            for (int i = 0; i < uses; i++) onUse(mc, sneak, ctrl);
            for (int i = 0; i < attacks; i++) onAttack(mc, ctrl);
        }

        if (anchor != null && schem != null && ticks % 10 == 0) {
            if (ticks % 100 == 0) lacking.clear(); // докупили/достали блоки — значок пропадёт
            refresh(mc);
        }

        // вставка командами идёт сама, пока очередь не опустеет
        if (!commands.isEmpty()) {
            for (int i = 0; i < 8 && !commands.isEmpty(); i++) mc.player.networkHandler.sendChatCommand(commands.poll());
            if (commands.isEmpty()) chat(mc, I18n.t("Схема вставлена командами: ") + commandsTotal);
        }

        boolean buildActive = holdingTool(mc) && free && placedHere(mc)
                && ((mode == 4 && builderOn) || (mode == 3 && creativeBuild && mc.player.isCreative()));
        if (buildActive) buildTick(mc);
        else pendingIdx = -1;
    }

    private static void nextMode(MinecraftClient mc) {
        mode = mode % 4 + 1;
        if (mode == 3 && !mc.player.isCreative()) mode = 4;
        builderOn = mode == 4;
        creativeBuild = false;
        pendingIdx = -1;
        lacking.clear();
        say(mc, modeHint());
        saveState();
    }

    public static String modeName(int m) {
        return switch (m) {
            case 1 -> I18n.t("1: Выделение");
            case 2 -> I18n.t("2: Размещение");
            case 3 -> I18n.t("3: Вставка (креатив)");
            default -> I18n.t("4: Постройка из инвентаря");
        };
    }

    private static String modeHint() {
        return switch (mode) {
            case 1 -> modeName(1) + " — " + I18n.t("ПКМ: точка 1, ЛКМ: точка 2, Ctrl+Shift: сохранить");
            case 2 -> modeName(2) + " — " + I18n.t("ПКМ: поставить, ЛКМ: другая схема, Ctrl+ПКМ: поворот, Ctrl+ЛКМ: убрать");
            case 3 -> modeName(3) + " — " + I18n.t("Shift+ПКМ: поставить все блоки");
            default -> modeName(4) + " — " + I18n.t("ПКМ: вкл/выкл автопостройку");
        };
    }

    private static BlockHitResult look(MinecraftClient mc) {
        HitResult h = mc.player.raycast(64.0, 1.0F, false);
        if (h instanceof BlockHitResult b && h.getType() == HitResult.Type.BLOCK) return b;
        return null;
    }

    private static String xyz(BlockPos p) {
        return p.getX() + " " + p.getY() + " " + p.getZ();
    }

    private static void onUse(MinecraftClient mc, boolean sneak, boolean ctrl) {
        switch (mode) {
            case 1 -> {
                BlockHitResult b = look(mc);
                pos1 = b != null ? b.getBlockPos() : mc.player.getBlockPos();
                dim = Waypoints.currentDim(mc);
                say(mc, I18n.t("Точка 1: ") + xyz(pos1) + selectionHint());
            }
            case 2 -> {
                if (schem == null && !cycleSchematic(mc)) return;
                if (ctrl) {
                    rot = (rot + 1) % 4;
                    rebuild();
                    say(mc, I18n.t("Поворот: ") + rot * 90 + "°");
                } else {
                    BlockHitResult b = look(mc);
                    anchor = b != null ? b.getBlockPos().offset(b.getSide()) : mc.player.getBlockPos();
                    dim = Waypoints.currentDim(mc);
                    rebuild();
                    say(mc, I18n.t("Схема «") + schem.name + I18n.t("» поставлена: ") + xyz(anchor));
                }
                saveState();
            }
            case 3 -> {
                if (!mc.player.isCreative()) {
                    say(mc, I18n.t("Режим 3 работает только в креативе"));
                } else if (!sneak) {
                    say(mc, I18n.t("Shift+ПКМ — поставить всю схему"));
                } else {
                    paste(mc);
                }
            }
            default -> {
                if (!placedHere(mc)) {
                    say(mc, I18n.t("Сначала поставьте схему (режим 2)"));
                    return;
                }
                builderOn = !builderOn;
                lacking.clear();
                say(mc, I18n.t("Автопостройка: ") + (builderOn ? I18n.t("ВКЛ") : I18n.t("ВЫКЛ")));
            }
        }
    }

    private static void onAttack(MinecraftClient mc, boolean ctrl) {
        switch (mode) {
            case 1 -> {
                BlockHitResult b = look(mc);
                pos2 = b != null ? b.getBlockPos() : mc.player.getBlockPos();
                dim = Waypoints.currentDim(mc);
                say(mc, I18n.t("Точка 2: ") + xyz(pos2) + selectionHint());
            }
            case 2 -> {
                if (ctrl) {
                    anchor = null;
                    rebuild();
                    say(mc, I18n.t("Схема убрана"));
                } else {
                    cycleSchematic(mc);
                }
                saveState();
            }
            case 3 -> {
                if (!commands.isEmpty() || creativeBuild) {
                    commands.clear();
                    creativeBuild = false;
                    say(mc, I18n.t("Вставка остановлена"));
                }
            }
            default -> { }
        }
    }

    private static String selectionHint() {
        if (pos1 == null || pos2 == null) return "";
        int x = Math.abs(pos1.getX() - pos2.getX()) + 1, y = Math.abs(pos1.getY() - pos2.getY()) + 1,
                z = Math.abs(pos1.getZ() - pos2.getZ()) + 1;
        return "  (" + x + "×" + y + "×" + z + ")  " + I18n.t("Ctrl+Shift — сохранить");
    }

    private static void saveSelection(MinecraftClient mc) {
        if (pos1 == null || pos2 == null) {
            say(mc, I18n.t("Поставьте обе точки: ПКМ и ЛКМ"));
            return;
        }
        long vol = (long) (Math.abs(pos1.getX() - pos2.getX()) + 1) * (Math.abs(pos1.getY() - pos2.getY()) + 1)
                * (Math.abs(pos1.getZ() - pos2.getZ()) + 1);
        if (vol > 4_000_000L) {
            say(mc, I18n.t("Слишком большая область (больше 4 млн блоков)"));
            return;
        }
        String name = "schem_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        Schematic s = Schematic.capture(mc.world, pos1, pos2, name);
        if (s.save()) {
            chat(mc, I18n.t("Схема сохранена: ") + name + " (" + s.sx + "×" + s.sy + "×" + s.sz + ")");
            schem = s;
            saveState();
        } else {
            chat(mc, I18n.t("Не удалось сохранить схему"));
        }
    }

    /** Следующая схема из папки. */
    private static boolean cycleSchematic(MinecraftClient mc) {
        List<Path> files = Schematic.list();
        if (files.isEmpty()) {
            say(mc, I18n.t("Нет сохранённых схем: выделите область в режиме 1"));
            return false;
        }
        int idx = 0;
        if (schem != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).equals(schem.file())) {
                    idx = (i + 1) % files.size();
                    break;
                }
            }
        }
        Schematic s = Schematic.load(files.get(idx));
        if (s == null) {
            say(mc, I18n.t("Не удалось прочитать ") + files.get(idx).getFileName());
            return false;
        }
        schem = s;
        rebuild();
        say(mc, I18n.t("Схема: ") + s.name + " (" + (idx + 1) + "/" + files.size() + ")");
        return true;
    }

    public static boolean placedHere(MinecraftClient mc) {
        return schem != null && anchor != null && dim.equals(Waypoints.currentDim(mc));
    }

    // ---------- Размещение ----------

    private static BlockRotation rotation() {
        return switch (rot) {
            case 1 -> BlockRotation.CLOCKWISE_90;
            case 2 -> BlockRotation.CLOCKWISE_180;
            case 3 -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private static BlockPos toWorld(int x, int y, int z) {
        int wx, wz;
        switch (rot) {
            case 1 -> { wx = schem.sz - 1 - z; wz = x; }
            case 2 -> { wx = schem.sx - 1 - x; wz = schem.sz - 1 - z; }
            case 3 -> { wx = z; wz = schem.sx - 1 - x; }
            default -> { wx = x; wz = z; }
        }
        return anchor.add(wx, y, wz);
    }

    /** Пересобрать список блоков схемы в мире (после смены схемы, места или поворота). */
    public static void rebuild() {
        pendingIdx = -1;
        tried.clear();
        rMissing.clear();
        rWrong.clear();
        rWrongState.clear();
        rMissingState.clear();
        rWrongWant.clear();
        rWrongStateWant.clear();
        near.clear();
        if (schem == null || anchor == null) {
            ePos = new long[0];
            eState = new BlockState[0];
            return;
        }
        BlockRotation r = rotation();
        BlockState[] rotated = new BlockState[schem.palette.size()];
        for (int i = 0; i < rotated.length; i++) rotated[i] = schem.palette.get(i).rotate(r);
        int n = 0;
        for (int b : schem.blocks) if (b != 0 && !rotated[b].isAir()) n++;
        ePos = new long[n];
        eState = new BlockState[n];
        int k = 0;
        for (int y = 0; y < schem.sy; y++) {
            for (int z = 0; z < schem.sz; z++) {
                for (int x = 0; x < schem.sx; x++) {
                    int b = schem.blocks[schem.index(x, y, z)];
                    if (b == 0 || rotated[b].isAir()) continue;
                    ePos[k] = toWorld(x, y, z).asLong();
                    eState[k] = rotated[b];
                    k++;
                }
            }
        }
        totalCount = n;
    }

    /** Верхняя половина двери/высокого растения, изголовье кровати — ставятся сами вместе с нижней частью. */
    public static boolean secondaryPart(BlockState st) {
        if (st.getProperties().contains(Properties.DOUBLE_BLOCK_HALF)
                && st.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return true;
        return st.getProperties().contains(Properties.BED_PART) && st.get(Properties.BED_PART) == BedPart.HEAD;
    }

    public static int entryCount() { return ePos.length; }
    public static BlockPos entryPos(int i) { return BlockPos.fromLong(ePos[i]); }
    public static BlockState entryState(int i) { return eState[i]; }

    private static boolean loaded(ClientWorld w, BlockPos p) {
        return w.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4);
    }

    private static void refresh(MinecraftClient mc) {
        rMissing.clear();
        rWrong.clear();
        rWrongState.clear();
        rMissingState.clear();
        rWrongWant.clear();
        rWrongStateWant.clear();
        near.clear();
        if (!placedHere(mc)) return;
        ClientWorld w = mc.world;
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getBlockInteractionRange() + 1.5;
        int done = 0;
        int shown = 0;
        for (int i = 0; i < ePos.length; i++) {
            BlockPos p = BlockPos.fromLong(ePos[i]);
            if (!loaded(w, p)) continue;
            BlockState want = eState[i];
            BlockState have = w.getBlockState(p);
            if (have.equals(want)) {
                done++;
                continue;
            }
            double d2 = eye.squaredDistanceTo(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            boolean visible = d2 < RENDER_RADIUS * RENDER_RADIUS && shown < RENDER_LIMIT;
            if (have.getBlock() == want.getBlock()) {
                if (visible) { rWrongState.add(p); rWrongStateWant.add(want); shown++; }
            } else if (have.isReplaceable()) {
                if (visible) { rMissing.add(p); rMissingState.add(want); shown++; }
                if (d2 < reach * reach && !secondaryPart(want)) near.add(i);
            } else {
                if (visible) { rWrong.add(p); rWrongWant.add(want); shown++; }
            }
        }
        doneCount = done;
        // снизу вверх, потом ближние
        near.sort((a, b) -> {
            BlockPos pa = BlockPos.fromLong(ePos[a]), pb = BlockPos.fromLong(ePos[b]);
            if (pa.getY() != pb.getY()) return Integer.compare(pa.getY(), pb.getY());
            return Double.compare(eye.squaredDistanceTo(pa.toCenterPos()), eye.squaredDistanceTo(pb.toCenterPos()));
        });
    }

    // ---------- Вставка в креативе ----------

    private static void paste(MinecraftClient mc) {
        if (!placedHere(mc)) {
            say(mc, I18n.t("Сначала поставьте схему (режим 2)"));
            return;
        }
        boolean cmds = false;
        try {
            var root = mc.player.networkHandler.getCommandDispatcher().getRoot();
            cmds = root.getChild("fill") != null && root.getChild("setblock") != null;
        } catch (Throwable ignored) {
        }
        if (!cmds) {
            creativeBuild = true;
            say(mc, I18n.t("Нет доступа к /fill — ставлю блоки вручную рядом с вами. ЛКМ — стоп"));
            return;
        }
        commands.clear();
        BlockRotation r = rotation();
        for (int y = 0; y < schem.sy; y++) {
            for (int z = 0; z < schem.sz; z++) {
                int x = 0;
                while (x < schem.sx) {
                    int b = schem.blocks[schem.index(x, y, z)];
                    int e = x;
                    while (e + 1 < schem.sx && schem.blocks[schem.index(e + 1, y, z)] == b) e++;
                    if (b != 0) {
                        String st = Schematic.toString(schem.palette.get(b).rotate(r));
                        BlockPos a = toWorld(x, y, z);
                        if (e == x) {
                            commands.add("setblock " + xyz(a) + " " + st);
                        } else {
                            commands.add("fill " + xyz(a) + " " + xyz(toWorld(e, y, z)) + " " + st);
                        }
                    }
                    x = e + 1;
                }
            }
        }
        commandsTotal = commands.size();
        say(mc, I18n.t("Вставка командами: ") + commandsTotal + I18n.t(". ЛКМ — стоп"));
    }

    // ---------- Постройка ----------

    private static void buildTick(MinecraftClient mc) {
        ClientPlayerEntity pl = mc.player;
        if (pl.currentScreenHandler != pl.playerScreenHandler) return;
        PlayerInventory inv = pl.getInventory();
        int toolSlot = inv.getSelectedSlot();

        if (pendingIdx >= 0) {
            int i = pendingIdx;
            pendingIdx = -1;
            BlockPos p = BlockPos.fromLong(ePos[i]);
            if (i < eState.length && mc.world.getBlockState(p).isReplaceable()) {
                pl.setYaw(pendingYaw);
                pl.setPitch(pendingPitch);
                if (pendingSlot != toolSlot) inv.setSelectedSlot(pendingSlot);
                ActionResult res = mc.interactionManager.interactBlock(pl, Hand.MAIN_HAND, pendingHit);
                if (res.isAccepted()) pl.swingHand(Hand.MAIN_HAND);
                if (pendingSlot != toolSlot) inv.setSelectedSlot(toolSlot);
            }
            cooldown = 1;
            if (tried.size() > 20000) tried.clear();
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        boolean creative = pl.isCreative();
        double range = pl.getBlockInteractionRange();
        Vec3d eye = pl.getEyePos();
        int checks = 0;
        for (int i : near) {
            if (++checks > 80) break;
            if (i >= eState.length) continue;
            BlockPos p = BlockPos.fromLong(ePos[i]);
            Long t = tried.get(ePos[i]);
            if (t != null && ticks - t < 40) continue;
            if (!mc.world.getBlockState(p).isReplaceable()) continue;
            BlockState want = eState[i];
            Item item = want.getBlock().asItem();
            if (item == Items.AIR) continue;
            if (!want.getCollisionShape(mc.world, p).isEmpty() && pl.getBoundingBox().intersects(new Box(p))) continue;

            int slot = -1;
            if (!creative) {
                slot = findItem(inv, item);
                if (slot < 0) {
                    lacking.add(item);
                    continue;
                }
            }
            ItemStack stack = creative ? new ItemStack(item) : inv.getStack(slot);
            Solution sol = solve(mc, p, want, stack, eye, range);
            if (sol == null) continue;

            // предмет — в хотбар
            int hot;
            if (creative) {
                hot = freeHotbar(inv, toolSlot);
                ItemStack give = new ItemStack(item, item.getMaxCount());
                inv.setStack(hot, give.copy());
                mc.interactionManager.clickCreativeStack(give, 36 + hot);
            } else if (slot < 9) {
                hot = slot;
            } else {
                hot = freeHotbar(inv, toolSlot);
                mc.interactionManager.clickSlot(pl.playerScreenHandler.syncId, slot, hot, SlotActionType.SWAP, pl);
            }

            tried.put(ePos[i], ticks);
            pendingIdx = i;
            pendingHit = sol.hit;
            pendingSlot = hot;
            pendingYaw = sol.yaw;
            pendingPitch = sol.pitch;
            placingItem = item;
            // наводим камеру; поворот уйдёт на сервер в этом тике, блок ставится в следующем
            pl.setYaw(sol.yaw);
            pl.setPitch(sol.pitch);
            return;
        }
    }

    private static int findItem(PlayerInventory inv, Item item) {
        for (int i = 0; i < 9; i++) if (inv.getStack(i).isOf(item)) return i;
        for (int i = 9; i < 36; i++) if (inv.getStack(i).isOf(item)) return i;
        return -1;
    }

    /** Слот хотбара для блока: пустой, иначе последний, кроме слота со стрелой. */
    private static int freeHotbar(PlayerInventory inv, int toolSlot) {
        for (int i = 8; i >= 0; i--) if (i != toolSlot && inv.getStack(i).isEmpty()) return i;
        return toolSlot == 8 ? 7 : 8;
    }

    private record Solution(BlockHitResult hit, float yaw, float pitch) { }

    private static final float[] YAWS = {0f, 90f, 180f, -90f};
    private static final float[] PITCHES = {30f, 75f, -75f};

    /**
     * Подбираем, по какой соседней грани кликнуть и куда смотреть, чтобы блок встал ровно как в схеме
     * (поворот, верх/низ, ось). Перебираем варианты и спрашиваем у самого блока, что получится.
     */
    private static Solution solve(MinecraftClient mc, BlockPos pos, BlockState want, ItemStack stack, Vec3d eye, double range) {
        ClientPlayerEntity pl = mc.player;
        Block block = want.getBlock();
        float oy = pl.getYaw(), op = pl.getPitch();
        Solution best = null;
        int bestScore = -1;
        try {
            outer:
            for (Direction d : Direction.values()) {
                BlockPos n = pos.offset(d);
                BlockState ns = mc.world.getBlockState(n);
                if (ns.isReplaceable() || interactive(ns)) continue;
                Direction face = d.getOpposite();
                double[] hys = d.getAxis().isHorizontal() ? new double[]{0.25, 0.75} : new double[]{0.5};
                for (double hy : hys) {
                    Vec3d c = Vec3d.ofCenter(n);
                    Vec3d hit = new Vec3d(c.x + face.getOffsetX() * 0.5,
                            d.getAxis().isHorizontal() ? n.getY() + hy : c.y + face.getOffsetY() * 0.5,
                            c.z + face.getOffsetZ() * 0.5);
                    if (eye.squaredDistanceTo(hit) > range * range) continue;
                    BlockHitResult bhr = new BlockHitResult(hit, face, n, false);
                    for (float yaw : YAWS) {
                        for (float pitch : PITCHES) {
                            pl.setYaw(yaw);
                            pl.setPitch(pitch);
                            ItemPlacementContext ctx = new ItemPlacementContext(pl, Hand.MAIN_HAND, stack, bhr);
                            if (!ctx.getBlockPos().equals(pos) || !ctx.canPlace()) continue;
                            BlockState got;
                            try {
                                got = block.getPlacementState(ctx);
                            } catch (Throwable t) {
                                got = null;
                            }
                            if (got == null) continue;
                            int score = got.equals(want) ? 1000 : score(got, want);
                            if (score > bestScore) {
                                bestScore = score;
                                best = new Solution(bhr, yaw, pitch);
                                if (score == 1000) break outer;
                            }
                        }
                    }
                }
            }
        } finally {
            pl.setYaw(oy);
            pl.setPitch(op);
        }
        return best;
    }

    private static int score(BlockState got, BlockState want) {
        if (got.getBlock() != want.getBlock()) return 0;
        int s = 1;
        for (Property<?> p : want.getProperties()) {
            if (got.getProperties().contains(p) && got.get(p).equals(want.get(p))) s++;
        }
        return s;
    }

    /** По таким блокам не кликаем: откроется окно или блок переключится. */
    private static boolean interactive(BlockState s) {
        Block b = s.getBlock();
        return s.hasBlockEntity() || b instanceof DoorBlock || b instanceof TrapdoorBlock || b instanceof FenceGateBlock
                || b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof CraftingTableBlock
                || b instanceof AnvilBlock || b instanceof BedBlock || b instanceof NoteBlock
                || b instanceof AbstractRedstoneGateBlock || b instanceof StonecutterBlock || b instanceof GrindstoneBlock
                || b instanceof LoomBlock || b instanceof CartographyTableBlock || b instanceof CakeBlock;
    }

    // ---------- Отрисовка ----------

    private static void renderWorld(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        boolean sel = holdingTool(mc) && mode == 1 && (pos1 != null || pos2 != null) && dim.equals(Waypoints.currentDim(mc));
        boolean placed = placedHere(mc);
        if (!sel && !placed) return;
        MatrixStack m = ctx.matrices();
        VertexConsumerProvider consumers = ctx.consumers();
        if (m == null || consumers == null) return;
        VertexConsumer vc = consumers.getBuffer(RenderLayers.lines());
        Vec3d cp = ContainerSearch.cameraPos(mc.gameRenderer.getCamera(), mc);
        float width = Math.max(2.0f, mc.getWindow().getFramebufferWidth() / 1920.0f * 2.0f);

        if (sel) {
            if (pos1 != null) box(m, vc, new Box(pos1).expand(0.02), cp, 0xFFFF5555, width);
            if (pos2 != null) box(m, vc, new Box(pos2).expand(0.02), cp, 0xFF5599FF, width);
            if (pos1 != null && pos2 != null) {
                box(m, vc, new Box(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()),
                        Math.min(pos1.getZ(), pos2.getZ()), Math.max(pos1.getX(), pos2.getX()) + 1,
                        Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1),
                        cp, 0xFFFFFF55, width);
            }
        }
        if (placed) {
            int ex = rot % 2 == 0 ? schem.sx : schem.sz, ez = rot % 2 == 0 ? schem.sz : schem.sx;
            box(m, vc, new Box(anchor.getX(), anchor.getY(), anchor.getZ(),
                    anchor.getX() + ex, anchor.getY() + schem.sy, anchor.getZ() + ez), cp, 0xFFFFFFFF, width);
            for (int i = 0; i < rMissing.size(); i++) {
                shape(m, vc, mc, rMissing.get(i), rMissingState.get(i), cp, 0xFF4FC3F7, width);
            }
            for (BlockPos p : rWrongState) box(m, vc, new Box(p).contract(0.03), cp, 0xFFFFA000, width);
            for (BlockPos p : rWrong) box(m, vc, new Box(p).contract(0.03), cp, 0xFFFF3030, width);

            // Сами блоки: уменьшенная копия нужного блока внутри клетки (как голограмма).
            // Рисуем строго после всех линий: запрос другого слоя закрывает буфер линий.
            if (!ghostBroken) {
                Vec3d eye = mc.player.getEyePos();
                double r2 = GHOST_RADIUS * GHOST_RADIUS;
                int drawn = 0;
                float bob = (float) Math.sin((ticks + mc.getRenderTickCounter().getTickProgress(false)) * 0.08) * 0.03f;
                for (int i = 0; i < rMissing.size() && drawn < GHOST_LIMIT; i++) {
                    BlockPos p = rMissing.get(i);
                    if (eye.squaredDistanceTo(p.toCenterPos()) > r2) continue;
                    ghost(m, consumers, mc, p, rMissingState.get(i), cp, 0.62f, bob);
                    drawn++;
                }
            }
        }
    }

    private static final int GHOST_RADIUS = 28;
    private static final int GHOST_LIMIT = 900;
    private static final int FULL_LIGHT = 0xF000F0;
    private static boolean ghostBroken = false;

    /** Модель блока размером size в центре клетки. */
    private static void ghost(MatrixStack m, VertexConsumerProvider vcp, MinecraftClient mc, BlockPos p, BlockState st,
                              Vec3d cp, float size, float bob) {
        if (size <= 0f) return;
        float off = (1f - size) / 2f;
        m.push();
        try {
            m.translate(p.getX() - cp.x + off, p.getY() - cp.y + off + bob, p.getZ() - cp.z + off);
            m.scale(size, size, size);
            mc.getBlockRenderManager().renderBlockAsEntity(st, m, vcp, FULL_LIGHT, OverlayTexture.DEFAULT_UV);
        } catch (Throwable t) {
            ghostBroken = true; // что-то не так с отрисовкой — дальше только рамки, без вылета игры
        } finally {
            m.pop();
        }
    }

    private static void box(MatrixStack m, VertexConsumer vc, Box b, Vec3d cp, int argb, float width) {
        VertexRendering.drawOutline(m, vc, VoxelShapes.cuboid(b), -cp.x, -cp.y, -cp.z, argb, width);
    }

    private static void shape(MatrixStack m, VertexConsumer vc, MinecraftClient mc, BlockPos p, BlockState st, Vec3d cp,
                              int argb, float width) {
        VoxelShape s;
        try {
            s = st.getOutlineShape(mc.world, p);
        } catch (Throwable t) {
            s = VoxelShapes.fullCube();
        }
        if (s.isEmpty()) s = VoxelShapes.cuboid(0.3, 0.0, 0.3, 0.7, 0.4, 0.7);
        VertexRendering.drawOutline(m, vc, s, p.getX() - cp.x, p.getY() - cp.y, p.getZ() - cp.z, argb, width);
    }

    private static final int[] MODE_COLORS = {0xFFF2C94C, 0xFF56A8F5, 0xFFB57BEE, 0xFF5BD38A};

    private static void renderHud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden || !holdingTool(mc)) return;
        var tr = mc.textRenderer;
        int accent = MODE_COLORS[mode - 1];
        int w = 240;
        int x = (ctx.getScaledWindowWidth() - w) / 2;
        int y = 4;

        // строки содержимого
        List<String> info = new ArrayList<>();
        String hint;
        if (mode == 1) {
            info.add(I18n.t("Точка 1: ") + (pos1 == null ? "—" : xyz(pos1)));
            info.add(I18n.t("Точка 2: ") + (pos2 == null ? "—" : xyz(pos2)));
            hint = pos1 != null && pos2 != null ? I18n.t("Ctrl+Shift — сохранить схему") : I18n.t("ПКМ — точка 1   ЛКМ — точка 2");
        } else if (mode == 2) {
            hint = I18n.t("ПКМ — сюда   ЛКМ — другая   Ctrl+ПКМ — поворот");
        } else if (mode == 3) {
            hint = I18n.t("Shift+ПКМ — поставить всё   ЛКМ — стоп");
        } else {
            hint = I18n.t("ПКМ — ") + (builderOn ? I18n.t("пауза") : I18n.t("строить"));
        }
        boolean showSchem = mode != 1;
        boolean showLack = mode == 4 && !lacking.isEmpty();
        boolean showPlacing = mode == 4 && builderOn && placingItem != null;
        int h = 18 + info.size() * 11 + (showSchem ? 24 : 0) + (showLack || showPlacing ? 22 : 0) + 12;

        // фон с рамкой и цветной полосой режима
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF101418);
        ctx.fill(x, y, x + w, y + h, 0xD01C2128);
        ctx.fill(x, y, x + w, y + 14, (accent & 0x00FFFFFF) | 0x55000000);
        ctx.fill(x, y + 14, x + w, y + 15, accent);
        ctx.drawItem(new ItemStack(Items.ARROW), x + 1, y - 1);
        ctx.drawTextWithShadow(tr, Text.literal(modeName(mode)), x + 19, y + 3, 0xFFFFFFFF);

        // значки режимов 1-4 справа
        for (int i = 1; i <= 4; i++) {
            int bx = x + w - 4 - (5 - i) * 13;
            boolean cur = i == mode;
            boolean off = i == 3 && !mc.player.isCreative();
            ctx.fill(bx, y + 2, bx + 11, y + 12, cur ? MODE_COLORS[i - 1] : 0x66000000);
            ctx.drawCenteredTextWithShadow(tr, Text.literal(String.valueOf(i)), bx + 6, y + 3,
                    cur ? 0xFF000000 : (off ? 0xFF555555 : 0xFFBBBBBB));
        }
        int cy = y + 19;
        for (String s : info) {
            ctx.drawTextWithShadow(tr, Text.literal(s), x + 6, cy, 0xFFDDDDDD);
            cy += 11;
        }
        if (showSchem) {
            if (schem == null) {
                ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Схема не выбрана — режим 2, ЛКМ")), x + 6, cy, 0xFFFFAA55);
            } else {
                String name = tr.trimToWidth(schem.name, w - 80);
                ctx.drawTextWithShadow(tr, Text.literal(name), x + 6, cy, 0xFFFFFFFF);
                String sz = schem.sx + "×" + schem.sy + "×" + schem.sz;
                ctx.drawTextWithShadow(tr, Text.literal(sz), x + w - 6 - tr.getWidth(sz), cy, 0xFF9AA4AE);
                int done = doneCount, total = Math.max(1, totalCount);
                boolean pasting = mode == 3 && !commands.isEmpty();
                if (pasting) {
                    done = commandsTotal - commands.size();
                    total = Math.max(1, commandsTotal);
                }
                bar(ctx, x + 6, cy + 12, w - 12, 7, done / (float) total, accent);
                String pct = (pasting ? I18n.t("вставка ") : "") + done + " / " + total;
                ctx.drawCenteredTextWithShadow(tr, Text.literal(pct), x + w / 2, cy + 11, 0xFFFFFFFF);
            }
            cy += 24;
        }
        if (showPlacing || showLack) {
            int ix = x + 6;
            if (showPlacing) {
                ctx.fill(ix - 1, cy - 1, ix + 17, cy + 17, accent);
                ctx.fill(ix, cy, ix + 16, cy + 16, 0xFF1C2128);
                ctx.drawItem(new ItemStack(placingItem), ix, cy);
                ix += 22;
            }
            if (showLack) {
                ctx.drawTextWithShadow(tr, Text.literal(I18n.t("Нет:")), ix, cy + 4, 0xFFFF6B6B);
                ix += tr.getWidth(I18n.t("Нет:")) + 4;
                for (Item it : lacking) {
                    if (ix > x + w - 20) break;
                    ctx.fill(ix - 1, cy - 1, ix + 17, cy + 17, 0xAAFF4040);
                    ctx.drawItem(new ItemStack(it), ix, cy);
                    ix += 19;
                }
            }
            cy += 22;
        }
        ctx.drawTextWithShadow(tr, Text.literal(tr.trimToWidth(hint, w - 12)), x + 6, cy, 0xFF9AA4AE);
    }

    /** Полоса прогресса с рамкой. */
    static void bar(DrawContext ctx, int x, int y, int w, int h, float f, int color) {
        f = Math.max(0f, Math.min(1f, f));
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0B0E11);
        ctx.fill(x, y, x + w, y + h, 0xFF2A313A);
        int fw = Math.round(w * f);
        if (fw > 0) {
            ctx.fill(x, y, x + fw, y + h, color);
            ctx.fill(x, y, x + fw, y + 1, 0x55FFFFFF);
        }
    }

    // ---------- Сохранение места схемы ----------

    public static void saveState() {
        try {
            Saved s = new Saved();
            s.file = schem == null ? null : schem.name;
            s.placed = anchor != null;
            if (anchor != null) {
                s.x = anchor.getX();
                s.y = anchor.getY();
                s.z = anchor.getZ();
            }
            s.rot = rot;
            s.dim = dim;
            s.mode = mode;
            try (Writer w = Files.newBufferedWriter(STATE_FILE)) {
                GSON.toJson(s, w);
            }
        } catch (Exception ignored) {
        }
    }

    private static void loadState() {
        if (!Files.exists(STATE_FILE)) return;
        try (Reader r = Files.newBufferedReader(STATE_FILE)) {
            Saved s = GSON.fromJson(r, Saved.class);
            if (s == null) return;
            mode = Math.max(1, Math.min(4, s.mode));
            rot = Math.floorMod(s.rot, 4);
            dim = s.dim == null ? "" : s.dim;
            if (s.file != null) schem = Schematic.load(Schematic.DIR.resolve(s.file + Schematic.EXT));
            if (schem != null && s.placed) anchor = new BlockPos(s.x, s.y, s.z);
            rebuild();
        } catch (Exception ignored) {
        }
    }

    /** После замены блоков в схеме: сохранить файл и обновить мир. */
    public static void schematicChanged() {
        if (schem == null) return;
        schem.save();
        rebuild();
    }
}
