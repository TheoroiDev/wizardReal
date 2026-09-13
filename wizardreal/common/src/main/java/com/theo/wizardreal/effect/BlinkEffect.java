package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Teleport along the look vector (07 roadmap §4.4): marches the caster's
 * bounding box forward in half-block steps and lands at the farthest safe
 * spot. Unsafe steps (suffocation, no floor rules ignored — vanilla suffocation
 * is the guard) shrink the distance; zero usable distance fizzles without
 * teleporting.
 */
public record BlinkEffect(double distance) implements SpellEffect {
    public static final MapCodec<BlinkEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("distance", 6.0).forGetter(BlinkEffect::distance)
            ).apply(instance, BlinkEffect::new));

    private static final double STEP = 0.5;

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.BLINK;
    }

    @Override
    public void apply(CastContext ctx) {
        Player caster = ctx.caster();
        ServerLevel level = (ServerLevel) caster.level();
        double reach = EffectPower.linear(ctx, distance);
        Vec3 start = caster.position();
        Vec3 safe = start;
        for (double t = STEP; t <= reach; t += STEP) {
            Vec3 candidate = start.add(ctx.lookDir().scale(t));
            if (!fits(level, caster, candidate)) {
                // blocked mid-path: try one block up (window ledges) before giving up
                Vec3 raised = safe.add(0, 1, 0);
                if (safe != start && fits(level, caster, raised)) safe = raised;
                break;
            }
            safe = candidate;
        }
        if (safe == start) return; // fizzle: no usable landing
        caster.teleportTo(safe.x, safe.y, safe.z);
        caster.fallDistance = 0;
    }

    /** Whether the caster's bounding box fits with feet at {@code pos}. */
    private static boolean fits(ServerLevel level, Player caster, Vec3 pos) {
        var box = caster.getBoundingBox().move(pos.subtract(caster.position()));
        return level.noCollision(caster, box);
    }
}
