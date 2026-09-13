package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Enemy-directed status effect (magic_eco 04 §2): applies a mob effect to every
 * living target except the caster. Targeting is a look cone when
 * {@code angle_cos} is set (length {@code range}), otherwise a sphere of
 * {@code radius} (default {@code range}) centred on the caster — or on the
 * aimed point when {@code at_target}. The inverse of {@code status_effect}
 * (self-only), specced in the 07 roadmap §4.2.
 */
public record HexEffect(MobEffect effect, int amplifier, int duration, double range,
                        Double angleCos, Double radius, boolean atTarget,
                        String mobFilter) implements SpellEffect {
    public static final MapCodec<HexEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EffectRegistry.registryByName(BuiltInRegistries.MOB_EFFECT).fieldOf("effect")
                            .forGetter(HexEffect::effect),
                    Codec.INT.optionalFieldOf("amplifier", 0).forGetter(HexEffect::amplifier),
                    Codec.INT.optionalFieldOf("duration", 100).forGetter(HexEffect::duration),
                    Codec.DOUBLE.optionalFieldOf("range", 8.0).forGetter(HexEffect::range),
                    Codec.DOUBLE.optionalFieldOf("angle_cos").forGetter(h -> java.util.Optional.ofNullable(h.angleCos())),
                    Codec.DOUBLE.optionalFieldOf("radius").forGetter(h -> java.util.Optional.ofNullable(h.radius())),
                    Codec.BOOL.optionalFieldOf("at_target", false).forGetter(HexEffect::atTarget),
                    Codec.STRING.optionalFieldOf("mob_filter", "").forGetter(HexEffect::mobFilter)
            ).apply(instance, (effect, amplifier, duration, range, angleCos, radius, atTarget, mobFilter) ->
                    new HexEffect(effect, amplifier, duration, range,
                            angleCos.orElse(null), radius.orElse(null), atTarget, mobFilter)));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.HEX;
    }

    @Override
    public void apply(CastContext ctx) {
        boolean cone = angleCos != null;
        Vec3 center;
        double reach;
        if (cone) {
            center = ctx.origin();
            reach = EffectPower.linear(ctx, range);
        } else {
            reach = radius != null ? EffectPower.linear(ctx, radius) : EffectPower.linear(ctx, range);
            center = atTarget ? EffectRegistryHelper.aimPoint(ctx, reach)
                    : ctx.caster().position().add(0, 1.0, 0);
        }
        Vec3 look = ctx.lookDir();
        TagKey<EntityType<?>> filter;
        if (mobFilter != null && !mobFilter.isBlank()) {
            ResourceLocation filterId = ResourceLocation.tryParse(mobFilter);
            filter = filterId == null ? null : TagKey.create(Registries.ENTITY_TYPE, filterId);
        } else {
            filter = null;
        }
        var targets = ctx.caster().level().getEntitiesOfClass(LivingEntity.class,
                new AABB(center.x - reach, center.y - reach, center.z - reach,
                        center.x + reach, center.y + reach, center.z + reach),
                e -> e != ctx.caster() && e.isAlive() && (filter == null || e.getType().is(filter))
                        && (!cone
                            || e.position().add(0, e.getBbHeight() / 2, 0).subtract(center).normalize()
                                    .dot(look) >= angleCos));
        int ticks = EffectPower.duration(ctx, duration);
        int amp = EffectPower.amplifierBonus(ctx, amplifier);
        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(effect, ticks, amp));
        }
    }
}
