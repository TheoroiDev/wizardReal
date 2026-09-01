package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Explosion at the caster's look point (raycast {@code range} blocks, or the
 * full range into the sky).
 */
public record ExplosionEffect(double range, float power, boolean setFire) implements SpellEffect {
    public static final MapCodec<ExplosionEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("range", 16.0).forGetter(ExplosionEffect::range),
                    Codec.FLOAT.optionalFieldOf("power", 4.0f).forGetter(ExplosionEffect::power),
                    Codec.BOOL.optionalFieldOf("set_fire", false).forGetter(ExplosionEffect::setFire)
            ).apply(instance, ExplosionEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.EXPLOSION;
    }

    @Override
    public void apply(CastContext ctx) {
        HitResult hit = ctx.caster().pick(range, 1.0f, false);
        Vec3 pos;
        if (hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
            pos = hit.getLocation();
        } else {
            pos = ctx.origin().add(ctx.lookDir().scale(range));
        }
        Level world = ctx.caster().level();
        world.explode(ctx.caster(), pos.x, pos.y, pos.z, power, setFire,
                Level.ExplosionInteraction.MOB);
    }
}
