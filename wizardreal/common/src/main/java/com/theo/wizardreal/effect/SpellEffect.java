package com.theo.wizardreal.effect;

import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;

/**
 * One composable spell effect. Implementations are immutable parameter
 * records; the per-cast mutable state lives in {@link CastContext#blackboard()}.
 *
 * <p>Types register their codec in {@link EffectRegistry} (built-in types via
 * {@link BuiltinEffects}, addons via the same path) and become addressable from
 * datapack JSON as {@code {"type": "<ns>:<id>", ...params}}.
 */
public interface SpellEffect {

    /** Stable effect-type id — the JSON {@code "type"} dispatch key. */
    ResourceLocation effectId();

    /** Execute on the server main thread. Must not throw (guard internally). */
    void apply(CastContext ctx);
}
