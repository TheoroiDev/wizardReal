package com.theo.wizardreal.g2p;

import com.theo.wizardreal.api.Pronunciation;
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
        // j/q/x + ü orthography (data table stores standard spellings)
        assertEquals("tɕyn", PinyinIpa.toIpa("军"));          // jun -> jün
        assertEquals("ɕɥɛ", PinyinIpa.toIpa("雪"));           // xue -> xüe
        assertEquals("tɕʰyn", PinyinIpa.toIpa("群"));         // qun -> qün
        assertEquals("ɕɥɛn", PinyinIpa.toIpa("选"));          // xuan -> xüan
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
        assertEquals("ma tɕ tɕi", KanaIpa.toIpa("マッチ"));   // gemination (full onset)
        assertEquals("biː ɾɯ", KanaIpa.toIpa("ビール"));      // chōonpu lengthens
        assertEquals("kja", KanaIpa.toIpa("きゃ"));           // palatalization
        assertEquals("ɕa", KanaIpa.toIpa("しゃ"));            // sibilant row: no j-glide
        assertEquals("dʑo", KanaIpa.toIpa("ジョ"));
    }

    @Test
    void jaKanjiUnsupportedAtP0() {
        assertEquals("", KanaIpa.toIpa("漢字"));
    }

    @Test
    void jaKanjiReadingsViaCuratedTable() {
        // Phrase-level table (docs C4): known phrases -> kana -> IPA.
        assertEquals("sa i mi n", G2p.toIpa("催眠", "ja"));
        assertEquals("dʑi wa ɾɛ", G2p.toIpa("地割れ", "ja"));
        // Mixed kanji+kana: substitution must run BEFORE segmentation.
        assertEquals("kɛ n no ɾjo ɯ i ki", G2p.toIpa("剣の領域", "ja"));
        assertEquals("sɛ n no kɛ n", G2p.toIpa("千の剣", "ja"));
    }

    @Test
    void jaKanjiUnknownStaysStrict() {
        // Bare kanji not in the table -> no draft (fail closed, never zh pinyin).
        assertEquals("", G2p.toIpa("火", "ja"));
        assertEquals("", G2p.toIpa("紅蓮よ応えよ", "ja"));
        // Kana path unaffected.
        assertEquals("a i gi sɯ", G2p.toIpa("アイギス", "ja"));
    }

    // ------------------------------------------------------------ ko (hangul)

    @Test
    void koDecomposition() {
        assertEquals("han kɯl", HangulIpa.toIpa("한글"));     // h-a-n + k-ɯ-l
        assertEquals("tɕa", HangulIpa.toIpa("자"));           // j-a
    }

    // ---------------------------------------- R1 audit fixes (regression pins)

    @Test
    void zhInterjectionYo() {
        assertEquals("jo", PinyinIpa.toIpa("哟"));       // io rime exists (was strict-fail)
        assertEquals("jo", PinyinIpa.toIpa("唷"));
    }

    @Test
    void zhSyllabicNasal() {
        assertEquals("n", PinyinIpa.toIpa("嗯"));         // zero-rime syllable (was strict-fail)
        assertEquals("m", PinyinIpa.toIpa("呣"));
    }

    @Test
    void zhStandaloneO() {
        assertEquals("o", PinyinIpa.toIpa("哦"));         // not wɔ (that is the bo/po/mo/fo o=uo form)
    }

    @Test
    void zhUmlautNonPalatalOnset() {
        assertEquals("lyɛ", PinyinIpa.toIpa("略"));       // l+ü: main-vowel y, no palatal ɥ
        assertEquals("nyɛ", PinyinIpa.toIpa("虐"));   // n+ü: main-vowel y
    }

    @Test
    void zhErhuaAnchor() {
        assertEquals("ɚ", PinyinIpa.toIpa("二"));         // regression anchor (already green)
        assertEquals("xwa ɚ", PinyinIpa.toIpa("花儿"));
    }

    @Test
    void jaSokuonBeforeVowelDropsGemination() {
        assertEquals("a", KanaIpa.toIpa("っあ"));          // nothing to double (was "a a")
    }

    @Test
    void jaChoonpuWordStartIsStrict() {
        assertEquals("", KanaIpa.toIpa("ービール"));       // was silent drop
        assertEquals("", KanaIpa.toIpa("ンー"));           // ː only after vowels
    }

    @Test
    void jaStandaloneGlideKeepsLoneVowel() {
        assertEquals("a ja", KanaIpa.toIpa("あゃ"));  // R1 narrow-review fix (was "ja")
    }

    @Test
    void jaVuFamily() {
        assertEquals("bɯ", KanaIpa.toIpa("ゔ"));
        assertEquals("ba ɾɯ", KanaIpa.toIpa("ヴァル")); // ヴァ = single ba syllable
    }

    @Test
    void jaRareKanaStayStrict() {
        assertEquals("", KanaIpa.toIpa("ヷ"));             // above shift window, documented
    }

    @Test
    void koUiKeepsGlide() {
        assertEquals("ɰi sa", HangulIpa.toIpa("의사"));
    }

    @Test
    void digitsAndEmojiAreStrict() {
        assertEquals("", G2p.toIpa("3 fireballs", "en"));  // documented strict input surface
        assertEquals("", G2p.toIpa("🔥", ""));
    }

    @Test
    void nbspSplitsRuns() {
        // NBSP must separate runs (was: absorbed into an OTHER run -> strict "")
        assertEquals("xwɔ xaʊ", G2p.toIpa("火 好", ""));
    }

    @Test
    void astralPlaneHanIsStrict() {
        assertEquals("", G2p.toIpa("𠀀", ""));             // ext-B: declared, fail-closed
    }

    @Test
    void hanUnderEnIsRejected() {
        assertEquals("", G2p.toIpa("火", "en"));           // was: pinyin leaked under lang=en
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
