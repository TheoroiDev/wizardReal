package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Spell;
import net.minecraft.server.level.ServerPlayer;

/**
 * Learning settlement (D4): gains accrue only on SUCCESSFUL casts (UO rule —
 * fizzles and interruptions never teach). One utterance settles exactly once
 * (D-D3: the router guarantees a single {@code castValidated} per cast event).
 *
 * <p>Gains are percentage points of {@code t}; the per-spell difficulty is
 * applied at storage time ({@code points = t x difficulty}), so difficult
 * spells convert the same effort into fewer points.
 *
 * <p>Two-axis rule (wizardReal#43 防异化): mastery follows SUCCESS — the score
 * bands and the resonance power reward never add a single point of mastery.
 * Practice rehearsals are the one extra mastery source (half gain, daily
 * capped), keeping "score follows performance, mastery follows success" true.
 */
public final class LearningService {
    public enum Source { VOICE, TOME, SCROLL, PRACTICE }

    public static final float VOICE_GAIN = 2.0f;
    public static final float TOME_GAIN = 10.0f;
    /** Tome study stops paying off at 75%: books teach, practice masters (D4 修正 3). */
    public static final float TOME_CAP_PERCENT = 75f;
    public static final float SCROLL_GAIN = 1.0f;
    /** Practice rehearsal (wizardReal#43): half a real cast, daily-capped —
     *  practice rehearses, real casts teach (练习 +1%/次，实 cast 全额 +2%）。 */
    public static final float PRACTICE_GAIN = 1.0f;
    /** Practice settlements per spell per day (the daily cap). */
    public static final int PRACTICE_DAILY_ATTEMPTS = 5;

    private LearningService() {}

    /** t gain for one use of {@code source} at the current mastery. TOME decays
     * linearly to zero at the 75% cap; VOICE/SCROLL/PRACTICE are linear to 100+
     * (only overlearning is config-gated at the power curve, not the counting). */
    public static float gainFor(Source source, float currentT) {
        return switch (source) {
            case VOICE -> VOICE_GAIN;
            case TOME -> currentT >= TOME_CAP_PERCENT
                    ? 0f
                    : TOME_GAIN * Math.max(0f, (TOME_CAP_PERCENT - currentT) / TOME_CAP_PERCENT);
            case SCROLL -> SCROLL_GAIN;
            case PRACTICE -> PRACTICE_GAIN;
        };
    }

    /** Settle one successful voice cast (standard path — commands with
     * {@code SKIP_LEARNING} never reach this). */
    public static void onCastSuccess(ServerPlayer player, Spell spell) {
        settle(player, spell, Source.VOICE);
    }

    /**
     * Settle one completed practice rehearsal (wizardReal#43): half gain,
     * daily-capped per spell.
     *
     * @return the applied gain (0 = daily cap reached or nothing to learn)
     */
    public static float onPractice(ServerPlayer player, Spell spell) {
        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        if (state.practiceAttemptsToday(player.getUUID(), spell.id(), today())
                >= PRACTICE_DAILY_ATTEMPTS) {
            return 0f;
        }
        float applied = settle(player, spell, Source.PRACTICE);
        if (applied > 0f) {
            state.recordPracticeAttempt(player.getUUID(), spell.id(), today());
        }
        return applied;
    }

    /** Day stamp for the practice cap (UTC day — long-lived servers roll it
     *  at midnight UTC; good enough for a rehearsal cap). */
    private static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    private static float settle(ServerPlayer player, Spell spell, Source source) {
        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        float difficulty = spell.difficulty();
        float t = state.learningPercent(player.getUUID(), spell.id(), difficulty);
        float gain = gainFor(source, t);
        if (gain <= 0f) return 0f;
        state.addLearningPercent(player.getUUID(), spell.id(), gain, difficulty);
        state.save();
        return gain;
    }

    /** Settle one tome use (study). Returns the applied gain (0 = nothing to learn). */
    public static float onTomeUse(ServerPlayer player, Spell spell) {
        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        float difficulty = spell.difficulty();
        float t = state.learningPercent(player.getUUID(), spell.id(), difficulty);
        float gain = gainFor(Source.TOME, t);
        if (gain <= 0f) return 0f;
        state.addLearningPercent(player.getUUID(), spell.id(), gain, difficulty);
        state.save();
        return gain;
    }
}
