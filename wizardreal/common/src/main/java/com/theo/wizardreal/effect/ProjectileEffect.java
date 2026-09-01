package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns {@code count} copies of an entity at the caster's eyes and launches
 * them along the look vector (with optional random spread). Projectile-type
 * entities are attributed to the caster.
 */
public record ProjectileEffect(EntityType<?> entity, float speed, int count, float spread) implements SpellEffect {
    public static final MapCodec<ProjectileEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EffectRegistry.registryByName(BuiltInRegistries.ENTITY_TYPE).fieldOf("entity")
                            .forGetter(ProjectileEffect::entity),
                    Codec.FLOAT.optionalFieldOf("speed", 1.5f).forGetter(ProjectileEffect::speed),
                    Codec.INT.optionalFieldOf("count", 1).forGetter(ProjectileEffect::count),
                    Codec.FLOAT.optionalFieldOf("spread", 0.0f).forGetter(ProjectileEffect::spread)
            ).apply(instance, ProjectileEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.PROJECTILE;
    }

    @Override
    public void apply(CastContext ctx) {
        Level world = ctx.caster().level();
        Vec3 eye = ctx.origin();
        Vec3 look = ctx.lookDir();
        RandomSource random = ctx.caster().getRandom();
        for (int i = 0; i < Math.max(1, count); i++) {
            Vec3 dir = look;
            if (spread > 0) {
                dir = look.add(
                        (random.nextFloat() - 0.5f) * spread,
                        (random.nextFloat() - 0.5f) * spread,
                        (random.nextFloat() - 0.5f) * spread).normalize();
            }
            Entity e = entity.create(world);
            if (e == null) {
                WizardReal.LOGGER.warn("Effect {} could not create entity {}", effectId(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity));
                return;
            }
            e.moveTo(
                    eye.x + look.x * 0.8, eye.y, eye.z + look.z * 0.8,
                    ctx.caster().getYRot(), ctx.caster().getXRot());
            e.setDeltaMovement(dir.scale(speed));
            if (e instanceof Projectile projectile) {
                projectile.setOwner(ctx.caster());
            }
            world.addFreshEntity(e);
        }
    }
}
