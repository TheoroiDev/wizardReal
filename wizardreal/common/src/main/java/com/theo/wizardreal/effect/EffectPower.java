package com.theo.wizardreal.effect;

import com.theo.wizardreal.api.CastContext;

/**
 * Power scaling for effect primitives (voice overhaul D3/D4): the chant tier /
 * learning multiplier arrives as {@link CastContext#power()} and every builtin
 * primitive consumes it here.
 *
 * <ul>
 *   <li>{@link #linear} — damage / amount / range-type parameters scale 1:1.</li>
 *   <li>{@link #duration} — status-type durations scale sub-linearly
 *       (power^0.5, D4 修正 2: 250% mastery must not mean permanent paralysis).</li>
 *   <li>{@link #amplifierBonus} — amplifier grows by at most +2
 *       (power &gt;= 1.25 → +1, power &gt;= 2.0 → +2).</li>
 * </ul>
 *
 * <p>Addons that ignore {@code ctx.power()} keep their old behavior — the API
 * contract is additive (javadoc on {@link SpellEffect}).
 */
public final class EffectPower {
    private EffectPower() {}

    public static float linear(CastContext ctx, float base) {
        return base * Math.max(0f, ctx.power());
    }

    public static double linear(CastContext ctx, double base) {
        return base * Math.max(0d, ctx.power());
    }

    public static int duration(CastContext ctx, int baseTicks) {
        return Math.max(0, Math.round(baseTicks * (float) Math.sqrt(Math.max(0d, ctx.power()))));
    }

    public static int amplifierBonus(CastContext ctx, int base) {
        float power = (float) Math.max(0d, ctx.power());
        int bonus = power >= 2.0f ? 2 : power >= 1.25f ? 1 : 0;
        return Math.min(base + bonus, base + 2);
    }

    public static int count(CastContext ctx, int base) {
        return Math.max(1, Math.round(base * Math.max(0f, ctx.power())));
    }
}
