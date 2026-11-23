package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Лица мобов для карты: берём настоящую текстуру моба (с учётом его вида — тёплая/холодная корова,
 * разные жители и т.п.) и вырезаем из неё переднюю грань головы. Для мобов, которых нет в таблице, — null
 * (тогда рисуется яйцо призыва).
 */
public final class MobFaces {
    private MobFaces() { }

    /** u, v, ширина, высота грани лица и размер текстуры. */
    private record Face(int u, int v, int w, int h, int texW, int texH) { }

    private static final Map<EntityType<?>, Face> FACES = new HashMap<>();
    private static final Map<Integer, Identifier> TEX = new HashMap<>();
    private static long lastClear = 0;

    static {
        Face biped = new Face(8, 8, 8, 8, 64, 64);
        Face biped32 = new Face(8, 8, 8, 8, 64, 32);
        Face villager = new Face(8, 8, 8, 10, 64, 64);
        Face piglin = new Face(8, 8, 10, 8, 64, 64);
        put(biped, EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED);
        put(villager, EntityType.ZOMBIE_VILLAGER, EntityType.VILLAGER, EntityType.WANDERING_TRADER,
                EntityType.VINDICATOR, EntityType.EVOKER, EntityType.PILLAGER, EntityType.ILLUSIONER);
        put(biped32, EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.BOGGED,
                EntityType.CREEPER, EntityType.ENDERMAN, EntityType.BLAZE, EntityType.SLIME, EntityType.MAGMA_CUBE);
        put(piglin, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE, EntityType.ZOMBIFIED_PIGLIN);
        put(new Face(40, 12, 8, 8, 64, 32), EntityType.SPIDER, EntityType.CAVE_SPIDER);
        put(new Face(8, 8, 8, 10, 64, 128), EntityType.WITCH);
        put(new Face(6, 6, 8, 8, 64, 64), EntityType.COW, EntityType.MOOSHROOM);
        put(new Face(8, 8, 8, 8, 64, 64), EntityType.PIG);
        put(new Face(8, 8, 6, 6, 64, 32), EntityType.SHEEP);
        put(new Face(3, 3, 4, 6, 64, 32), EntityType.CHICKEN);
        put(new Face(4, 4, 6, 6, 64, 32), EntityType.WOLF);
        put(new Face(7, 11, 8, 6, 48, 32), EntityType.FOX);
        put(new Face(8, 8, 8, 10, 128, 128), EntityType.IRON_GOLEM);
        put(new Face(16, 16, 16, 16, 64, 32), EntityType.GHAST);
    }

    private static void put(Face f, EntityType<?>... types) {
        for (EntityType<?> t : types) FACES.put(t, f);
    }

    /** Текстура моба из его отрисовщика (кешируется на несколько секунд). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Identifier texture(Entity e) {
        long now = System.currentTimeMillis();
        if (now - lastClear > 5000) {
            TEX.clear();
            lastClear = now;
        }
        Identifier c = TEX.get(e.getId());
        if (c != null) return c;
        try {
            EntityRenderer r = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(e);
            if (!(r instanceof LivingEntityRenderer lr)) return null;
            Object state = r.getAndUpdateRenderState(e, 1.0f);
            Identifier id = lr.getTexture((LivingEntityRenderState) state);
            if (id != null) TEX.put(e.getId(), id);
            return id;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Нарисовать лицо моба в квадрате size×size. false — для этого моба лица нет. */
    public static boolean draw(DrawContext ctx, Entity e, int x, int y, int size) {
        Face f = FACES.get(e.getType());
        if (f == null) return false;
        Identifier tex = texture(e);
        if (tex == null) return false;
        int m = Math.max(f.w(), f.h());
        int dw = Math.max(1, size * f.w() / m), dh = Math.max(1, size * f.h() / m);
        int dx = x + (size - dw) / 2, dy = y + (size - dh) / 2;
        ctx.fill(dx - 1, dy - 1, dx + dw + 1, dy + dh + 1, 0xFF000000);
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, tex, dx, dy, f.u(), f.v(), dw, dh, f.w(), f.h(), f.texW(), f.texH());
        return true;
    }
}
