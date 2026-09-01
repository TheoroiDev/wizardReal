package com.theo.wizardreal.effect;

import com.theo.wizardreal.WizardReal;
import net.minecraft.resources.ResourceLocation;

/**
 * Ids + registration for the built-in effect primitives. Addons register
 * their own types via {@link EffectRegistry#register} the same way.
 */
public final class BuiltinEffects {
    public static final ResourceLocation PROJECTILE = WizardReal.id("projectile");
    public static final ResourceLocation LIGHTNING = WizardReal.id("lightning");
    public static final ResourceLocation HEAL = WizardReal.id("heal");
    public static final ResourceLocation STATUS_EFFECT = WizardReal.id("status_effect");
    public static final ResourceLocation KNOCKBACK = WizardReal.id("knockback");
    public static final ResourceLocation EXPLOSION = WizardReal.id("explosion");
    public static final ResourceLocation BEAM = WizardReal.id("beam");
    public static final ResourceLocation SOUND = WizardReal.id("sound");
    public static final ResourceLocation PARTICLES = WizardReal.id("particles");

    private BuiltinEffects() {}

    /** Call once from mod init. */
    public static void register() {
        EffectRegistry.register(PROJECTILE, ProjectileEffect.CODEC);
        EffectRegistry.register(LIGHTNING, LightningEffect.CODEC);
        EffectRegistry.register(HEAL, HealEffect.CODEC);
        EffectRegistry.register(STATUS_EFFECT, StatusEffectEffect.CODEC);
        EffectRegistry.register(KNOCKBACK, KnockbackEffect.CODEC);
        EffectRegistry.register(EXPLOSION, ExplosionEffect.CODEC);
        EffectRegistry.register(BEAM, BeamEffect.CODEC);
        EffectRegistry.register(SOUND, SoundEffect.CODEC);
        EffectRegistry.register(PARTICLES, ParticlesEffect.CODEC);
    }
}
