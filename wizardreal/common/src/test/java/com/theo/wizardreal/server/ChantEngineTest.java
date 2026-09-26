package com.theo.wizardreal.server;

import com.theo.voicecast.match.LenientLine;
import com.theo.wizardreal.api.Pronunciation;
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

    private static Spell cjkRitualSpell() {
        Chant zh = new Chant(List.of(
                new ChantLine("key.zh0", pron("以雷暴之名应召")),
                new ChantLine("key.zh1", pron("织电光入我言")),
                new ChantLine("key.zh2", pron("雷暴"))));
        return new TestSpell("wizardreal:cjk", null, -1f, List.of(zh));
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
        ChantEngine.FeedResult r = engine.feed("fire burn", null, T0 + ChantEngine.DEFAULT_TIMEOUT_MS + 1);
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
        assertTrue(LenientLine.looseText("oh mighty fire burn loudly!", "Fire, Burn"));
        // Exact match.
        assertTrue(LenientLine.looseText("explosion", "explosion"));
        // Punctuation/case normalized away.
        assertTrue(LenientLine.looseText("Explosion!", "explosion"));
        // Token coverage: only 1 of 2 tokens -> not a line.
        assertFalse(LenientLine.looseText("fire", "fire burn"));
        // 2 of 2 tokens (>=75%) -> matches.
        assertTrue(LenientLine.looseText("burning fire", "fire burn"));
        // Single-word line: fuzzy full-string match.
        assertTrue(LenientLine.looseText("igniss", "ignis"));
        assertFalse(LenientLine.looseText("xyzwvu", "ignis"));
        assertFalse(LenientLine.looseText("", "ignis"));
    }

    @Test
    void loosePhoneticRules() {
        assertTrue(LenientLine.loosePhonetic("f ʊ l m ɛ n", "ˈfʊlmɛn"));
        assertTrue(LenientLine.loosePhonetic("fu mn", "fulmen")); // 0.667 >= 0.6
        assertFalse(LenientLine.loosePhonetic("xxxx", "fulmen"));
        assertFalse(LenientLine.loosePhonetic("anything", ""));
    }

    @Test
    void timeoutIsPureClockMath() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        assertFalse(engine.timedOut(T0 + ChantEngine.DEFAULT_TIMEOUT_MS));
        assertTrue(engine.timedOut(T0 + ChantEngine.DEFAULT_TIMEOUT_MS + 1));
        // A consumed (non-timeout) feed resets the idle clock.
        engine.feed("ignis", null, T0 + 5000);
        assertFalse(engine.timedOut(T0 + 5000 + ChantEngine.DEFAULT_TIMEOUT_MS));
        assertTrue(engine.timedOut(T0 + 5000 + ChantEngine.DEFAULT_TIMEOUT_MS + 1));
    }

    // ---- D9 entry rework -------------------------------------------------

    @Test
    void preLockedStartCountsEntryAsLineOne() {
        // The idle L1 gate matched the entry utterance: the engine starts
        // pre-locked at variant 0 with line 1 already complete.
        ChantEngine engine = new ChantEngine(ritualSpell(), T0, 90_000L, 0);
        assertEquals(3, engine.currentLineCount());
        ChantEngine.FeedResult r = engine.feed("fire burn", null, T0 + 100);
        assertEquals(new ChantEngine.Progress(0, 2, false), r.progress().get(0));
        assertFalse(r.earlyRelease());
    }

    @Test
    void spellNameMidChantReleasesEarly() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        // Line 2 expected, but the spell name (last line) is spoken -> 断章.
        ChantEngine.FeedResult r = engine.feed("Explosion!!!", null, T0 + 2000);
        assertTrue(r.consumed());
        assertTrue(r.earlyRelease());
        assertFalse(r.finished());
        assertEquals(1, r.completedLines());
    }

    @Test
    void injectedTimeoutReplacesDefault() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0, 30_000L, 0);
        assertFalse(engine.timedOut(T0 + 30_000L));
        assertTrue(engine.timedOut(T0 + 30_000L + 1));
    }

    @Test
    void consecutiveWrongLinesFailTheChant() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        long t = T0 + 10_000;
        ChantEngine.FeedResult third = null;
        for (int i = 0; i < 3; i++) {
            third = engine.feed("totally different words", null, t);
            t += 1500;
        }
        assertTrue(third.failed());
        assertTrue(third.consumed());
        assertFalse(third.finished());
    }

    @Test
    void wrongLineThenCorrectLineResetsStreak() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        long t = T0 + 10_000;
        engine.feed("wrong words here", null, t);
        engine.feed("more wrong stuff", null, t + 1500);
        // Correct line: streak resets; two more wrong lines must NOT fail.
        ChantEngine.FeedResult ok = engine.feed("fire burn", null, t + 3000);
        assertTrue(ok.progress().get(0).lineIndex() == 2);
        engine.feed("wrong again now", null, t + 4500);
        ChantEngine.FeedResult notFailed = engine.feed("still wrong here", null, t + 6000);
        assertFalse(notFailed.failed());
    }

    @Test
    void rollbackLineRetreatsOneLine() {
        ChantEngine engine = new ChantEngine(ritualSpell(), T0);
        engine.feed("ignis", null, T0);
        engine.feed("fire burn", null, T0 + 1000);
        assertEquals(2, engine.currentLineCount() - 1); // on line index 2
        ChantEngine.Progress p = engine.rollbackLine(T0 + 2000);
        assertEquals(new ChantEngine.Progress(0, 1, false), p);
        // The retreated line must be spoken again.
        ChantEngine.FeedResult r = engine.feed("fire burn", null, T0 + 3000);
        assertEquals(new ChantEngine.Progress(0, 2, false), r.progress().get(0));
    }

    @Test
    void cjkTextAdvancesTheLine() {
        // voiceCast#47 follow-up: the hasText gate stripped CJK via an
        // ASCII-only regex, silently swallowing every zh/ja text-lane chant
        // line (qwen3 progression 0% in the chant-sequence bench).
        ChantEngine ce = new ChantEngine(cjkRitualSpell(), T0, 600_000L, 0);
        var fr = ce.feed("织电光入我言", List.of(), T0 + 6_000L);
        assertTrue(fr.consumed());
        assertTrue(fr.progress().stream().anyMatch(p -> p.lineIndex() >= 2));
    }

    @Test
    void cjkWrongLineStillCountsAsWrong() {
        ChantEngine ce = new ChantEngine(cjkRitualSpell(), T0, 600_000L, 0);
        var fr = ce.feed("完全无关的一句话", List.of(), T0 + 6_000L);
        assertTrue(fr.consumed());
        assertTrue(fr.progress().stream().anyMatch(p -> p.error()));
    }
}
