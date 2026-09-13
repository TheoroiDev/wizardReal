package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Touch-smelting (magic_eco 04, 矿位): ore blocks and smeltable stones around
 * the aimed point pop their furnace product (and a sliver of XP) without a
 * furnace trip. The product map is primitive knowledge (like lightning knowing
 * how lightning works), not datapack content.
 */
public record SmeltEffect(double radius) implements SpellEffect {
    private static final Map<Block, ItemStack> PRODUCTS = Map.ofEntries(
            Map.entry(Blocks.IRON_ORE, new ItemStack(Items.IRON_INGOT)),
            Map.entry(Blocks.DEEPSLATE_IRON_ORE, new ItemStack(Items.IRON_INGOT)),
            Map.entry(Blocks.GOLD_ORE, new ItemStack(Items.GOLD_INGOT)),
            Map.entry(Blocks.DEEPSLATE_GOLD_ORE, new ItemStack(Items.GOLD_INGOT)),
            Map.entry(Blocks.COPPER_ORE, new ItemStack(Items.COPPER_INGOT)),
            Map.entry(Blocks.DEEPSLATE_COPPER_ORE, new ItemStack(Items.COPPER_INGOT)),
            Map.entry(Blocks.ANCIENT_DEBRIS, new ItemStack(Items.NETHERITE_SCRAP)),
            Map.entry(Blocks.SAND, new ItemStack(Items.GLASS)),
            Map.entry(Blocks.RED_SAND, new ItemStack(Items.GLASS)),
            Map.entry(Blocks.COBBLESTONE, new ItemStack(Items.STONE)),
            Map.entry(Blocks.STONE, new ItemStack(Items.SMOOTH_STONE)),
            Map.entry(Blocks.CLAY, new ItemStack(Items.TERRACOTTA)),
            Map.entry(Blocks.WET_SPONGE, new ItemStack(Items.SPONGE)));

    private static final Map<Block, Integer> XP = Map.of(
            Blocks.IRON_ORE, 1, Blocks.DEEPSLATE_IRON_ORE, 1,
            Blocks.GOLD_ORE, 1, Blocks.DEEPSLATE_GOLD_ORE, 1,
            Blocks.COPPER_ORE, 1, Blocks.DEEPSLATE_COPPER_ORE, 1,
            Blocks.ANCIENT_DEBRIS, 2);

    public static final MapCodec<SmeltEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.DOUBLE.optionalFieldOf("radius", 1.5).forGetter(SmeltEffect::radius)
            ).apply(instance, SmeltEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.SMELT;
    }

    @Override
    public void apply(CastContext ctx) {
        ServerLevel level = (ServerLevel) ctx.caster().level();
        Vec3 center = EffectRegistryHelper.aimPoint(ctx, 8.0);
        int r = (int) Math.max(0, Math.round(EffectPower.linear(ctx, radius)));
        int smelted = 0;
        BlockPos base = BlockPos.containing(center.x, center.y, center.z);
        for (int dx = -r; dx <= r && smelted < 32; dx++) {
            for (int dy = -r; dy <= r && smelted < 32; dy++) {
                for (int dz = -r; dz <= r && smelted < 32; dz++) {
                    BlockPos pos = base.offset(dx, dy, dz);
                    Block block = level.getBlockState(pos).getBlock();
                    ItemStack product = PRODUCTS.get(block);
                    if (product == null) continue;
                    level.removeBlock(pos, false);
                    Block.popResource(level, pos, product.copy());
                    Integer xp = XP.get(block);
                    if (xp != null) {
                        ExperienceOrb.award(level, Vec3.atCenterOf(pos), xp);
                    }
                    level.sendParticles(ParticleTypes.FLAME,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            6, 0.2, 0.2, 0.2, 0.01);
                    smelted++;
                }
            }
        }
    }
}
