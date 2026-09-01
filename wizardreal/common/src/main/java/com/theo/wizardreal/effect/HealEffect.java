package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;

/** Instantly heals the caster. */
public record HealEffect(float amount) implements SpellEffect {
    public static final MapCodec<HealEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("amount").forGetter(HealEffect::amount)
            ).apply(instance, HealEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.HEAL;
    }

    @Override
    public void apply(CastContext ctx) {
        ctx.caster().heal(amount);
    }
}
