package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A straight beam from the caster's eyes along the look vector. Damages and
 * optionally ignites every living entity intersecting the beam corridor.
 * Does not break blocks.
 */
public record BeamEffect(double range, double width, float damage, boolean setFire,
                         float fireSeconds) implements SpellEffect {
    public static final MapCodec<BeamEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("range", 30.0).forGetter(BeamEffect::range),
                    Codec.DOUBLE.optionalFieldOf("width", 1.0).forGetter(BeamEffect::width),
                    Codec.FLOAT.optionalFieldOf("damage", 8.0f).forGetter(BeamEffect::damage),
                    Codec.BOOL.optionalFieldOf("set_fire", false).forGetter(BeamEffect::setFire),
                    Codec.FLOAT.optionalFieldOf("fire_seconds", 3.0f).forGetter(BeamEffect::fireSeconds)
            ).apply(instance, BeamEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.BEAM;
    }

    @Override
    public void apply(CastContext ctx) {
        Vec3 from = ctx.origin();
        Vec3 to = from.add(ctx.lookDir().scale(range));
        // Corridor approximation: entities intersecting the segment's box
        // (expanded by half the beam width). Good enough for the basic set.
        AABB corridor = new AABB(from, to).inflate(width * 0.5);
        ServerLevel world = (ServerLevel) ctx.caster().level();
        List<net.minecraft.world.entity.Entity> targets = world.getEntities(
                ctx.caster(), corridor, e -> e.isAlive());
        for (net.minecraft.world.entity.Entity e : targets) {
            e.hurt(world.damageSources().playerAttack(ctx.caster()), damage);
            if (setFire) {
                e.setSecondsOnFire((int) fireSeconds);
            }
        }
    }
}
