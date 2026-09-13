package com.theo.wizardreal.effect;

import com.theo.wizardreal.api.CastContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared targeting helpers for the built-in effect primitives. */
public final class EffectRegistryHelper {

    private EffectRegistryHelper() {}

    /** The caster's aimed position: the raycast block hit within {@code range},
     *  or the full-range point along the look vector (same rule as the
     *  {@code explosion} primitive). */
    public static Vec3 aimPoint(CastContext ctx, double range) {
        double reach = EffectPower.linear(ctx, range);
        HitResult hit = ctx.caster().pick(reach, 1.0f, false);
        if (hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return ctx.origin().add(ctx.lookDir().scale(reach));
    }
}
