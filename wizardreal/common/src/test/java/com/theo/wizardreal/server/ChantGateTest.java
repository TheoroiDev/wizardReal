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
        System.clearProperty("wizardreal.voice.rejectLevel");
    }

    private static void rejectLevel(int level) {
        System.setProperty("wizardreal.voice.rejectLevel", String.valueOf(level));
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
        // Levels 0/1 keep the legacy priority: the L1 text match beats the
        // spell-name SKIP candidate from scores.
        rejectLevel(1);
        ChantGate.Decision d = ChantGate.route("o tide and storm", null,
                Map.of("wizardreal:mare.chant.en.0:2", 0.9f));
        assertEquals(ChantGate.Kind.ENTER, d.kind());
        // Level 2 is CTC-authoritative: the passing last-line score wins and
        // the lenient L1 gate is not consulted.
        rejectLevel(2);
        ChantGate.Decision authoritative = ChantGate.route("o tide and storm", null,
                Map.of("wizardreal:mare.chant.en.0:2", 0.9f));
        assertEquals(ChantGate.Kind.SKIP, authoritative.kind());
    }

    @Test
    void belowThresholdScoresRouteNone() {
        rejectLevel(0);
        SpellRegistry.replace(instant());
        ChantGate.Decision d = ChantGate.route("ignis", null, Map.of("wizardreal:ignis", 0.3f));
        // Scores are below threshold, but the text fallback still matches.
        assertEquals(ChantGate.Kind.INSTANT, d.kind());
        // Nothing matches at all.
        assertTrue(ChantGate.route("zzz qqq", null, Map.of()).kind() == ChantGate.Kind.NONE);
    }

    // ------------------------------------------------- rejection (误触发治理)

    @Test
    void level1RejectsTextFallbackWhenCtcPresent() {
        rejectLevel(1);
        SpellRegistry.replace(instant());
        // CTC scores present but below threshold: the snap-to-nearest text
        // fallback is suppressed — no cast from ambiguous evidence.
        ChantGate.Decision d = ChantGate.route("ignis", null, Map.of("wizardreal:ignis", 0.3f));
        assertEquals(ChantGate.Kind.NONE, d.kind());
        // Without scores the fallbacks still run.
        assertEquals(ChantGate.Kind.INSTANT, ChantGate.route("ignis", null, Map.of()).kind());
    }

    @Test
    void level2AlsoSuppressesLenientL1Gate() {
        SpellRegistry.replace(ritual());
        // Level 1 keeps the lenient L1 gate: ritual entry still works...
        rejectLevel(1);
        assertEquals(ChantGate.Kind.ENTER,
                ChantGate.route("o tide and storm", null, Map.of("wizardreal:mare", 0.2f)).kind());
        // ...but level 2 suppresses it when CTC evidence is present.
        rejectLevel(2);
        assertEquals(ChantGate.Kind.NONE,
                ChantGate.route("o tide and storm", null, Map.of("wizardreal:mare", 0.2f)).kind());
        // A real L1 utterance that passes CTC still ENTERs (首行即门).
        ChantGate.Decision d = ChantGate.route("whatever", List.of(),
                Map.of("wizardreal:mare.chant.en.0:0", 0.85f));
        assertEquals(ChantGate.Kind.ENTER, d.kind());
    }

    @Test
    void passingCtcStillDecidesUnderRejection() {
        rejectLevel(2);
        SpellRegistry.replace(instant());
        ChantGate.Decision d = ChantGate.route("ignis", null, Map.of("wizardreal:ignis", 0.9f));
        assertEquals(ChantGate.Kind.INSTANT, d.kind());
    }

    // ------------------------------------------------- language trim (裁剪)

    @Test
    void languageTrimDropsForeignTemplateScores() {
        rejectLevel(0);
        SpellRegistry.replace(ritualZh());
        // zh not enabled: the zh line score is invisible to the router.
        ChantGate.Decision d = ChantGate.route("whatever", List.of(),
                Map.of("wizardreal:mare.chant.zh.0:0", 0.95f), java.util.Set.of("en"));
        assertEquals(ChantGate.Kind.NONE, d.kind());
        // zh enabled: ENTER fires (line 0, 首行即门).
        ChantGate.Decision ok = ChantGate.route("whatever", List.of(),
                Map.of("wizardreal:mare.chant.zh.0:0", 0.95f), java.util.Set.of("en", "zh"));
        assertEquals(ChantGate.Kind.ENTER, ok.kind());
    }

    /** Ritual whose only variant is zh-bucketed (ids embed the language). */
    private static Spell ritualZh() {
        Chant variant = new Chant(List.of(
                new ChantLine(null, pron("wizardreal:mare.chant.zh.0:0", "潮汐听我号令")),
                new ChantLine(null, pron("wizardreal:mare.chant.zh.0:1", "淹没战场")),
                new ChantLine(null, pron("wizardreal:mare.chant.zh.0:2", "玛蕾"))));
        return new TestSpell("wizardreal:mare", pron("wizardreal:mare", "mare"), -1f, List.of(variant));
    }
}
