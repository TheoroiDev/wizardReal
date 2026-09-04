package com.theo.wizardreal.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** 0.4.0 learning model (D-D2): points are the single progression source;
 * known is derived; legacy NBT migrates. */
class PlayerMagicStateTest {

    private static final String IGNIS = "wizardreal:ignis";
    private static final String FULMEN = "wizardreal:fulmen";
    private static final String EXPLOSION = "wizardreal:explosion";

    @Test
    void absentLearningReadsAsBaseline() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();
        assertEquals(LearningCurve.GATE_PERCENT, state.learningPercent(player, IGNIS, 1.0f), 0.001f);
        // No storage for untouched spells.
        assertTrue(state.learningSnapshot(player).isEmpty());
    }

    @Test
    void gateAndKnownDeriveFromPoints() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();
        float difficulty = 2.0f; // forbidden-spell scale

        // Baseline: castable at the gate but NOT known (t=10 is not > 10).
        assertTrue(LearningCurve.castable(state.learningPercent(player, EXPLOSION, difficulty)));
        assertFalse(LearningCurve.known(state.learningPercent(player, EXPLOSION, difficulty), 10f));

        // First voice cast: +2 -> 12% -> known flips on (t > 10).
        state.addLearningPercent(player, EXPLOSION, 2.0f, difficulty);
        float t = state.learningPercent(player, EXPLOSION, difficulty);
        assertEquals(12.0f, t, 0.001f);
        assertTrue(LearningCurve.known(t, 10f));
        // Points stored scaled by difficulty: 12% x difficulty 2.0 = 24 points.
        assertEquals(24.0f, state.learningPoints(player, EXPLOSION), 0.001f);
    }

    @Test
    void addAndSetClampToBounds() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();

        // Fully forgotten (0%) then over-capped (625% max).
        state.setLearningPercent(player, IGNIS, 0f, 1.0f);
        assertEquals(0f, state.learningPercent(player, IGNIS, 1.0f), 0.001f);
        state.setLearningPercent(player, IGNIS, 9000f, 1.0f);
        assertEquals(LearningCurve.MAX_PERCENT, state.learningPercent(player, IGNIS, 1.0f), 0.001f);
        // Negative deltas clamp at zero, never negative.
        state.addLearningPercent(player, FULMEN, -999f, 1.0f);
        assertEquals(0f, state.learningPercent(player, FULMEN, 1.0f), 0.001f);
    }

    @Test
    void learningSurvivesNbtRoundtrip() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();
        state.setLearningPercent(player, IGNIS, 55f, 1.0f);
        state.setLearningPercent(player, FULMEN, 0f, 1.0f);
        state.setLearningPercent(player, EXPLOSION, 120f, 2.0f);

        CompoundTag nbt = state.writeNbt();
        PlayerMagicState reloaded = new PlayerMagicState();
        reloaded.fromNbt(nbt);

        assertEquals(55f, reloaded.learningPercent(player, IGNIS, 1.0f), 0.001f);
        assertEquals(0f, reloaded.learningPercent(player, FULMEN, 1.0f), 0.001f);
        assertEquals(120f, reloaded.learningPercent(player, EXPLOSION, 2.0f), 0.001f);
    }

    /** Legacy 0.3.x NBT: forgottenSpells become 0 points (gate keeps blocking
     * them); knownSpells fold into the default baseline (no storage). */
    @Test
    void legacyNbtMigratesToLearning() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();

        CompoundTag nbt = new CompoundTag();
        CompoundTag known = new CompoundTag();
        net.minecraft.nbt.ListTag knownList = new net.minecraft.nbt.ListTag();
        knownList.add(net.minecraft.nbt.StringTag.valueOf(IGNIS));
        known.put(player.toString(), knownList);
        nbt.put("knownSpells", known);
        CompoundTag forgotten = new CompoundTag();
        net.minecraft.nbt.ListTag forgottenList = new net.minecraft.nbt.ListTag();
        forgottenList.add(net.minecraft.nbt.StringTag.valueOf(FULMEN));
        forgotten.put(player.toString(), forgottenList);
        nbt.put("forgottenSpells", forgotten);

        state.fromNbt(nbt);

        // Known spells fold into the baseline (not stored).
        assertEquals(LearningCurve.GATE_PERCENT, state.learningPercent(player, IGNIS, 1.0f), 0.001f);
        assertFalse(state.learningSnapshot(player).containsKey(IGNIS));
        // Forgotten spells are pinned to 0.
        assertEquals(0f, state.learningPercent(player, FULMEN, 1.0f), 0.001f);
        assertEquals(0f, state.learningPoints(player, FULMEN), 0.001f);
    }

    @Test
    void learningSnapshotExposesStoredEntries() {
        PlayerMagicState state = new PlayerMagicState();
        UUID player = UUID.randomUUID();
        state.setLearningPercent(player, IGNIS, 42f, 1.0f);
        Map<String, Float> snapshot = state.learningSnapshot(player);
        assertEquals(1, snapshot.size());
        assertEquals(42f, snapshot.get(IGNIS), 0.001f);
    }
}
