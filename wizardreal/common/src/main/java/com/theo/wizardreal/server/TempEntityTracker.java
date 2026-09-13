package com.theo.wizardreal.server;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expiry tracking for spell-summoned entities (magic_eco 04 §2 {@code summon}):
 * when the summon's duration lapses the entity is discarded with a poof,
 * whether or not it died in between (a dead entity's entry is simply dropped).
 */
public final class TempEntityTracker {
    private record Tracked(ResourceKey<Level> dimension, UUID entity, long expireGameTime) {}

    private static final Map<ResourceKey<Level>, List<Tracked>> PENDING = new HashMap<>();

    private TempEntityTracker() {}

    /** Call once from mod init. */
    public static void register() {
        TickEvent.SERVER_POST.register(TempEntityTracker::tick);
    }

    public static void track(ServerLevel level, Entity entity, int durationTicks) {
        PENDING.computeIfAbsent(level.dimension(), k -> new ArrayList<>())
                .add(new Tracked(level.dimension(), entity.getUUID(),
                        level.getGameTime() + Math.max(1, durationTicks)));
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (Map.Entry<ResourceKey<Level>, List<Tracked>> dimEntry : PENDING.entrySet()) {
            ServerLevel level = server.getLevel(dimEntry.getKey());
            if (level == null) continue;
            List<Tracked> entries = dimEntry.getValue();
            List<Tracked> remaining = null;
            for (Tracked t : entries) {
                if (level.getGameTime() >= t.expireGameTime()) {
                    Entity e = level.getEntity(t.entity());
                    if (e != null && e.isAlive()) {
                        level.sendParticles(ParticleTypes.POOF,
                                e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(),
                                10, 0.2, 0.2, 0.2, 0.02);
                        e.discard();
                    }
                } else {
                    if (remaining == null) remaining = new ArrayList<>();
                    remaining.add(t);
                }
            }
            dimEntry.setValue(remaining == null ? new ArrayList<>() : remaining);
        }
        PENDING.values().removeIf(List::isEmpty);
    }

    /** Discard every tracked summon (server stopped). */
    public static void clearAll(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, List<Tracked>> dimEntry : PENDING.entrySet()) {
            ServerLevel level = server.getLevel(dimEntry.getKey());
            if (level == null) continue;
            for (Tracked t : dimEntry.getValue()) {
                Entity e = level.getEntity(t.entity());
                if (e != null && e.isAlive()) e.discard();
            }
        }
        PENDING.clear();
    }
}
