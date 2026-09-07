package com.theo.wizardreal.server;

import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scheduled particle playback for the {@code visual} effect primitive
 * (magic_eco 05): a shape computes a sequence of particle emissions spread
 * over the effect's duration and hands them here; the server tick plays them
 * back in order. One-shot shapes simply schedule everything at tick +0.
 */
public final class VisualPlayback {
    private record Emission(ResourceKey<Level> dimension, ParticleOptions particle, Vec3 pos,
                            int count, double dx, double dy, double dz, float speed) {}

    private static final Map<Long, List<Emission>> QUEUED = new HashMap<>();

    private VisualPlayback() {}

    /** Call once from mod init. */
    public static void register() {
        TickEvent.SERVER_POST.register(VisualPlayback::tick);
    }

    public static void now(ServerLevel level, ParticleOptions particle, Vec3 pos,
                           int count, double dx, double dy, double dz, float speed) {
        schedule(level, 0, particle, pos, count, dx, dy, dz, speed);
    }

    public static void schedule(ServerLevel level, int delayTicks, ParticleOptions particle, Vec3 pos,
                                int count, double dx, double dy, double dz, float speed) {
        long due = level.getGameTime() + Math.max(0, delayTicks);
        QUEUED.computeIfAbsent(due, k -> new ArrayList<>())
                .add(new Emission(level.dimension(), particle, pos, count, dx, dy, dz, speed));
    }

    private static void tick(MinecraftServer server) {
        if (QUEUED.isEmpty()) return;
        long now = server.overworld().getGameTime();
        List<Long> due = new ArrayList<>();
        for (Long key : QUEUED.keySet()) {
            if (key <= now) due.add(key);
        }
        for (Long key : due) {
            for (Emission e : QUEUED.remove(key)) {
                ServerLevel level = server.getLevel(e.dimension());
                if (level == null) continue;
                level.sendParticles(e.particle(), e.pos().x, e.pos().y, e.pos().z,
                        e.count(), e.dx(), e.dy(), e.dz(), e.speed());
            }
        }
    }

    /** Drop all pending emissions (server stopped). */
    public static void clearAll() {
        QUEUED.clear();
    }
}
