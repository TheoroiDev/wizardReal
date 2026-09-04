package com.theo.wizardreal.server;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idle-state routing (D9): L1 entry gate / instant cast / skip candidate. */
class ChantGateTest {

    private static final long T0 = 1_000_000L;

    private static Pronunciation pron(String id, String... aliases) {
        return new Pronunciation(id, List.of(), List.of(aliases));
    }

    private static Spell instant() {
        return TestSpell.of("wizardreal:ignis", pron("wizardreal:ignis", "ignis", "fire"));
    }

    private static Spell ritual() {
        Chant variantA = new Chant(List.of(
                new ChantLine(null, pron("wizardreal:mare.chant.en.0:0", "o tide and storm")),
                new ChantLine(null, pron("wizardreal:mare.chant.en.0:1", "drown the field")),
                new ChantLine(null, pron("wizardreal:mare.chant.en.0:2", "mare"))));
        return new TestSpell("wizardreal:mare", pron("wizardreal:mare", "mare"), -1f, List.of(variantA));
    }

    @AfterEach
    void clearRegistry() {
        SpellRegistry.clear();
    }

    @Test
    void instantTriggerRoutesInstant() {
        SpellRegistry.replace(instant());
        SpellRegistry.replace(ritual());
        ChantGate.Decision d = ChantGate.route("ignis", null, Map.of());
        assertEquals(ChantGate.Kind.INSTANT, d.kind());
        assertEquals("wizardreal:ignis", d.spell().id());
    }

    @Test
    void ritualL1TextRoutesEnter() {
        SpellRegistry.replace(instant());
        SpellRegistry.replace(ritual());
        ChantGate.Decision d = ChantGate.route("o tide and storm", null, Map.of());
        assertEquals(ChantGate.Kind.ENTER, d.kind());
        assertEquals("wizardreal:mare", d.spell().id());
        assertEquals(0, d.variant());
    }

    @Test
    void templateScoresLineIdRoutesEnter() {
        SpellRegistry.replace(ritual());
        ChantGate.Decision d = ChantGate.route("whatever", List.of(),
                Map.of("wizardreal:mare.chant.en.0:0", 0.85f));
        assertEquals(ChantGate.Kind.ENTER, d.kind());
        assertEquals(0, d.variant());
    }

    @Test
    void templateScoresMidLineIsIgnored() {
        SpellRegistry.replace(ritual());
        ChantGate.Decision d = ChantGate.route("whatever", List.of(),
                Map.of("wizardreal:mare.chant.en.0:1", 0.9f));
        assertEquals(ChantGate.Kind.NONE, d.kind());
    }

    @Test
    void ritualTriggerOrSpellNameRoutesSkip() {
        SpellRegistry.replace(ritual());
        // Trigger id via templateScores...
        ChantGate.Decision viaTrigger = ChantGate.route("mare", null,
                Map.of("wizardreal:mare", 0.9f));
        assertEquals(ChantGate.Kind.SKIP, viaTrigger.kind());
        // ...and via the text matcher fallback (cast line == trigger word).
        ChantGate.Decision viaText = ChantGate.route("mare", null, Map.of());
        assertEquals(ChantGate.Kind.SKIP, viaText.kind());
    }

    @Test
    void enterBeatsSkipWhenBothMatch() {
        SpellRegistry.replace(ritual());
        // L1 text match wins over the spell-name SKIP candidate from scores.
        ChantGate.Decision d = ChantGate.route("o tide and storm", null,
                Map.of("wizardreal:mare.chant.en.0:2", 0.9f));
        assertEquals(ChantGate.Kind.ENTER, d.kind());
    }

    @Test
    void belowThresholdScoresRouteNone() {
        SpellRegistry.replace(instant());
        ChantGate.Decision d = ChantGate.route("ignis", null, Map.of("wizardreal:ignis", 0.3f));
        // Scores are below threshold, but the text fallback still matches.
        assertEquals(ChantGate.Kind.INSTANT, d.kind());
        // Nothing matches at all.
        assertTrue(ChantGate.route("zzz qqq", null, Map.of()).kind() == ChantGate.Kind.NONE);
    }
}
