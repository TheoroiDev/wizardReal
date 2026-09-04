package com.theo.wizardreal.net;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side holder for synced magic state. Updated ONLY via {@link #apply},
 * which the network receiver marshals onto the main thread (ctx.queue); reads
 * come from {@link com.theo.wizardreal.client.MagicHud} on the render pass.
 * Backed by a ConcurrentHashMap as a second line of defense.
 */
public final class MagicClientState {

    public static volatile float mana = 200f;
    public static volatile float maxMana = 200f;
    /** spell id -> client time (ms) when cooldown ends */
    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    /** spell id -> mastery percent (0.4.0 learning sync; absent spells sit at the 10% baseline) */
    private static final Map<String, Float> learning = new ConcurrentHashMap<>();

    private MagicClientState() {}

    /** Apply a full sync. Must be called on the client main thread. */
    public static void apply(float newMana, float newMaxMana, Map<String, Integer> remainingTicksBySpell,
                             Map<String, Float> learningPercents) {
        mana = newMana;
        maxMana = newMaxMana;
        long now = System.currentTimeMillis();
        cooldowns.clear();
        for (Map.Entry<String, Integer> e : remainingTicksBySpell.entrySet()) {
            cooldowns.put(e.getKey(), now + e.getValue() * 50L);
        }
        learning.clear();
        learning.putAll(learningPercents);
    }

    /** Mastery percent for the HUD; absent spells read as the 10% baseline. */
    public static float learningPercent(String spellId) {
        Float t = learning.get(spellId);
        return t == null ? 10f : t;
    }

    /** Snapshot of active cooldowns (spell id -> end ms) for HUD rendering. */
    public static Map<String, Long> snapshotCooldowns() {
        return new HashMap<>(cooldowns);
    }

    /** Returns remaining seconds for a cooldown, or 0 if expired. */
    public static float cooldownSecondsLeft(String spellId) {
        Long end = cooldowns.get(spellId);
        if (end == null) return 0f;
        long remainingMs = end - System.currentTimeMillis();
        if (remainingMs <= 0) {
            cooldowns.remove(spellId);
            return 0f;
        }
        return remainingMs / 1000f;
    }
}
