package com.theo.wizardreal.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.server.TempEntityTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Temporary summon (07 roadmap §4.5): spawns {@code count} entities around the
 * caster, owned and followed when the type is tameable, pointed at the nearest
 * monster when the type itself is hostile (raised skeletons guard, not attack
 * the caster). Every summon expires via {@link TempEntityTracker}.
 */
public record SummonEffect(EntityType<?> entity, int count, int durationTicks,
                           boolean follow) implements SpellEffect {
    public static final MapCodec<SummonEffect> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    EffectRegistry.registryByName(BuiltInRegistries.ENTITY_TYPE).fieldOf("entity")
                            .forGetter(SummonEffect::entity),
                    Codec.INT.optionalFieldOf("count", 1).forGetter(SummonEffect::count),
                    Codec.INT.optionalFieldOf("duration_ticks", 600).forGetter(SummonEffect::durationTicks),
                    Codec.BOOL.optionalFieldOf("follow", true).forGetter(SummonEffect::follow)
            ).apply(instance, SummonEffect::new));

    @Override
    public ResourceLocation effectId() {
        return BuiltinEffects.SUMMON;
    }

    @Override
    public void apply(CastContext ctx) {
        ServerLevel level = (ServerLevel) ctx.caster().level();
        Vec3 base = ctx.caster().position();
        int n = Math.min(EffectPower.count(ctx, count), 6);
        for (int i = 0; i < n; i++) {
            Entity spawned = entity.create(level);
            if (spawned == null) {
                WizardReal.LOGGER.warn("Effect {} could not create entity {}", effectId(),
                        BuiltInRegistries.ENTITY_TYPE.getKey(entity));
                return;
            }
            double angle = (Math.PI * 2 / n) * i;
            spawned.moveTo(base.x + Math.cos(angle) * 1.5, base.y, base.z + Math.sin(angle) * 1.5,
                    ctx.caster().getYRot(), 0);
            if (spawned instanceof Mob mob) {
                mob.setPersistenceRequired();
                if (follow && mob instanceof TamableAnimal tamable && !tamable.isTame()) {
                    tamable.tame(ctx.caster());
                }
                if (mob instanceof Monster) {
                    pointAtNearestMonster(level, mob, spawned);
                }
            }
            level.addFreshEntity(spawned);
            TempEntityTracker.track(level, spawned, (int) EffectPower.linear(ctx, durationTicks));
        }
    }

    private static void pointAtNearestMonster(ServerLevel level, Mob summon, Entity self) {
        var monsters = level.getEntities(self, new AABB(self.position(), self.position()).inflate(24),
                e -> e != self && e.isAlive() && e instanceof net.minecraft.world.entity.monster.Enemy);
        if (!monsters.isEmpty()) {
            summon.setTarget((net.minecraft.world.entity.LivingEntity) monsters.get(0));
        }
    }
}
