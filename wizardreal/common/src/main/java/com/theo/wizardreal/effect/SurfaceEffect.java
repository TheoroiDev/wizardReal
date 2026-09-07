package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.server.TempBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Temporary ground cover (DOS2 表面系, 07 roadmap §4.1): a disc or a straight
 * line of blocks placed on the walkable surface around the aimed point — fire
 * trails, ice bridges, thorn patches. Only replaceable ground (air, grass,
 * snow layers…) is taken; solid terrain and builds stay untouched, and every
 * placement restores itself after {@code duration_ticks}.
 */
public record SurfaceEffect(Block block, String shape, double radius,
                            int durationTicks) implements SpellEffect {
    public static final MapCodec<SurfaceEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EffectRegistry.registryByName(net.minecraft.core.registries.BuiltInRegistries.BLOCK)
                            .fieldOf("block").forGetter(SurfaceEffect::block),
                    Codec.STRING.optionalFieldOf("shape", "circle").forGetter(SurfaceEffect::shape),
                    Codec.DOUBLE.optionalFieldOf("radius", 2.0).forGetter(SurfaceEffect::radius),
                    Codec.INT.optionalFieldOf("duration_ticks", 200).forGetter(SurfaceEffect::durationTicks)
            ).apply(instance, SurfaceEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.SURFACE;
    }

    @Override
    public void apply(CastContext ctx) {
        ServerLevel level = (ServerLevel) ctx.caster().level();
        Vec3 center = EffectRegistryHelper.aimPoint(ctx, 8.0);
        BlockState state = block.defaultBlockState();
        int duration = (int) EffectPower.linear(ctx, durationTicks);
        int placed = 0;
        if ("line".equals(shape)) {
            Vec3 dir = new Vec3(ctx.lookDir().x, 0, ctx.lookDir().z);
            if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
            dir = dir.normalize();
            int length = (int) Math.max(2, Math.round(EffectPower.linear(ctx, radius * 2)));
            for (int i = -length / 2; i <= length / 2 && placed < 64; i++) {
                Vec3 p = center.add(dir.scale(i));
                placed += placeColumn(level, BlockPos.containing(p.x, p.y, p.z), state, duration);
            }
        } else { // circle
            int r = (int) Math.max(1, Math.round(EffectPower.linear(ctx, radius)));
            for (int dx = -r; dx <= r && placed < 64; dx++) {
                for (int dz = -r; dz <= r && placed < 64; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    BlockPos pos = BlockPos.containing(center.x + dx, center.y, center.z + dz);
                    placed += placeColumn(level, pos, state, duration);
                }
            }
        }
    }

    /** Drop from the aim point's y to the top solid block and cover it. Returns blocks placed. */
    private static int placeColumn(ServerLevel level, BlockPos pos, BlockState state, int duration) {
        BlockPos cursor = pos;
        for (int i = 0; i < 4; i++) {
            BlockState below = level.getBlockState(cursor.below());
            BlockState at = level.getBlockState(cursor);
            if (below.isSolidRender(level, cursor.below()) && at.canBeReplaced()) {
                return TempBlockManager.place(level, cursor, state, duration) ? 1 : 0;
            }
            if (below.isSolidRender(level, cursor.below())) return 0;
            cursor = cursor.below();
        }
        return 0;
    }
}
