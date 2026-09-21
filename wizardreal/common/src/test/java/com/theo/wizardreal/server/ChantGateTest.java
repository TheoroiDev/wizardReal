package com.theo.wizardreal.server;

import com.theo.voicecast.api.Alternative;
import com.theo.voicecast.api.Decision;
import com.theo.voicecast.api.RecognitionResult;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Pronunciation;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Idle-state semantic gate (D9), re-keyed onto the voicecast Decision by the
 * C1b contract: the gate consumes {@code Decision + spellId + pronId + score
 * + alternatives} and maps them onto gameplay kinds (ENTER / INSTANT / SKIP
 * / NONE). The matching itself (what was said) is voicecast's — the shared
 * equivalence vectors (ChantGateEquivalenceVectorTest) pin the cross-repo
 * outcomes; this class pins the gate's own branch semantics.
 */
class ChantGateTest {

    private static Pronunciation pron(String id, String... aliases) {
        return new Pronunciation(id, List.of(), List.of(aliases));
    }

    private static RecognitionResult exact(String spellId, String pronId, float score, Alternative... alts) {
        return RecognitionResult.finality("heard", "", "", Decision.EXACT, spellId, pronId, score,
                List.of(alts), 0L);
    }

    private static RecognitionResult near(String spellId, String pronId, float score) {
        return RecognitionResult.finality("heard", "", "", Decision.NEAR, spellId, pronId, score,
                List.of(), 0L);
    }

    private static Alternative alt(String spellId, String pronId, float score) {
        return new Alternative(spellId, pronId, score);
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
        ChantGate.Decision d = ChantGate.route(exact("wizardreal:ignis", "wizardreal:ignis", 1.0f));
        assertEquals(ChantGate.Kind.INSTANT, d.kind());
        assertEquals("wizardreal:ignis", d.spell().id());
    }

    @Test
    void firstLineDecisionRoutesEnter() {
        SpellRegistry.replace(ritual());
        ChantGate.Decision d = ChantGate.route(exact("wizardreal:mare", "wizardreal:mare.chant.en.0:0", 0.85f));
        assertEquals(ChantGate.Kind.ENTER, d.kind());
        assertEquals(0, d.variant());
    }

    @Test
    void midLineIsNeverACandidate() {
        SpellRegistry.replace(ritual());
        // middle lines carry no idle meaning — voicecast never elects them,
        // and the gate ignores the id if one ever arrives.
        ChantGate.Decision d = ChantGate.route(exact("wizardreal:mare", "wizardreal:mare.chant.en.0:1", 0.9f));
        assertEquals(ChantGate.Kind.NONE, d.kind());
    }

    @Test
    void ritualTriggerOrSpellNameRoutesSkip() {
        SpellRegistry.replace(ritual());
        // Trigger id decision...
        ChantGate.Decision viaTrigger = ChantGate.route(exact("wizardreal:mare", "wizardreal:mare", 0.9f));
        assertEquals(ChantGate.Kind.SKIP, viaTrigger.kind());
        // ...and the spell-name (last chant line) decision.
        ChantGate.Decision viaLastLine = ChantGate.route(
                exact("wizardreal:mare", "wizardreal:mare.chant.en.0:2", 0.9f));
        assertEquals(ChantGate.Kind.SKIP, viaLastLine.kind());
    }

    @Test
    void firstLineAlternativePromotesOverSkipDecision() {
        SpellRegistry.replace(ritual());
        // 首行即门: the decision is the trigger (SKIP), but a first-line
        // runner-up in the alternatives ENTERs (the pre-v2 L1-gate priority).
        ChantGate.Decision d = ChantGate.route(exact("wizardreal:mare", "wizardreal:mare", 1.0f,
                alt("wizardreal:mare", "wizardreal:mare.chant.en.0:0", 0.85f)));
        assertEquals(ChantGate.Kind.ENTER, d.kind());
        assertEquals(0, d.variant());
    }

    @Test
    void level1KeepsExact_firstLineOverCtcSkip() {
        // Pre-v2 case (enterBeatsSkipWhenBothMatch, level 1): the lenient
        // first-line hit beat the passing last-line CTC score. In v2 the
        // fusion elects the first line (tier 1) and the gate keeps it.
        SpellRegistry.replace(ritual());
        ChantGate.Decision d = ChantGate.route(
                exact("wizardreal:mare", "wizardreal:mare.chant.en.0:0", 1.0f), 1);
        assertEquals(ChantGate.Kind.ENTER, d.kind());
    }

    @Test
    void level2CtcAuthoritativeSkip() {
        // Pre-v2 case (enterBeatsSkipWhenBothMatch, level 2): level 2
        // suppressed the lenient L1 surface (now a push-time hint overlay, so
        // voicecast elects the last line) — the gate maps it to SKIP.
        SpellRegistry.replace(ritual());
        ChantGate.Decision d = ChantGate.route(
                exact("wizardreal:mare", "wizardreal:mare.chant.en.0:2", 0.9f), 2);
        assertEquals(ChantGate.Kind.SKIP, d.kind());
    }

    @Test
    void rejectedDecisionRoutesNone() {
        SpellRegistry.replace(instant());
        RecognitionResult rejected = RecognitionResult.finality("zzz qqq", "", "",
                Decision.REJECTED, "", "", 0f, List.of(), 0L);
        assertEquals(ChantGate.Kind.NONE, ChantGate.route(rejected, 0).kind());
        // nothing matches at all
        assertTrue(ChantGate.route(rejected).kind() == ChantGate.Kind.NONE);
    }

    // ------------------------------------------------- rejection (误触发治理)

    @Test
    void level1RejectsNearDecision() {
        SpellRegistry.replace(instant());
        // C1b re-keying: the pre-v2 "ctcPresent && level >= 1 suppresses the
        // matcher fallbacks" branch is now Decision-keyed — NEAR never casts
        // at level >= 1 (the push-time hint overlay already suppresses the
        // surfaces, this is the gate-side backstop).
        assertEquals(ChantGate.Kind.NONE,
                ChantGate.route(near("wizardreal:ignis", "wizardreal:ignis", 0.8f), 1).kind());
        // Level 0 keeps the legacy acceptance.
        assertEquals(ChantGate.Kind.INSTANT,
                ChantGate.route(near("wizardreal:ignis", "wizardreal:ignis", 0.8f), 0).kind());
    }

    @Test
    void level2SuppressedSurfacesArriveAsRejections() {
        // The pre-v2 "level 2 also suppresses the lenient L1 gate" branch is a
        // push-time hint overlay now: voicecast REJECTS what the overlay
        // killed, and the gate maps every rejection to NONE.
        SpellRegistry.replace(ritual());
        RecognitionResult rejected = RecognitionResult.finality("o tide and storm", "", "",
                Decision.REJECTED, "", "", 0f, List.of(), 0L);
        assertEquals(ChantGate.Kind.NONE, ChantGate.route(rejected, 2).kind());
        // A real L1 utterance that clears the evidence still ENTERs (首行即门).
        assertEquals(ChantGate.Kind.ENTER, ChantGate.route(
                exact("wizardreal:mare", "wizardreal:mare.chant.en.0:0", 0.85f), 2).kind());
    }

    @Test
    void passingExactStillDecidesUnderRejection() {
        SpellRegistry.replace(instant());
        ChantGate.Decision d = ChantGate.route(exact("wizardreal:ignis", "wizardreal:ignis", 0.9f), 2);
        assertEquals(ChantGate.Kind.INSTANT, d.kind());
    }

    // ------------------------------------------------- defensive mapping

    @Test
    void ambiguousAndPartialRouteNone() {
        SpellRegistry.replace(instant());
        RecognitionResult ambiguous = RecognitionResult.finality("heard", "", "",
                Decision.AMBIGUOUS, "wizardreal:ignis", "wizardreal:ignis", 0.67f, List.of(), 0L);
        assertEquals(ChantGate.Kind.NONE, ChantGate.route(ambiguous).kind());
        // partial results carry no decision
        assertEquals(ChantGate.Kind.NONE, ChantGate.route(RecognitionResult.partial("heard", "")).kind());
        assertEquals(ChantGate.Kind.NONE, ChantGate.route(null).kind());
    }

    @Test
    void unknownSpellOrPronunciationRouteNone() {
        SpellRegistry.replace(instant());
        assertEquals(ChantGate.Kind.NONE, ChantGate.route(
                exact("wizardreal:other", "wizardreal:other", 1.0f)).kind());
        // registered spell, foreign pronunciation id (e.g. an addon vocabulary row)
        assertEquals(ChantGate.Kind.NONE, ChantGate.route(
                exact("wizardreal:ignis", "addon:foreign", 1.0f)).kind());
    }
}
