package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Expiring spell-placed blocks (magic_eco 04 §2 {@code surface}/{@code barrier}):
 * tracks every placed position with its original state and restores it on
 * expiry — the "到期复原，防 reload 残留" contract from the 07 roadmap §4.3.
 *
 * <p>The first placement of a position wins: overlapping spells never capture
 * another spell's block as the "original". A position mined away before expiry
 * restores too (an air gap reverts); a position the player re-built on does not.
 */
public final class TempBlockManager {
    private record Entry(ResourceKey<Level> dimension, BlockPos pos,
                         BlockState original, BlockState placed, long expireGameTime) {}

    private static final Map<ResourceKey<Level>, List<Entry>> PENDING = new HashMap<>();

    private TempBlockManager() {}

    /** Call once from mod init. */
    public static void register() {
        TickEvent.SERVER_POST.register(TempBlockManager::tick);
    }

    /** Place {@code state} at {@code pos}; restore the previous state after
     *  {@code durationTicks}. Positions that already carry a pending temp block
     *  are skipped (returns false). */
    public static boolean place(ServerLevel level, BlockPos pos, BlockState state, int durationTicks) {
        ResourceKey<Level> key = level.dimension();
        List<Entry> entries = PENDING.computeIfAbsent(key, k -> new ArrayList<>());
        BlockPos immutable = pos.immutable();
        for (Entry e : entries) {
            if (e.pos().equals(immutable)) return false;
        }
        BlockState original = level.getBlockState(immutable);
        if (!level.setBlock(immutable, state, 3)) return false;
        long expire = level.getGameTime() + Math.max(1, durationTicks);
        entries.add(new Entry(key, immutable, original, state, expire));
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        for (Map.Entry<ResourceKey<Level>, List<Entry>> dimEntry : PENDING.entrySet()) {
            ServerLevel level = server.getLevel(dimEntry.getKey());
            if (level == null) continue;
            List<Entry> entries = dimEntry.getValue();
            List<Entry> remaining = null;
            for (Entry e : entries) {
                if (level.getGameTime() >= e.expireGameTime()) {
                    restore(level, e);
                } else {
                    if (remaining == null) remaining = new ArrayList<>();
                    remaining.add(e);
                }
            }
            dimEntry.setValue(remaining == null ? new ArrayList<>() : remaining);
        }
        PENDING.values().removeIf(List::isEmpty);
    }

    private static void restore(ServerLevel level, Entry e) {
        BlockState current = level.getBlockState(e.pos());
        // Untouched temp block or mined-away gap → restore. Any other block
        // means someone else modified this spot — leave it alone.
        if (current.equals(e.placed()) || current.isAir()) {
            level.setBlock(e.pos(), e.original(), 3);
        }
    }

    /** Clear every pending restoration (server stopped / registry reloaded). */
    public static void clearAll(MinecraftServer server) {
        for (Map.Entry<ResourceKey<Level>, List<Entry>> dimEntry : PENDING.entrySet()) {
            ServerLevel level = server.getLevel(dimEntry.getKey());
            if (level == null) continue;
            for (Entry e : dimEntry.getValue()) {
                try {
                    restore(level, e);
                } catch (Throwable t) {
                    WizardReal.LOGGER.warn("Temp block restore failed at {}", e.pos(), t);
                }
            }
        }
        PENDING.clear();
    }
}
