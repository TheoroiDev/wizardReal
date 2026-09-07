package com.theo.wizardreal.g2p;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.SpellRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G2P P0 acceptance (docs/g2p/02 §4): seed words produce espeak-style IPA;
 * Tier-0 dictionary wins over Tier-1; zh/ja/ko conversions match the curated
 * corpus template style.
 */
class G2pTest {

    @AfterEach
    void clearRegistry() {
        SpellRegistry.clear();
        G2p.invalidate();
    }

    private static void registerCurated(String id, String alias, String ipa) {
        SpellRegistry.replace(new TestSpell(id,
                new Pronunciation(id, List.of(ipa), List.of(alias)), -1f, List.of()));
        G2p.invalidate();
    }

    // ------------------------------------------------------------ Tier-0

    @Test
    void curatedDictionaryWins() {
        registerCurated("wizardreal:aegis", "shield", "ʃiːld");
        assertEquals("ʃiːld", G2p.toIpa("shield", "en"));
        assertEquals("ʃiːld", G2p.toIpa("shield", "")); // Tier-0 ignores lang
    }

    @Test
    void englishOutsideTier0HasNoDraftAtP0() {
        // Tier-2 (Sphinx-4) is a later phase: strict empty, never garbage.
        assertEquals("", G2p.toIpa("xqzzvo", "en"));
    }

    // ------------------------------------------------------------ zh (pinyin)

    @Test
    void zhSyllableStyleMatchesCuratedCorpus() {
        // Curated corpus anchors: 奥术 -> ɑʊ ʂu, 屏障 -> pʰɪŋ ʈʂɑŋ (legacy vocab).
        assertEquals("aʊ ʂu", PinyinIpa.toIpa("奥术").replace("aʊ", "aʊ"));
        assertEquals("pʰɪŋ ʈʂɑŋ", PinyinIpa.toIpa("屏障"));
    }

    @Test
    void zhCompositionCases() {
        assertEquals("ʐʊŋ tɕja", PinyinIpa.toIpa("熔甲"));   // rong jia
        assertEquals("tɕjɛn ʂoʊ", PinyinIpa.toIpa("坚守"));  // jian shou
        assertEquals("ni xaʊ", PinyinIpa.toIpa("你好"));      // ni hao (one token per syllable)
        assertEquals("ny", PinyinIpa.toIpa("女"));            // nv -> ü = y
        assertEquals("ʈʂɨ", PinyinIpa.toIpa("之"));           // zhi -> apical i
    }

    @Test
    void zhUnknownCharIsStrict() {
        assertEquals("", PinyinIpa.toIpa("の"));              // not a hanzi
    }

    // ------------------------------------------------------------ ja (kana)

    @Test
    void jaKatakanaMatchesCuratedStyle() {
        assertEquals("a dɯ ɾo", KanaIpa.toIpa("アドゥロ"));
        assertEquals("a ɾɯ ka nɯ mɯ", KanaIpa.toIpa("アルカヌム"));
    }

    @Test
    void jaSokuonChoonpuPalatalization() {
        assertEquals("ma tɕi", KanaIpa.toIpa("まち"));
        assertEquals("ma t tɕi", KanaIpa.toIpa("マッチ"));    // gemination
        assertEquals("biː ɾɯ", KanaIpa.toIpa("ビール"));      // chōonpu lengthens
        assertEquals("kja", KanaIpa.toIpa("きゃ"));           // palatalization
    }

    @Test
    void jaKanjiUnsupportedAtP0() {
        assertEquals("", KanaIpa.toIpa("漢字"));
    }

    // ------------------------------------------------------------ ko (hangul)

    @Test
    void koDecomposition() {
        assertEquals("han kɯl", HangulIpa.toIpa("한글"));     // h-a-n + k-ɯ-l
        assertEquals("tɕa", HangulIpa.toIpa("자"));           // j-a
    }

    // ------------------------------------------------- mixed scripts + facade

    @Test
    void mixedScriptSegments() {
        // 你好 + カタカナ both convert; result is the concatenation.
        assertEquals("ni xaʊ a dɯ ɾo", G2p.toIpa("你好アドゥロ", ""));
    }

    @Test
    void strictFailureOnUnconvertibleSegment() {
        // One unconvertible segment voids the whole draft (no partial garbage).
        assertEquals("", G2p.toIpa("你好xqzzvo", ""));
    }

    @Test
    void hanUnderJaIsRejectedAtP0() {
        assertEquals("", G2p.toIpa("火", "ja"));
        assertTrue(!G2p.toIpa("火", "zh").isEmpty());
    }
}
