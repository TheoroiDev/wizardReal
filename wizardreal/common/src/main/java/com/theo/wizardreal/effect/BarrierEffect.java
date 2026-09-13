package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.server.TempBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Temporary structure (07 roadmap §4.3): {@code wall} = a width×height plane
 * perpendicular to the look direction at the aimed point, {@code ring} = a
 * horizontal circle of pillars, {@code cage} = a spherical shell around the
 * aimed point. Expiring via {@link TempBlockManager} (到期复原，不掉落).
 */
public record BarrierEffect(Block block, String shape, int width, int height, double radius,
                            int durationTicks) implements SpellEffect {
    public static final MapCodec<BarrierEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EffectRegistry.registryByName(BuiltInRegistries.BLOCK)
                            .fieldOf("block").forGetter(BarrierEffect::block),
                    Codec.STRING.optionalFieldOf("shape", "wall").forGetter(BarrierEffect::shape),
                    Codec.INT.optionalFieldOf("width", 3).forGetter(BarrierEffect::width),
                    Codec.INT.optionalFieldOf("height", 3).forGetter(BarrierEffect::height),
                    Codec.DOUBLE.optionalFieldOf("radius", 2.0).forGetter(BarrierEffect::radius),
                    Codec.INT.optionalFieldOf("duration_ticks", 200).forGetter(BarrierEffect::durationTicks)
            ).apply(instance, BarrierEffect::new));

    private static final int BLOCK_CAP = 128;

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.BARRIER;
    }

    @Override
    public void apply(CastContext ctx) {
        ServerLevel level = (ServerLevel) ctx.caster().level();
        Vec3 center = EffectRegistryHelper.aimPoint(ctx, 10.0);
        BlockState state = block.defaultBlockState();
        int duration = (int) EffectPower.linear(ctx, durationTicks);
        int[] budget = {BLOCK_CAP};
        switch (shape) {
            case "cage" -> placeCage(level, center, state, duration, budget);
            case "ring" -> placeRing(level, center, state, duration, budget);
            default -> placeWall(level, center, ctx.lookDir(), state, duration, budget);
        }
    }

    private void placeWall(ServerLevel level, Vec3 center, Vec3 look, BlockState state,
                           int duration, int[] budget) {
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0E-4) horizontal = new Vec3(0, 0, 1);
        horizontal = horizontal.normalize();
        // right = up × look: horizontal, perpendicular to the look direction
        Vec3 right = new Vec3(horizontal.z, 0, -horizontal.x);
        Vec3 up = new Vec3(0, 1, 0);
        int halfW = width / 2;
        for (int w = -halfW; w <= halfW && budget[0] > 0; w++) {
            for (int h = 0; h < height && budget[0] > 0; h++) {
                Vec3 p = center.add(right.scale(w)).add(up.scale(h));
                budget[0] -= placeIfReplaceable(level, BlockPos.containing(p.x, p.y, p.z), state, duration);
            }
        }
    }

    private void placeRing(ServerLevel level, Vec3 center, BlockState state,
                           int duration, int[] budget) {
        int r = (int) Math.max(1, Math.round(radius));
        int pillarHeight = Math.max(1, height);
        for (int deg = 0; deg < 360 && budget[0] > 0; deg += Math.max(4, 360 / (r * 6))) {
            double rad = Math.toRadians(deg);
            BlockPos pos = BlockPos.containing(
                    center.x + Math.cos(rad) * r, center.y, center.z + Math.sin(rad) * r);
            for (int h = 0; h < pillarHeight && budget[0] > 0; h++) {
                budget[0] -= placeIfReplaceable(level, pos.above(h), state, duration);
            }
        }
    }

    private void placeCage(ServerLevel level, Vec3 center, BlockState state,
                           int duration, int[] budget) {
        int r = (int) Math.max(1, Math.round(radius));
        for (int dx = -r; dx <= r && budget[0] > 0; dx++) {
            for (int dy = -r; dy <= r && budget[0] > 0; dy++) {
                for (int dz = -r; dz <= r && budget[0] > 0; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d < r - 0.5 || d > r + 0.5) continue;
                    BlockPos pos = BlockPos.containing(center.x + dx, center.y + dy, center.z + dz);
                    budget[0] -= placeIfReplaceable(level, pos, state, duration);
                }
            }
        }
    }

    private static int placeIfReplaceable(ServerLevel level, BlockPos pos, BlockState state, int duration) {
        if (!level.getBlockState(pos).canBeReplaced()) return 0;
        return TempBlockManager.place(level, pos, state, duration) ? 1 : 0;
    }
}
