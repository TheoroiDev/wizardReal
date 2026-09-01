package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Applies a status effect to the caster. Self-target only in the basic set.
 */
public record StatusEffectEffect(MobEffect effect, int duration, int amplifier) implements SpellEffect {
    public static final MapCodec<StatusEffectEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EffectRegistry.registryByName(BuiltInRegistries.MOB_EFFECT).fieldOf("effect")
                            .forGetter(StatusEffectEffect::effect),
                    Codec.INT.optionalFieldOf("duration", 200).forGetter(StatusEffectEffect::duration),
                    Codec.INT.optionalFieldOf("amplifier", 0).forGetter(StatusEffectEffect::amplifier)
            ).apply(instance, StatusEffectEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.STATUS_EFFECT;
    }

    @Override
    public void apply(CastContext ctx) {
        ctx.caster().addEffect(
                new MobEffectInstance(effect, duration, amplifier, true, true, true));
    }
}
