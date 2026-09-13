package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Mining magic (magic_eco 04, 挖矿位): {@code tunnel} bores a walkable corridor
 * along the look vector, {@code area} scoops a sphere around the aimed point.
 * Drops land in-world (player-attributed breakage); unbreakable blocks and
 * liquids are skipped; a hard per-cast cap bounds the work.
 */
public record ExcavateEffect(String shape, double length, double radius) implements SpellEffect {
    private static final int BLOCK_CAP = 256;

    public static final MapCodec<ExcavateEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.STRING.optionalFieldOf("shape", "tunnel").forGetter(ExcavateEffect::shape),
                    Codec.DOUBLE.optionalFieldOf("length", 8.0).forGetter(ExcavateEffect::length),
                    Codec.DOUBLE.optionalFieldOf("radius", 1.0).forGetter(ExcavateEffect::radius)
            ).apply(instance, ExcavateEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.EXCAVATE;
    }

    @Override
    public void apply(CastContext ctx) {
        ServerLevel level = (ServerLevel) ctx.caster().level();
        int[] budget = {BLOCK_CAP};
        if ("area".equals(shape)) {
            Vec3 center = EffectRegistryHelper.aimPoint(ctx, 8.0);
            int r = (int) Math.max(1, Math.round(EffectPower.linear(ctx, radius)));
            for (int dx = -r; dx <= r && budget[0] > 0; dx++) {
                for (int dy = -r; dy <= r && budget[0] > 0; dy++) {
                    for (int dz = -r; dz <= r && budget[0] > 0; dz++) {
                        if (dx * dx + dy * dy + dz * dz > r * r) continue;
                        budget[0] -= breakBlock(level,
                                BlockPos.containing(center.x + dx, center.y + dy, center.z + dz), ctx);
                    }
                }
            }
        } else { // tunnel: 2-high corridor along the look vector
            Vec3 feet = ctx.caster().position();
            Vec3 dir = new Vec3(ctx.lookDir().x, 0, ctx.lookDir().z);
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
            dir = dir.normalize();
            int len = (int) Math.max(2, Math.round(EffectPower.linear(ctx, length)));
            int r = (int) Math.max(0, Math.round(radius));
            BlockPos start = BlockPos.containing(feet.x, feet.y, feet.z);
            for (int t = 1; t <= len && budget[0] > 0; t++) {
                for (int dx = -r; dx <= r && budget[0] > 0; dx++) {
                    for (int dz = -r; dz <= r && budget[0] > 0; dz++) {
                        BlockPos column = start.offset(
                                (int) Math.round(dir.x * t) + dx, 0,
                                (int) Math.round(dir.z * t) + dz);
                        budget[0] -= breakBlock(level, column, ctx);
                        budget[0] -= breakBlock(level, column.above(), ctx);
                    }
                }
            }
        }
    }

    private static int breakBlock(ServerLevel level, BlockPos pos, CastContext ctx) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) return 0;
        if (state.getDestroySpeed(level, pos) < 0) return 0; // bedrock & friends
        level.destroyBlock(pos, true, ctx.caster(), 512);
        return 1;
    }
}
