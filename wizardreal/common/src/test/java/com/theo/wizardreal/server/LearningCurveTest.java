package com.theo.wizardreal.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Learning curve boundaries (D4 §4.3): 0/10/50/100/121/625 + gate + known. */
class LearningCurveTest {

    @Test
    void gateBlocksBelowTenPercent() {
        assertFalse(LearningCurve.castable(0f));
        assertFalse(LearningCurve.castable(9.9f));
        assertTrue(LearningCurve.castable(10f));
        assertTrue(LearningCurve.castable(100f));
    }

    @Test
    void knownIsStrictlyAboveThreshold() {
        assertFalse(LearningCurve.known(10f, 10f));
        assertTrue(LearningCurve.known(10.1f, 10f));
        assertTrue(LearningCurve.known(12f, 10f));
    }

    @Test
    void powerCurveBoundaries() {
        assertEquals(0.5f, LearningCurve.power(0f, false), 0.0001f);
        assertEquals(0.5f, LearningCurve.power(10f, false), 0.0001f);
        assertEquals(0.75f, LearningCurve.power(55f, false), 0.0001f);
        assertEquals(1.0f, LearningCurve.power(100f, false), 0.0001f);
        // Continuity at t=100 between the linear and overlearning segments.
        assertEquals(1.0f, LearningCurve.power(100f, true), 0.0001f);
    }

    @Test
    void overlearningOffCapsAtOne() {
        assertEquals(1.0f, LearningCurve.power(121f, false), 0.0001f);
        assertEquals(1.0f, LearningCurve.power(625f, false), 0.0001f);
    }

    @Test
    void overlearningOnFollowsSquareRoot() {
        assertEquals(1.1f, LearningCurve.power(121f, true), 0.0001f);
        assertEquals(2.5f, LearningCurve.power(625f, true), 0.0001f);
        assertEquals(2.5f, LearningCurve.power(10000f, true), 0.0001f); // hard cap
    }

    @Test
    void resolverClampsMultiplicativeResult() {
        // Chant tier 1.0 x learning 1.0 = 1.0.
        assertEquals(1.0f, PowerResolver.resolve(1.0f, 100f, false), 0.0001f);
        // Chant tier 0.5 x newbie learning 0.5 = 0.25 (still positive).
        assertEquals(0.25f, PowerResolver.resolve(0.5f, 10f, false), 0.0001f);
        // Overlearning: tier 1.0 x 1.1 = 1.1.
        assertEquals(1.1f, PowerResolver.resolve(1.0f, 121f, true), 0.0001f);
        // Global cap: even 2.0 x 2.5 clamps to 2.5.
        assertEquals(2.5f, PowerResolver.resolve(2.0f, 625f, true), 0.0001f);
    }

    @Test
    void learningServiceGains() {
        assertEquals(2.0f, LearningService.gainFor(LearningService.Source.VOICE, 0f), 0.0001f);
        assertEquals(2.0f, LearningService.gainFor(LearningService.Source.VOICE, 99f), 0.0001f);
        // Tome: 10% x linear decay toward the 75% cap (decays from the baseline too).
        assertEquals(10.0f, LearningService.gainFor(LearningService.Source.TOME, 0f), 0.0001f);
        assertEquals(8.6667f, LearningService.gainFor(LearningService.Source.TOME, 10f), 0.001f);
        assertEquals(4.3333f, LearningService.gainFor(LearningService.Source.TOME, 42.5f), 0.001f);
        assertEquals(0.0f, LearningService.gainFor(LearningService.Source.TOME, 75f), 0.0001f);
        assertEquals(0.0f, LearningService.gainFor(LearningService.Source.TOME, 90f), 0.0001f);
    }
}
