package com.theo.wizardreal.server;

import com.theo.wizardreal.api.SpellStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Chant-stage ladder resolution (magic_eco 03): line-count AND mastery gates. */
class StageResolverTest {

    private static final List<SpellStage> LADDER = List.of(
            new SpellStage(2, 0f, List.of(), null, null),
            new SpellStage(4, 40f, List.of(), 30, 200),
            new SpellStage(6, 70f, List.of(), 60, 600));

    @Test
    void belowFirstStageResolvesToBase() {
        assertEquals(0, StageResolver.resolve(LADDER, 0, 100f));
        assertEquals(0, StageResolver.resolve(LADDER, 1, 100f));
    }

    @Test
    void linesSelectStageWhenMasteryUnlocked() {
        assertEquals(1, StageResolver.resolve(LADDER, 2, 0f));
        assertEquals(2, StageResolver.resolve(LADDER, 4, 40f));
        assertEquals(3, StageResolver.resolve(LADDER, 6, 70f));
        // Full chant beyond the ladder stays on the top stage.
        assertEquals(3, StageResolver.resolve(LADDER, 9, 100f));
    }

    @Test
    void masteryGateResolvesDown() {
        // Enough lines but not enough mastery → the highest unlocked stage.
        assertEquals(1, StageResolver.resolve(LADDER, 4, 39.9f));
        assertEquals(2, StageResolver.resolve(LADDER, 6, 69.9f));
        // No mastery at all → base tier even with all lines spoken.
        assertEquals(0, StageResolver.resolve(List.of(
                new SpellStage(2, 40f, List.of(), null, null)), 6, 0f));
    }

    @Test
    void emptyLadderIsAlwaysBase() {
        assertEquals(0, StageResolver.resolve(List.of(), 6, 100f));
    }

    @Test
    void manaAndCooldownOverridesApplyPerStage() {
        assertEquals(10, StageResolver.manaCost(LADDER, 0, 10));
        assertEquals(10, StageResolver.manaCost(LADDER, 1, 10)); // stage 1 has no override
        assertEquals(30, StageResolver.manaCost(LADDER, 2, 10));
        assertEquals(60, StageResolver.manaCost(LADDER, 3, 10));
        assertEquals(200, StageResolver.cooldownTicks(LADDER, 2, 40));
        assertEquals(600, StageResolver.cooldownTicks(LADDER, 3, 40));
        assertEquals(40, StageResolver.cooldownTicks(LADDER, 1, 40));
        // Out-of-range stage index falls back to base values.
        assertEquals(10, StageResolver.manaCost(LADDER, 99, 10));
        assertEquals(40, StageResolver.cooldownTicks(LADDER, 0, 40));
    }
}
