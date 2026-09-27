package com.theo.wizardreal.particle;

import com.theo.wizardreal.api.School;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;

/**
 * Central particle-type registry for WizardReal.
 *
 * <p>Fields are populated by platform-specific initialisation
 * ({@code Registry.register} on Fabric, {@code DeferredRegister} on Forge),
 * mirroring {@link com.theo.wizardreal.item.WizardRealItems}. The types are
 * optionless {@code SimpleParticleType}s, so datapack spell JSON can
 * reference them directly as {@code "wizardreal:spark"} / {@code "wizardreal:rune"}.
 */
public final class WizardRealParticles {

    private WizardRealParticles() {}

    public static Supplier<ParticleType<?>> SPARK;
    public static Supplier<ParticleType<?>> RUNE;

    /** Tiny mote used for held-staff idle ambience (client-side cosmetics). */
    public static Supplier<ParticleType<?>> WISP;

    /** Per-school spark particles (wizardReal#44 粒子词汇表): one optionless
     *  type per school, its sprite palette-locked (spark_<school>.png).
     *  Populated by the loader inits alongside SPARK/RUNE/WISP. */
    public static final Map<School, Supplier<ParticleType<?>>> SCHOOL_SPARKS =
            new EnumMap<>(School.class);
}
