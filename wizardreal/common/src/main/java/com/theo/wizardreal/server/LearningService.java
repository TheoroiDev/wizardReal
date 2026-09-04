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
 */
public final class LearningService {
    public enum Source { VOICE, TOME, SCROLL }

    public static final float VOICE_GAIN = 2.0f;
    public static final float TOME_GAIN = 10.0f;
    /** Tome study stops paying off at 75%: books teach, practice masters (D4 修正 3). */
    public static final float TOME_CAP_PERCENT = 75f;
    public static final float SCROLL_GAIN = 1.0f;

    private LearningService() {}

    /** t gain for one use of {@code source} at the current mastery. TOME decays
     * linearly to zero at the 75% cap; VOICE/SCROLL are linear to 100+ (only
     * overlearning is config-gated at the power curve, not the counting). */
    public static float gainFor(Source source, float currentT) {
        return switch (source) {
            case VOICE -> VOICE_GAIN;
            case TOME -> currentT >= TOME_CAP_PERCENT
                    ? 0f
                    : TOME_GAIN * Math.max(0f, (TOME_CAP_PERCENT - currentT) / TOME_CAP_PERCENT);
            case SCROLL -> SCROLL_GAIN;
        };
    }

    /** Settle one successful voice cast (standard path — commands with
     * {@code SKIP_LEARNING} never reach this). */
    public static void onCastSuccess(ServerPlayer player, Spell spell) {
        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        float difficulty = spell.difficulty();
        float t = state.learningPercent(player.getUUID(), spell.id(), difficulty);
        float gain = gainFor(Source.VOICE, t);
        if (gain <= 0f) return;
        state.addLearningPercent(player.getUUID(), spell.id(), gain, difficulty);
        state.save();
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
