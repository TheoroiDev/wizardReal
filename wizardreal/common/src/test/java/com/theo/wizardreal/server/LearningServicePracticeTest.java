package com.theo.wizardreal.server;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** wizardReal#43 熟练度双轴: mastery follows SUCCESS — practice rehearsals
 *  settle at HALF the real-cast rate under a daily cap, and the score-band /
 *  resonance reward (a ChantManager power concept) never appears as a
 *  mastery gain here. */
class LearningServicePracticeTest {

    private static final String IGNIS = "wizardreal:ignis";

    @Test
    void practiceGainIsHalfTheVoiceGain() {
        assertEquals(1.0f, LearningService.gainFor(LearningService.Source.PRACTICE, 10f), 0.0001f);
        assertEquals(LearningService.VOICE_GAIN / 2,
                LearningService.gainFor(LearningService.Source.PRACTICE, 10f), 0.0001f);
        // The voice rate itself is untouched by #43 (实 cast 全额 +2%).
        assertEquals(2.0f, LearningService.gainFor(LearningService.Source.VOICE, 10f), 0.0001f);
    }

    @Test
    void dailyCapCountsPerSpellAndResetsOnANewDay() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();
        long today = 20_000;
        long tomorrow = 20_001;

        for (int i = 0; i < LearningService.PRACTICE_DAILY_ATTEMPTS; i++) {
            assertEquals(i, state.practiceAttemptsToday(player, IGNIS, today));
            state.recordPracticeAttempt(player, IGNIS, today);
        }
        assertEquals(LearningService.PRACTICE_DAILY_ATTEMPTS,
                state.practiceAttemptsToday(player, IGNIS, today));
        // A new day resets the counter; another spell is unaffected.
        assertEquals(0, state.practiceAttemptsToday(player, IGNIS, tomorrow));
        assertEquals(0, state.practiceAttemptsToday(player, "wizardreal:fulmen", today));
    }

    @Test
    void practiceCapSurvivesNbtRoundtrip() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();
        long day = 123456;
        state.recordPracticeAttempt(player, IGNIS, day);
        state.recordPracticeAttempt(player, IGNIS, day);

        PlayerMagicState reloaded = new PlayerMagicState();
        reloaded.fromNbt(state.writeNbt());
        assertEquals(2, reloaded.practiceAttemptsToday(player, IGNIS, day));
        assertNotEquals(0, reloaded.practiceAttemptsToday(player, IGNIS, day));
    }
}
