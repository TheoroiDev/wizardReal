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

/**
 * Perception toolbox (lux / spiritus / mentis / flatu): marks entities with
 * Glowing and grants the caster Night Vision. Vanillaeffect-based on purpose —
 * no custom entity, fully client-rendered.
 */
public record LightEffect(double glowRange, int glowDuration, int nightVisionSeconds) implements SpellEffect {
    public static final MapCodec<LightEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("glow_range", 0.0).forGetter(LightEffect::glowRange),
                    Codec.INT.optionalFieldOf("glow_duration", 100).forGetter(LightEffect::glowDuration),
                    Codec.INT.optionalFieldOf("night_vision_seconds", 0).forGetter(LightEffect::nightVisionSeconds)
            ).apply(instance, LightEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.LIGHT;
    }

    @Override
    public void apply(CastContext ctx) {
        if (glowRange > 0) {
            double reach = EffectPower.linear(ctx, glowRange);
            var targets = ctx.caster().level().getEntitiesOfClass(LivingEntity.class,
                    new AABB(ctx.origin(), ctx.origin()).inflate(reach),
                    e -> e != ctx.caster() && e.isAlive() && !e.is(ctx.caster()));
            int ticks = EffectPower.duration(ctx, glowDuration);
            for (LivingEntity target : targets) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            }
        }
        if (nightVisionSeconds > 0) {
            // vanilla flashes when < 10s remain — floor at 12s
            int ticks = Math.max(240, (int) (nightVisionSeconds * 20 * Math.max(1, ctx.power())));
            ctx.caster().addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0));
        }
    }
}
