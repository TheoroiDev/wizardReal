package com.theo.wizardreal.server;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChantEngineTest {

    private static final long T0 = 1_000_000L;

    private static Pronunciation pron(String... aliases) {
        return new Pronunciation("p", List.of(), List.of(aliases));
    }

    private static Spell ritualSpell() {
        Chant variantA = new Chant(List.of(
                new ChantLine("key.a1", pron("ignis")),
                new ChantLine("key.a2", pron("fire burn")),
                new ChantLine("key.a3", pron("explosion"))));
        Chant variantB = new Chant(List.of(
                new ChantLine("key.b1", pron("glacius")),
                new ChantLine("key.b2", pron("ice freeze")),
                new ChantLine("key.a3", pron("explosion"))));
        return new TestSpell("wizardreal:ritual", null, -1f, List.of(variantA, variantB));
    }

    @Test
    void wrongFirstLineStaysSilentDuringOpeningGrace() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        ChantEngine.FeedResult r1 = engine.feed("noise blip", null, T0 + 100);
        assertTrue(r1.consumed());
        assertTrue(r1.progress().isEmpty());
        ChantEngine.FeedResult r2 = engine.feed("more noise", null, T0 + 900);
        assertTrue(r2.progress().isEmpty());
        // Past the grace window the error flash appears (hardcoded variant 0/line 0).
        ChantEngine.FeedResult r3 = engine.feed("more noise", null, T0 + 1500);
        assertEquals(1, r3.progress().size());
        assertEquals(new ChantEngine.Progress(0, 0, true), r3.progress().get(0));
        assertFalse(r3.finished());
    }

    @Test
    void firstLineLocksVariant() {
        ChantEngine a = new ChantEngine(ritualSpell(), T0);
        ChantEngine.FeedResult ra = a.feed("Ignis!", null, T0 + 100);
        assertEquals(new ChantEngine.Progress(0, 1, false), ra.progress().get(0));
        assertFalse(ra.finished());

        ChantEngine b = new ChantEngine(ritualSpell(), T0);
        ChantEngine.FeedResult rb = b.feed("glacius", null, T0 + 100);
        assertEquals(new ChantEngine.Progress(1, 1, false), rb.progress().get(0));
    }

    @Test
    void wrongLineRetriesSameLineWithProgressKept() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0 + 100);
        ChantEngine.FeedResult wrong = engine.feed("totally different words", null, T0 + 2000);
        assertEquals(new ChantEngine.Progress(0, 1, true), wrong.progress().get(0));
        // Same line is retried and advances normally afterwards.
        ChantEngine.FeedResult retry = engine.feed("fire burn", null, T0 + 2500);
        assertEquals(new ChantEngine.Progress(0, 2, false), retry.progress().get(0));
    }

    @Test
    void completingTheLastLineFinishes() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        engine.feed("fire burn", null, T0 + 1000);
        ChantEngine.FeedResult last = engine.feed("Explosion!!!", null, T0 + 2000);
        assertFalse(last.timeout());
        assertTrue(last.finished());
        assertTrue(last.progress().isEmpty() || !last.progress().get(last.progress().size() - 1).error());
    }

    @Test
    void singleLineChantCompletesOnFirstLine() {
        Spell spell = new TestSpell("wizardreal:short", null, -1f, List.of(
                new Chant(List.of(new ChantLine("key.s1", pron("abracadabra"))))));
        ChantEngine engine = new ChantEngine(spell, T0);
        ChantEngine.FeedResult r = engine.feed("abracadabra", null, T0 + 100);
        assertTrue(r.finished());
    }

    @Test
    void idleChantTimesOut() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        ChantEngine.FeedResult r = engine.feed("fire burn", null, T0 + ChantEngine.TIMEOUT_MS + 1);
        assertTrue(r.consumed());
        assertTrue(r.timeout());
        assertFalse(r.finished());
    }

    @Test
    void noiseUtterancesAreSwallowedWithoutPenalty() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        for (String noise : new String[]{"", "a", "  "}) {
            ChantEngine.FeedResult r = engine.feed(noise, null, T0 + 100);
            assertTrue(r.consumed());
            assertTrue(r.progress().isEmpty());
        }
        ChantEngine.FeedResult r = engine.feed(null, null, T0 + 200);
        assertTrue(r.consumed());
        assertTrue(r.progress().isEmpty());
        // Progress was kept: the next real line advances from index 1.
        ChantEngine.FeedResult next = engine.feed("fire burn", null, T0 + 300);
        assertEquals(new ChantEngine.Progress(0, 2, false), next.progress().get(0));
    }

    @Test
    void shortIpaOnlyUtterancesCountAsNoise() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        // a single IPA token is below the >=2 threshold -> swallowed
        ChantEngine.FeedResult r = engine.feed(null, List.of("f"), T0 + 100);
        assertTrue(r.progress().isEmpty());
        // two tokens go through the phonetic path
        ChantEngine.FeedResult r2 = engine.feed(null, List.of("f", "uː"), T0 + 200);
        assertTrue(r2.progress().isEmpty() || r2.progress().get(0).error());
    }

    @Test
    void looseTextRules() {
        // Whole line spoken with filler words.
        assertTrue(ChantEngine.looseText("oh mighty fire burn loudly!", "Fire, Burn"));
        // Exact match.
        assertTrue(ChantEngine.looseText("explosion", "explosion"));
        // Punctuation/case normalized away.
        assertTrue(ChantEngine.looseText("Explosion!", "explosion"));
        // Token coverage: only 1 of 2 tokens -> not a line.
        assertFalse(ChantEngine.looseText("fire", "fire burn"));
        // 2 of 2 tokens (>=75%) -> matches.
        assertTrue(ChantEngine.looseText("burning fire", "fire burn"));
        // Single-word line: fuzzy full-string match.
        assertTrue(ChantEngine.looseText("igniss", "ignis"));
        assertFalse(ChantEngine.looseText("xyzwvu", "ignis"));
        assertFalse(ChantEngine.looseText("", "ignis"));
    }

    @Test
    void loosePhoneticRules() {
        assertTrue(ChantEngine.loosePhonetic("f ʊ l m ɛ n", "ˈfʊlmɛn"));
        assertTrue(ChantEngine.loosePhonetic("fu mn", "fulmen")); // 0.667 >= 0.6
        assertFalse(ChantEngine.loosePhonetic("xxxx", "fulmen"));
        assertFalse(ChantEngine.loosePhonetic("anything", ""));
    }

    @Test
    void timeoutIsPureClockMath() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        assertFalse(engine.timedOut(T0 + ChantEngine.TIMEOUT_MS));
        assertTrue(engine.timedOut(T0 + ChantEngine.TIMEOUT_MS + 1));
        // A consumed (non-timeout) feed resets the idle clock.
        engine.feed("ignis", null, T0 + 5000);
        assertFalse(engine.timedOut(T0 + 5000 + ChantEngine.TIMEOUT_MS));
        assertTrue(engine.timedOut(T0 + 5000 + ChantEngine.TIMEOUT_MS + 1));
    }
}
