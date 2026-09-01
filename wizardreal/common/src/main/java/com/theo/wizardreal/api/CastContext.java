package com.theo.wizardreal.api;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side context passed to a {@link Spell} when it is cast.
 *
 * @param caster     the player casting the spell
 * @param origin     eye position of the caster
 * @param lookDir    normalized look vector
 * @param power      0..1 multiplier (e.g. from shout volume or charge time)
 * @param blackboard per-cast scratch space shared between composed effects
 *                   (e.g. a projectile effect may publish its hit position for
 *                   a follow-up effect). Mutable; server main thread only.
 */
public record CastContext(
        ServerPlayer caster,
        Vec3 origin,
        Vec3 lookDir,
        float power,
        Map<ResourceLocation, Object> blackboard
) {
    public CastContext(ServerPlayer caster, Vec3 origin, Vec3 lookDir, float power) {
        this(caster, origin, lookDir, power, new HashMap<>());
    }
}
