package com.theo.wizardreal.g2p;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Readings deriver acceptance (wo_a §1.3): language buckets zh/ja/en/ko,
 * hand-curated-ipa-wins, and the fail-closed absent-key paths. Keys fixed:
 * pinyin / romaji / ipa.
 */
class ReadingsTest {

    // ------------------------------------------------------------ zh

    @Test
    void zhDerivesTonedPinyinAndDerivedIpa() {
        Map<String, String> r = Readings.derive("真空刃", "zh", List.of());
        assertEquals(2, r.size());
        assertEquals("zhēn kōng rèn", r.get(Readings.PINYIN));
        assertEquals("ʈʂən kʰʊŋ ʐən", r.get(Readings.IPA)); // G2p draft (PinyinIpa chain)
    }

    @Test
    void zhHandCuratedIpaWins() {
        Map<String, String> r = Readings.derive("真空刃", "zh",
                List.of("ʈʂən kʰʊŋ ʐən"));
        assertEquals("ʈʂən kʰʊŋ ʐən", r.get(Readings.IPA));
        assertEquals("zhēn kōng rèn", r.get(Readings.PINYIN));
    }

    @Test
    void zhWhitespaceSplitsRuns() {
        Map<String, String> r = Readings.derive("火 好", "zh", List.of());
        assertEquals("huǒ hǎo", r.get(Readings.PINYIN));
    }

    @Test
    void zhPunctuationIsSeparatorNotFatal() {
        // punctuation/whitespace are separators (the hand IPA templates carry
        // no pause tokens — dropping keeps the reading same-source); curated
        // ipa still rides
        Map<String, String> r = Readings.derive("来兮，无羁之罡风", "zh",
                List.of("laɪ ɕi u tɕi ʈʂɨ kɑŋ fəŋ"));
        assertEquals(2, r.size());
        assertEquals("lái xī wú jī zhī gāng fēng", r.get(Readings.PINYIN));
        assertEquals("laɪ ɕi u tɕi ʈʂɨ kɑŋ fəŋ", r.get(Readings.IPA));
    }

    @Test
    void zhForeignLetterVoidsKey() {
        // a latin letter is CONTENT (not a separator): strict fail
        Map<String, String> r = Readings.derive("火x好", "zh", List.of());
        assertTrue(r.isEmpty(), "unknown letter -> no pinyin AND no derived ipa");
    }

    @Test
    void zhUnknownHanziVoidsEverything() {
        Map<String, String> r = Readings.derive("火ᛝ", "zh", List.of());
        assertTrue(r.isEmpty(), "unknown char -> no pinyin AND no derived ipa");
    }

    @Test
    void zhSupplementaryLetterVoidsKey() {
        // 𠮷 (U+20BB7, non-BMP) is a LETTER — content, not a separator: the
        // pinyin table has no such row -> key voided. Pre-fix the surrogate
        // pair was skipped as separators, yielding a partial "huǒ".
        assertTrue(Readings.derive("火𠮷", "zh", List.of()).isEmpty(),
                "non-BMP letter -> no pinyin AND no derived ipa");
    }

    @Test
    void zhLineWithKanaGetsIpaButNoPinyin() {
        // pinned asymmetry: G2p's KANA segment is language-agnostic (KanaIpa),
        // so a zh line containing kana derives an ipa draft but no pinyin —
        // mirrors the recognition chain, which scores kana the same way
        Map<String, String> r = Readings.derive("火アネモス", "zh", List.of());
        assertFalse(r.containsKey(Readings.PINYIN), "kana yields no pinyin in zh bucket");
        assertEquals("xwɔ a nɛ mo sɯ", r.get(Readings.IPA));
    }

    // ------------------------------------------------------------ ja

    @Test
    void jaKanaRomajiAndCuratedIpa() {
        Map<String, String> r = Readings.derive("アネモス", "ja",
                List.of("a nɛ mo sɯ"));
        assertEquals("a ne mo su", r.get(Readings.ROMAJI));
        assertEquals("a nɛ mo sɯ", r.get(Readings.IPA));
    }

    @Test
    void jaKanjiPhraseThroughCuratedTable() {
        Map<String, String> r = Readings.derive("地割れ", "ja", List.of());
        assertEquals("ji wa re", r.get(Readings.ROMAJI));   // KanjiIpa -> KanaRomaji
        assertEquals("dʑi wa ɾɛ", r.get(Readings.IPA));     // G2p draft (KanaIpa chain)
    }

    @Test
    void jaPunctuationIsSeparatorNotFatal() {
        // ・ splits runs (hand template "a kɯ a ɯ i ta ɛ" carries no pause token)
        Map<String, String> r = Readings.derive("アクア・ウィタエ", "ja",
                List.of("a kɯ a ɯ i ta ɛ"));
        assertEquals("a ku a wi ta e", r.get(Readings.ROMAJI));
        assertEquals("a kɯ a ɯ i ta ɛ", r.get(Readings.IPA));
    }

    @Test
    void jaUnknownKanjiStillVoidsRomaji() {
        // corpus reality: unknown kanji is CONTENT -> romaji + G2P draft fail closed
        Map<String, String> r = Readings.derive("アネモスよ、疾風を聴け", "ja", List.of());
        assertTrue(r.isEmpty());
    }

    @Test
    void jaSupplementaryLetterVoidsRomaji() {
        // 𠮷 (U+20BB7, non-BMP) rides inside the kana run -> KanaRomaji strict
        // "" -> key voided. Pre-fix the surrogate pair split/dropped, yielding
        // a partial "no".
        assertTrue(Readings.derive("𠮷の", "ja", List.of()).isEmpty(),
                "non-BMP letter -> no romaji AND no derived ipa");
    }

    // ------------------------------------------------------------ unannotated buckets

    @Test
    void enKoNeutralBucketsStayEmpty() {
        assertTrue(Readings.derive("anemos, hear the gale", "en", List.of()).isEmpty());
        assertTrue(Readings.derive("아네모스", "ko", List.of()).isEmpty());
        assertTrue(Readings.derive("some text", "", List.of()).isEmpty());
        assertTrue(Readings.derive("some text", null, List.of()).isEmpty());
    }

    @Test
    void emptyTextStaysEmpty() {
        assertTrue(Readings.derive("", "zh", List.of("x")).isEmpty());
        assertTrue(Readings.derive("   ", "ja", List.of()).isEmpty());
        assertTrue(Readings.derive(null, "zh", List.of()).isEmpty());
    }

    @Test
    void keySetIsFixed() {
        Map<String, String> r = Readings.derive("真空刃", "zh", List.of());
        for (String key : r.keySet()) {
            assertTrue(key.equals(Readings.PINYIN) || key.equals(Readings.IPA),
                    "unexpected key: " + key);
        }
    }
}
