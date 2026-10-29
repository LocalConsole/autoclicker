package com.example.universalmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;

import java.util.List;

/**
 * Замена «зельных» частиц у игроков и мобов с эффектом невидимости на более заметные ванильные частицы
 * (сердечки, огонь и т.д.). Выбор — ползунок в настройках; 0 = ничего не менять.
 */
public class InvisibleParticles {
    /** Названия вариантов для ползунка: 0 — стандартные частицы игры. */
    public static final String[] NAMES = {
            "Обычные", "Сердечки", "Радость", "Огонь", "Звёздочки", "Крит", "Ноты", "Гнев", "Души"
    };
    /** Сколько частиц ставим вместо одной зельной (чтобы было лучше видно). */
    public static final int COPIES = 2;

    private static ParticleEffect effectFor(int idx) {
        return switch (idx) {
            case 1 -> ParticleTypes.HEART;
            case 2 -> ParticleTypes.HAPPY_VILLAGER;
            case 3 -> ParticleTypes.FLAME;
            case 4 -> ParticleTypes.END_ROD;
            case 5 -> ParticleTypes.CRIT;
            case 6 -> ParticleTypes.NOTE;
            case 7 -> ParticleTypes.ANGRY_VILLAGER;
            case 8 -> ParticleTypes.SOUL_FIRE_FLAME;
            default -> null;
        };
    }

    /** У нот скорость по X — это цвет, поэтому для них задаём случайный оттенок. */
    public static double velX(ParticleEffect e) {
        return e == ParticleTypes.NOTE ? Math.random() : 0.0;
    }

    /** Чем заменить частицу, либо null — оставить как есть. */
    public static ParticleEffect replacement(ParticleEffect effect, double x, double y, double z) {
        int idx = ClickerConfig.invisParticle;
        if (idx <= 0) return null;
        if (effect.getType() != ParticleTypes.ENTITY_EFFECT) return null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;
        ParticleEffect r = effectFor(idx);
        if (r == null) return null;
        // Ищем рядом с частицей существо с невидимостью: игрока, а при включённой настройке и любого моба
        Box near = new Box(x - 3.0, y - 3.0, z - 3.0, x + 3.0, y + 3.0, z + 3.0);
        List<LivingEntity> list = client.world.getEntitiesByClass(LivingEntity.class, near,
                e -> e.hasStatusEffect(StatusEffects.INVISIBILITY)
                        && (ClickerConfig.invisMobs || e instanceof PlayerEntity));
        for (LivingEntity e : list) {
            if (e.getBoundingBox().expand(0.5).contains(x, y, z)) return r;
        }
        return null;
    }
}
