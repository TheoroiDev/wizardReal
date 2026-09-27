package com.theo.wizardreal.match;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Refine R2: FailStreaks (voiceCast#52 失败庇护 state) previously had only
 * indirect coverage through FailSafeLineMatcherTest — the streak semantics
 * themselves (threshold, per-spell isolation, reset-on-success, quit clear)
 * were untested, including {@link FailStreaks#clear}.
 */
class FailStreaksTest {

    @Test
    void streakAccumulatesAndRelaxesAtThreshold() {
        FailStreaks s = new FailStreaks();
        UUID p = UUID.randomUUID();
        assertFalse(s.relaxed(p, "wizardreal:ignis"));
        s.fail(p, "wizardreal:ignis");
        s.fail(p, "wizardreal:ignis");
        assertEquals(2, s.count(p, "wizardreal:ignis"));
        assertFalse(s.relaxed(p, "wizardreal:ignis"));
        s.fail(p, "wizardreal:ignis");
        assertTrue(s.relaxed(p, "wizardreal:ignis"), "3 consecutive failures = relaxed");
    }

    @Test
    void successResetsOnlyThatSpell() {
        FailStreaks s = new FailStreaks();
        UUID p = UUID.randomUUID();
        s.fail(p, "a");
        s.fail(p, "a");
        s.fail(p, "a");
        s.fail(p, "b");
        s.success(p, "a");
        assertEquals(0, s.count(p, "a"));
        assertEquals(1, s.count(p, "b"), "per-spell isolation: b keeps its streak");
    }

    @Test
    void playersAreIsolatedAndClearDropsEverything() {
        FailStreaks s = new FailStreaks();
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        s.fail(p1, "a");
        s.fail(p1, "a");
        assertEquals(0, s.count(p2, "a"), "other player unaffected");
        s.clear(p1);
        assertEquals(0, s.count(p1, "a"), "quit cleanup wipes all spells");
    }
}
