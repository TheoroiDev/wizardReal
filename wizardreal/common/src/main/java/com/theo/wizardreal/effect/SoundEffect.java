package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Plays a sound at the caster's position. */
public record SoundEffect(SoundEvent sound, float volume, float pitch) implements SpellEffect {
    public static final MapCodec<SoundEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EffectRegistry.registryByName(BuiltInRegistries.SOUND_EVENT).fieldOf("sound")
                            .forGetter(SoundEffect::sound),
                    Codec.FLOAT.optionalFieldOf("volume", 1.0f).forGetter(SoundEffect::volume),
                    Codec.FLOAT.optionalFieldOf("pitch", 1.0f).forGetter(SoundEffect::pitch)
            ).apply(instance, SoundEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.SOUND;
    }

    @Override
    public void apply(CastContext ctx) {
        ctx.caster().level().playSound(null, ctx.caster().blockPosition(),
                sound, SoundSource.PLAYERS, volume, pitch);
    }
}
