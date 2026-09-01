package com.theo.wizardreal.particle;

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
}
