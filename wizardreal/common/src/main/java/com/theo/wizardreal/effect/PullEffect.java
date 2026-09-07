package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Pull entities (and dropped items) toward the caster — the vortex/magnet
 * family (gurges, gravitas, galvanum). Dragging hostile mobs into melee range
 * is the point; the pull is a velocity impulse, so heavier/anchored entities
 * resist naturally.
 */
public record PullEffect(double range, double strength, boolean includeItems) implements SpellEffect {
    public static final MapCodec<PullEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("range", 6.0).forGetter(PullEffect::range),
                    Codec.DOUBLE.optionalFieldOf("strength", 0.8).forGetter(PullEffect::strength),
                    Codec.BOOL.optionalFieldOf("include_items", true).forGetter(PullEffect::includeItems)
            ).apply(instance, PullEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.PULL;
    }

    @Override
    public void apply(CastContext ctx) {
        Vec3 center = ctx.caster().position().add(0, 1.0, 0);
        double reach = EffectPower.linear(ctx, range);
        double force = EffectPower.linear(ctx, strength);
        var targets = ctx.caster().level().getEntities(ctx.caster(),
                new AABB(center.x - reach, center.y - reach, center.z - reach,
                        center.x + reach, center.y + reach, center.z + reach),
                e -> e.isAlive() && !e.isSpectator()
                        && (includeItems || !(e instanceof ItemEntity)));
        for (Entity target : targets) {
            Vec3 drag = center.subtract(target.position().add(0, target.getBbHeight() / 2, 0));
            if (drag.lengthSqr() < 1.0E-4) continue;
            target.addDeltaMovement(drag.normalize().scale(force));
            target.hurtMarked = true; // sync velocity to clients
        }
    }
}
