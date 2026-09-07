package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Farming magic (magic_eco 04, 农位): around the aimed point —
 * {@code harvest} breaks mature crops (drops + replant at age 0 when
 * {@code replant}), {@code grow} bonemeal-rushes everything growable.
 */
public record HarvestEffect(double radius, String mode, boolean replant) implements SpellEffect {
    private static final int BLOCK_CAP = 64;

    public static final MapCodec<HarvestEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("radius", 3.0).forGetter(HarvestEffect::radius),
                    Codec.STRING.optionalFieldOf("mode", "harvest").forGetter(HarvestEffect::mode),
                    Codec.BOOL.optionalFieldOf("replant", true).forGetter(HarvestEffect::replant)
            ).apply(instance, HarvestEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.HARVEST;
    }

    @Override
    public void apply(CastContext ctx) {
        ServerLevel level = (ServerLevel) ctx.caster().level();
        Vec3 center = EffectRegistryHelper.aimPoint(ctx, 8.0);
        int r = (int) Math.max(1, Math.round(EffectPower.linear(ctx, radius)));
        int budget = BLOCK_CAP;
        BlockPos base = BlockPos.containing(center.x, center.y, center.z);
        for (int dx = -r; dx <= r && budget > 0; dx++) {
            for (int dz = -r; dz <= r && budget > 0; dz++) {
                for (int dy = 1; dy >= -1 && budget > 0; dy--) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();
                    if ("grow".equals(mode)) {
                        if (block instanceof BonemealableBlock growable
                                && growable.isValidBonemealTarget(level, pos, state, false)) {
                            growable.performBonemeal(level, level.random, pos, state);
                            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    4, 0.3, 0.3, 0.3, 0.0);
                            budget--;
                        }
                    } else if (block instanceof CropBlock crop && crop.isMaxAge(state)) {
                        BlockState cropState = state;
                        level.destroyBlock(pos, true, ctx.caster(), 512);
                        if (replant) level.setBlock(pos, crop.getStateForAge(0), 3);
                        budget--;
                    }
                }
            }
        }
    }
}
