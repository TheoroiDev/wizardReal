package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.net.ChantNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side ritual chanting state. A player who says a ritual spell's trigger
 * word enters a chant; each subsequent recognized utterance is matched against
 * the current incantation line. Correct lines advance; a wrong line retries the
 * SAME line; finishing the last line casts the spell. The first spoken line
 * locks which chant variant is used.
 *
 * <p>Matching rules, grace windows and timeouts live in the pure
 * {@link ChantEngine} state machine (unit-testable); this class only wires the
 * engine's results to network packets, lockouts and the validated cast path.
 * State is kept per player and only touched on the server main thread (callers
 * marshal via {@code server.execute}).
 */
public final class ChantManager {
    // After a chant COMPLETES, ignore every further utterance from that player
    // for a moment: the final word of a chant usually also matches the ritual
    // trigger, and recognition emits multiple finals per utterance (vosk final
    // + IPA final / endpoint flush) — the first completes the chant, the rest
    // would instantly re-trigger it.
    private static final long COMPLETION_LOCKOUT_MS = 3000L;
    // Shorter lock after an explicit cancel (left-click / timeout) so the
    // player can restart a ritual immediately while still swallowing the
    // audio tail of the cancelled line.
    private static final long CANCEL_LOCKOUT_MS = 1200L;

    private static final ChantManager INSTANCE = new ChantManager();
    public static ChantManager get() { return INSTANCE; }

    private ChantManager() {}

    private final Map<UUID, ChantEngine> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockoutUntil = new ConcurrentHashMap<>();

    public boolean isChanting(ServerPlayer player) {
        return active.containsKey(player.getUUID());
    }

    /**
     * Whether the player may start a new chant / cast at all: false while
     * chanting and for a short window after a chant ended (re-trigger guard,
     * see {@link #COMPLETION_LOCKOUT_MS}).
     */
    public boolean isLocked(ServerPlayer player) {
        if (active.containsKey(player.getUUID())) return true;
        Long until = lockoutUntil.get(player.getUUID());
        return until != null && System.currentTimeMillis() < until;
    }

    private void lock(ServerPlayer player, long durationMs) {
        lockoutUntil.put(player.getUUID(), System.currentTimeMillis() + durationMs);
    }

    /** Enter chanting state for a ritual spell. */
    public void start(ServerPlayer player, Spell spell) {
        active.put(player.getUUID(), new ChantEngine(spell, System.currentTimeMillis()));

        List<List<String>> variantLines = new ArrayList<>();
        for (Chant c : spell.chants()) {
            List<String> keys = new ArrayList<>();
            for (ChantLine line : c.lines()) keys.add(line.displayKey());
            variantLines.add(keys);
        }
        ChantNetwork.sendStart(player, spell.id(), variantLines);
        WizardReal.LOGGER.info("{} began chanting {}", player.getName().getString(), spell.id());
    }

    /**
     * Feed one recognized utterance to an in-progress chant.
     * @return true if the utterance was consumed by the chant (caller should not
     *         try instant casting).
     */
    public boolean feed(ServerPlayer player, String heard, List<String> heardIpa, float confidence) {
        ChantEngine engine = active.get(player.getUUID());
        if (engine == null) return false;

        ChantEngine.FeedResult r = engine.feed(heard, heardIpa, System.currentTimeMillis());
        if (!r.consumed()) return false;

        if (r.timeout()) {
            cancel(player, false);
            return true;
        }
        for (ChantEngine.Progress p : r.progress()) {
            ChantNetwork.sendProgress(player, p.variant(), p.lineIndex(), p.error());
        }
        if (r.finished()) {
            complete(player, engine.spell());
        }
        return true;
    }

    private void complete(ServerPlayer player, Spell spell) {
        active.remove(player.getUUID());
        lock(player, COMPLETION_LOCKOUT_MS);
        ChantNetwork.sendEnd(player, true);
        // Perform the actual cast through the normal validated path.
        SpellCastHandler.handleCast(player, spell.id(), 1.0f);
        WizardReal.LOGGER.info("{} completed chant for {}", player.getName().getString(), spell.id());
    }

    public void cancel(ServerPlayer player, boolean success) {
        if (active.remove(player.getUUID()) != null) {
            lock(player, CANCEL_LOCKOUT_MS);
            ChantNetwork.sendEnd(player, success);
        }
    }

    public void onQuit(ServerPlayer player) {
        if (active.remove(player.getUUID()) != null) {
            ChantNetwork.sendEnd(player, false);
        }
    }

    /**
     * Cancel every active chant. Called when the spell registry is rebuilt
     * (datapack /reload) so sessions never reference stale {@link Spell}
     * objects.
     */
    public void clearAll(MinecraftServer server) {
        if (active.isEmpty()) return;
        for (UUID uuid : active.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) ChantNetwork.sendEnd(p, false);
        }
        active.clear();
        WizardReal.LOGGER.info("All active chants cancelled (spell registry reloaded)");
    }

    /** Periodic timeout sweep (called from the server tick). */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        active.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            ChantEngine engine = e.getValue();
            if (p == null) return true; // player gone; quit handler covers packet
            if (now - engine.lastActivityMs() <= ChantEngine.TIMEOUT_MS) return false;
            lock(p, COMPLETION_LOCKOUT_MS);
            ChantNetwork.sendEnd(p, false);
            return true;
        });
        // Expire stale lockouts so the map cannot grow without bound.
        lockoutUntil.values().removeIf(until -> now >= until);
    }
}
