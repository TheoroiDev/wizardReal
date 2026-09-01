package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Calls a lightning bolt down where the caster is looking. */
public record LightningEffect(double range) implements SpellEffect {
    public static final MapCodec<LightningEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("range", 48.0).forGetter(LightningEffect::range)
            ).apply(instance, LightningEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.LIGHTNING;
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
        ServerLevel world = (ServerLevel) ctx.caster().level();
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, world);
        bolt.setPos(pos.x, pos.y, pos.z);
        world.addFreshEntity(bolt);
        BlockPos bp = BlockPos.containing(pos);
        world.playSound(null, bp.getX(), bp.getY(), bp.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 1.0f, 1.0f);
    }
}
