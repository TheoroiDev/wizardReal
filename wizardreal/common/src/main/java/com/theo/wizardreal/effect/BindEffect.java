package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Single-target movement lock (07 roadmap §4.2.1): the nearest living entity
 * in the look cone is rooted (speed 0 + jump lock). {@code group} extends the
 * lock to every cone target (禁咒 vincula). Boss-grade targets (max health
 * ≥ 100) are immune — the {@code #wizardreal:bind_immune} tag work lands with
 * the tag registry pass; the health heuristic covers the vanilla bosses now.
 */
public record BindEffect(int duration, double range, double angleCos, boolean group) implements SpellEffect {
    private static final double BOSS_HEALTH_IMMUNITY = 100.0;

    public static final MapCodec<BindEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("duration", 60).forGetter(BindEffect::duration),
                    Codec.DOUBLE.optionalFieldOf("range", 16.0).forGetter(BindEffect::range),
                    Codec.DOUBLE.optionalFieldOf("angle_cos", 0.75).forGetter(BindEffect::angleCos),
                    Codec.BOOL.optionalFieldOf("group", false).forGetter(BindEffect::group)
            ).apply(instance, BindEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.BIND;
    }

    @Override
    public void apply(CastContext ctx) {
        Vec3 eye = ctx.origin();
        double reach = EffectPower.linear(ctx, range);
        List<LivingEntity> targets = ctx.caster().level().getEntitiesOfClass(LivingEntity.class,
                new AABB(eye.x - reach, eye.y - reach, eye.z - reach,
                        eye.x + reach, eye.y + reach, eye.z + reach),
                e -> e != ctx.caster() && e.isAlive() && e.getMaxHealth() < BOSS_HEALTH_IMMUNITY
                        && e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye).normalize()
                                .dot(ctx.lookDir()) >= angleCos);
        if (targets.isEmpty()) return;
        int ticks = EffectPower.duration(ctx, duration);
        if (!group) {
            targets.sort(Comparator.comparingDouble(t -> t.distanceToSqr(eye)));
            targets = List.of(targets.get(0));
        }
        for (LivingEntity target : targets) {
            // speed 0 + jump lock (amplifier 250+ cancels the jump impulse)
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6));
            target.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 250));
        }
    }
}
