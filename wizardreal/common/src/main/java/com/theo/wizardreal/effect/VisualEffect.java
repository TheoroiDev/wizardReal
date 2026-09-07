package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.server.VisualPlayback;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Choreographed spell visuals (magic_eco 05) — the flashy layer. A shape
 * computes particle points and schedules them over {@code duration_ticks} via
 * {@link VisualPlayback}: static shapes fire once, helix/orbit/trail animate.
 * Particles are simple types or colored dust ({"dust": [r,g,b], "dust_scale": s}).
 */
public record VisualEffect(ParticleOptions particle, String shape, double radius, int count,
                           int durationTicks, double yOffset, boolean atPoint) implements SpellEffect {
    private static final int POINT_CAP = 120;

    private static final Codec<ParticleOptions> SIMPLE_PARTICLE =
            EffectRegistry.registryByName(BuiltInRegistries.PARTICLE_TYPE)
                    .flatXmap(
                            pt -> pt instanceof ParticleOptions pe
                                    ? DataResult.success(pe)
                                    : DataResult.error(() -> "Particle " + pt + " requires extra options"),
                            pe -> DataResult.success((ParticleType<?>) pe.getType()));

    private static final MapCodec<ParticleOptions> DUST = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.FLOAT.listOf().fieldOf("dust").forGetter(d -> List3.of(((DustParticleOptions) d).getColor())),
                    Codec.FLOAT.optionalFieldOf("dust_scale", 1.0f).forGetter(d -> ((DustParticleOptions) d).getScale())
            ).apply(instance, (color, scale) -> (ParticleOptions) new DustParticleOptions(
                    new Vector3f(color.get(0), color.get(1), color.get(2)), scale)));

    private static final Codec<ParticleOptions> VISUAL_PARTICLE =
            Codec.either(SIMPLE_PARTICLE, DUST.codec())
                    .xmap(e -> e.left().isPresent() ? e.left().get() : e.right().orElseThrow(),
                            p -> p instanceof DustParticleOptions dust
                                    ? com.mojang.datafixers.util.Either.right(dust)
                                    : com.mojang.datafixers.util.Either.left(p));

    public static final MapCodec<VisualEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    VISUAL_PARTICLE.fieldOf("particle").forGetter(VisualEffect::particle),
                    Codec.STRING.optionalFieldOf("shape", "ring").forGetter(VisualEffect::shape),
                    Codec.DOUBLE.optionalFieldOf("radius", 1.5).forGetter(VisualEffect::radius),
                    Codec.INT.optionalFieldOf("count", 30).forGetter(VisualEffect::count),
                    Codec.INT.optionalFieldOf("duration_ticks", 20).forGetter(VisualEffect::durationTicks),
                    Codec.DOUBLE.optionalFieldOf("y_offset", 1.0).forGetter(VisualEffect::yOffset),
                    Codec.BOOL.optionalFieldOf("at_point", false).forGetter(VisualEffect::atPoint)
            ).apply(instance, VisualEffect::new));

    private static final class List3 {
        static java.util.List<Float> of(Vector3f v) {
            return java.util.List.of(v.x(), v.y(), v.z());
        }
    }

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.VISUAL;
    }

    @Override
    public void apply(CastContext ctx) {
        Vec3 center = atPoint
                ? EffectRegistryHelper.aimPoint(ctx, 8.0).add(0, yOffset, 0)
                : ctx.caster().position().add(0, yOffset, 0);
        Vec3 look = ctx.lookDir();
        RandomSource random = ctx.caster().getRandom();
        int points = Math.min(Math.max(4, count), POINT_CAP);
        int duration = Math.max(1, durationTicks);
        Vec3 eye = ctx.origin();
        switch (shape) {
            case "helix" -> {
                double turns = 2.5, height = radius * 2.5;
                for (int i = 0; i < points; i++) {
                    double t = (double) i / points;
                    double angle = t * turns * Math.PI * 2;
                    double r = radius * (1.0 - t * 0.4);
                    Vec3 p = center.add(Math.cos(angle) * r, t * height, Math.sin(angle) * r);
                    VisualPlayback.schedule((net.minecraft.server.level.ServerLevel) ctx.caster().level(),
                            (int) (t * duration), particle, p, 1, 0, 0, 0, 0);
                }
            }
            case "pillar" -> {
                int steps = Math.min(points, 24);
                for (int i = 0; i < steps; i++) {
                    Vec3 p = center.add(0, (double) i / steps * radius * 2.5, 0);
                    VisualPlayback.now(level(ctx), particle, p, 2, 0.1, 0.05, 0.1, 0.01f);
                }
            }
            case "burst" -> {
                for (int i = 0; i < points; i++) {
                    Vec3 dir = new Vec3(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f,
                            random.nextFloat() - 0.5f).normalize();
                    VisualPlayback.now(level(ctx), particle, center.add(dir.scale(radius * random.nextDouble())),
                            1, dir.x * 0.1, dir.y * 0.1, dir.z * 0.1, 0.02f);
                }
            }
            case "trail" -> {
                Vec3 end = EffectRegistryHelper.aimPoint(ctx, Math.max(4, radius * 4));
                for (int i = 0; i < points; i++) {
                    double t = (double) i / points;
                    Vec3 p = eye.add(end.subtract(eye).scale(t));
                    VisualPlayback.schedule(level(ctx), (int) (t * duration), particle, p,
                            1, 0, 0, 0, 0);
                }
            }
            case "cone" -> {
                double reach = Math.max(2, radius * 4);
                for (int i = 1; i <= points; i++) {
                    double t = (double) i / points;
                    Vec3 p = eye.add(look.scale(t * reach))
                            .add(perp(look).scale(radius * t * (random.nextDouble() - 0.5) * 2));
                    VisualPlayback.schedule(level(ctx), (int) (t * duration), particle, p,
                            1, 0, 0, 0, 0);
                }
            }
            case "cross" -> {
                Vec3 right = perp(look);
                for (int i = -points / 2; i <= points / 2; i++) {
                    double t = (double) i / points * radius;
                    VisualPlayback.now(level(ctx), particle, center.add(right.scale(t)), 1, 0, 0, 0, 0);
                    VisualPlayback.now(level(ctx), particle, center.add(look.cross(right).scale(t)),
                            1, 0, 0, 0, 0);
                }
            }
            case "orbit" -> {
                for (int i = 0; i < points; i++) {
                    double t = (double) i / points;
                    double angle = t * Math.PI * 2;
                    Vec3 p = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    VisualPlayback.schedule(level(ctx), (int) (t * duration), particle, p,
                            1, 0, 0, 0, 0);
                }
            }
            default -> { // ring: one-shot magic circle
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2 / points) * i;
                    Vec3 p = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                    VisualPlayback.now(level(ctx), particle, p, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    private static net.minecraft.server.level.ServerLevel level(CastContext ctx) {
        return (net.minecraft.server.level.ServerLevel) ctx.caster().level();
    }

    /** A horizontal vector perpendicular to {@code v}. */
    private static Vec3 perp(Vec3 v) {
        Vec3 horizontal = new Vec3(-v.z, 0, v.x);
        return horizontal.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : horizontal.normalize();
    }
}
