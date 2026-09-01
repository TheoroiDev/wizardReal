package com.theo.wizardreal.particle;

import net.minecraft.core.particles.SimpleParticleType;

/**
 * Public-constructor stand-in for {@code DefaultParticleType} (its ctor is
 * protected in vanilla/Yarn; Forge's access transformer only helps the Forge
 * subproject). Subclassing keeps the vanilla network deserializer intact:
 * {@code fromNetwork} returns the registered instance itself, so identity and
 * client factory lookup are preserved.
 */
public class WizardSimpleParticle extends SimpleParticleType {

    public WizardSimpleParticle() {
        super(false);
    }
}
