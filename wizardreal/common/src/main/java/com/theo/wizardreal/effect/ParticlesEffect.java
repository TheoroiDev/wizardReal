package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Spawns particles around the caster (simple particles only). */
public record ParticlesEffect(ParticleOptions particle, int count, float spread,
                              float speed, float yOffset) implements SpellEffect {
    private static final Codec<ParticleOptions> SIMPLE_PARTICLE =
            EffectRegistry.registryByName(BuiltInRegistries.PARTICLE_TYPE)
                    .flatXmap(
                            pt -> pt instanceof ParticleOptions pe
                                    ? DataResult.success(pe)
                                    : DataResult.error(() -> "Particle " + pt + " requires extra options"),
                            pe -> DataResult.success((ParticleType<?>) pe.getType()));

    public static final MapCodec<ParticlesEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    SIMPLE_PARTICLE.fieldOf("particle").forGetter(ParticlesEffect::particle),
                    Codec.INT.optionalFieldOf("count", 10).forGetter(ParticlesEffect::count),
                    Codec.FLOAT.optionalFieldOf("spread", 0.5f).forGetter(ParticlesEffect::spread),
                    Codec.FLOAT.optionalFieldOf("speed", 0.1f).forGetter(ParticlesEffect::speed),
                    Codec.FLOAT.optionalFieldOf("y_offset", 1.0f).forGetter(ParticlesEffect::yOffset)
            ).apply(instance, ParticlesEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.PARTICLES;
    }

    @Override
    public void apply(CastContext ctx) {
        ServerLevel world = (ServerLevel) ctx.caster().level();
        world.sendParticles(particle,
                ctx.caster().getX(), ctx.caster().getY() + yOffset, ctx.caster().getZ(),
                count, spread, spread, spread, speed);
    }
}
