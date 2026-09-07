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
    // magic_eco 04 §2 (0.4.x 法术生态扩军)
    public static final ResourceLocation HEX = WizardReal.id("hex");
    public static final ResourceLocation BIND = WizardReal.id("bind");
    public static final ResourceLocation PULL = WizardReal.id("pull");
    public static final ResourceLocation BLINK = WizardReal.id("blink");
    public static final ResourceLocation SURFACE = WizardReal.id("surface");
    public static final ResourceLocation BARRIER = WizardReal.id("barrier");
    public static final ResourceLocation SUMMON = WizardReal.id("summon");
    public static final ResourceLocation EXCAVATE = WizardReal.id("excavate");
    public static final ResourceLocation HARVEST = WizardReal.id("harvest");
    public static final ResourceLocation SMELT = WizardReal.id("smelt");
    public static final ResourceLocation VISUAL = WizardReal.id("visual");
    public static final ResourceLocation WEATHER = WizardReal.id("weather");
    public static final ResourceLocation LIGHT = WizardReal.id("light");

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
        EffectRegistry.register(HEX, HexEffect.CODEC);
        EffectRegistry.register(BIND, BindEffect.CODEC);
        EffectRegistry.register(PULL, PullEffect.CODEC);
        EffectRegistry.register(BLINK, BlinkEffect.CODEC);
        EffectRegistry.register(SURFACE, SurfaceEffect.CODEC);
        EffectRegistry.register(BARRIER, BarrierEffect.CODEC);
        EffectRegistry.register(SUMMON, SummonEffect.CODEC);
        EffectRegistry.register(EXCAVATE, ExcavateEffect.CODEC);
        EffectRegistry.register(HARVEST, HarvestEffect.CODEC);
        EffectRegistry.register(SMELT, SmeltEffect.CODEC);
        EffectRegistry.register(VISUAL, VisualEffect.CODEC);
        EffectRegistry.register(WEATHER, WeatherEffect.CODEC);
        EffectRegistry.register(LIGHT, LightEffect.CODEC);
    }
}
