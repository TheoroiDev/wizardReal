package com.theo.wizardreal.match;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consecutive failed chants per (player, spell) — the state behind the
 * voiceCast#52 失败庇护 relaxation. Pure and MC-free so the streak
 * semantics (threshold, reset-on-success, per-spell isolation) are
 * simulation-testable.
 */
public final class FailStreaks {

    /** Consecutive failures after which the庇护 relaxation applies. */
    public static final int RELAX_AFTER = 3;

    private final Map<UUID, Map<String, Integer>> bySpell = new ConcurrentHashMap<>();

    /** Record one failed chant of {@code spellId}. */
    public void fail(UUID player, String spellId) {
        bySpell.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(spellId, 1, Integer::sum);
    }

    /** Record a success — the streak resets (any partial relaxation ends). */
    public void success(UUID player, String spellId) {
        Map<String, Integer> m = bySpell.get(player);
        if (m != null) m.remove(spellId);
    }

    /** Consecutive failures counted so far (0 = none / reset / new day). */
    public int count(UUID player, String spellId) {
        Map<String, Integer> m = bySpell.get(player);
        Integer n = m == null ? null : m.get(spellId);
        return n == null ? 0 : n;
    }

    /** Whether the streak reached {@link #RELAX_AFTER} — the庇护 is active. */
    public boolean relaxed(UUID player, String spellId) {
        return count(player, spellId) >= RELAX_AFTER;
    }

    /** Drop all state for a player (quit cleanup). */
    public void clear(UUID player) {
        bySpell.remove(player);
    }

    /** Drop everything (server stop — nothing may leak across worlds). */
    public void clearAll() {
        bySpell.clear();
    }
}
