package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * A cone of force in front of the caster that shoves entities away.
 */
public record KnockbackEffect(double range, double angleCos, double power) implements SpellEffect {
    public static final MapCodec<KnockbackEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("range", 6.0).forGetter(KnockbackEffect::range),
                    Codec.DOUBLE.optionalFieldOf("angle_cos", 0.5).forGetter(KnockbackEffect::angleCos),
                    Codec.DOUBLE.optionalFieldOf("power", 1.1).forGetter(KnockbackEffect::power)
            ).apply(instance, KnockbackEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.KNOCKBACK;
    }

    @Override
    public void apply(CastContext ctx) {
        Vec3 origin = ctx.origin();
        Vec3 look = ctx.lookDir();
        List<Entity> targets = ctx.caster().level().getEntities(
                ctx.caster(),
                ctx.caster().getBoundingBox().inflate(range),
                e -> e.isAlive() && e.distanceToSqr(ctx.caster()) <= range * range);
        for (Entity e : targets) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin).normalize();
            if (to.dot(look) < angleCos) continue;
            double p = power * ctx.power() + 0.6;
            e.push(look.x * p, 0.35 + look.y * p * 0.4, look.z * p);
            e.hurtMarked = true;
        }
    }
}
