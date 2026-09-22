package com.theo.wizardreal.g2p;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * KanaRomaji acceptance: Hepburn romaji for the annotation layer (wo_a §1.1 —
 * every category spelled out in the work order, expectations written dead).
 * Output style: mora-spaced, ん as coda, long vowels as macrons (class doc).
 */
class KanaRomajiTest {

    // ------------------------------------------------------------ clear/dakuten/handakuten

    @Test
    void gojuonRows() {
        assertEquals("a i u e o", KanaRomaji.toRomaji("あいうえお"));
        assertEquals("ka ki ku ke ko", KanaRomaji.toRomaji("かきくけこ"));
        assertEquals("ga gi gu ge go", KanaRomaji.toRomaji("がぎぐげご"));
        assertEquals("sa shi su se so", KanaRomaji.toRomaji("さしすせそ"));
        assertEquals("za ji zu ze zo", KanaRomaji.toRomaji("ざじずぜぞ"));
        assertEquals("ta chi tsu te to", KanaRomaji.toRomaji("たちつてと"));
        assertEquals("da de do", KanaRomaji.toRomaji("だでど"));
        assertEquals("ha hi fu he ho", KanaRomaji.toRomaji("はひふへほ"));
        assertEquals("ba bi bu be bo", KanaRomaji.toRomaji("ばびぶべぼ"));
        assertEquals("pa pi pu pe po", KanaRomaji.toRomaji("ぱぴぷぺぽ"));
    }

    @Test
    void katakanaByShift() {
        assertEquals("a ru ka nu mu", KanaRomaji.toRomaji("アルカヌム"));
        assertEquals("a ne mo su", KanaRomaji.toRomaji("アネモス"));
        assertEquals("ka ki ku ke ko", KanaRomaji.toRomaji("カキクケコ"));
    }

    // ------------------------------------------------------------ yōon (拗音)

    @Test
    void palatalizedRows() {
        assertEquals("kya", KanaRomaji.toRomaji("きゃ"));
        assertEquals("sha", KanaRomaji.toRomaji("しゃ"));   // sibilant: no j-glide
        assertEquals("cha", KanaRomaji.toRomaji("ちゃ"));
        assertEquals("nya", KanaRomaji.toRomaji("にゃ"));
        assertEquals("hya", KanaRomaji.toRomaji("ひゃ"));
        assertEquals("mya", KanaRomaji.toRomaji("みゃ"));
        assertEquals("rya", KanaRomaji.toRomaji("りゃ"));
        assertEquals("gya", KanaRomaji.toRomaji("ぎゃ"));
        assertEquals("ja", KanaRomaji.toRomaji("じゃ"));
        assertEquals("bya", KanaRomaji.toRomaji("びゃ"));
        assertEquals("pya", KanaRomaji.toRomaji("ぴゃ"));
        assertEquals("fyu", KanaRomaji.toRomaji("ふゅ"));   // foreign
        assertEquals("shu", KanaRomaji.toRomaji("しゅ"));   // sibilant y-merge: no "shyu"
        assertEquals("cho", KanaRomaji.toRomaji("ちょ"));
        assertEquals("ju", KanaRomaji.toRomaji("じゅ"));
        assertEquals("kya", KanaRomaji.toRomaji("キャ"));   // katakana same rule
        assertEquals("ja", KanaRomaji.toRomaji("ジャ"));
    }

    // ------------------------------------------------------------ sokuon (促音)

    @Test
    void sokuonGemination() {
        assertEquals("kka", KanaRomaji.toRomaji("っか"));
        assertEquals("tta", KanaRomaji.toRomaji("った"));
        assertEquals("sshi", KanaRomaji.toRomaji("っし"));
        assertEquals("tchi", KanaRomaji.toRomaji("っち"));  // ち-series doubles t (MLIT-style Hepburn)
        assertEquals("tcha", KanaRomaji.toRomaji("っちゃ"));
        assertEquals("tchu", KanaRomaji.toRomaji("っちゅ"));
        assertEquals("ma chi", KanaRomaji.toRomaji("まち"));
        assertEquals("ma tchi", KanaRomaji.toRomaji("マッチ"));
        assertEquals("shi ppū", KanaRomaji.toRomaji("しっぷう")); // gemination + long vowel
        assertEquals("a", KanaRomaji.toRomaji("っあ"));     // nothing to double: dropped
    }

    @Test
    void sokuonWordFinal() {
        // Terminal sokuon = apostrophe (documented convention; no letter for a
        // terminal glottal catch)
        assertEquals("a'", KanaRomaji.toRomaji("あっ"));
    }

    @Test
    void sokuonForeignMorae() {
        // gemination composes through small-kana foreign morae: the onset
        // doubles on the base kana, then the small-vowel composition lands
        assertEquals("tti", KanaRomaji.toRomaji("っティ"));
        assertEquals("ffa", KanaRomaji.toRomaji("っファ"));
    }

    @Test
    void sokuonBeforeNonSyllables() {
        // っ before ー/ん has nothing to double: the mark is dropped and the
        // geminate flag cleared (same rule as っ+vowel) — no phantom
        // terminal-sokuon apostrophe on the orphan
        assertEquals("ā", KanaRomaji.toRomaji("あっー"));
        assertEquals("n", KanaRomaji.toRomaji("っん"));
    }

    // ------------------------------------------------------------ long vowels (長音, Hepburn macron)

    @Test
    void longVowelMacrons() {
        assertEquals("tō kyō", KanaRomaji.toRomaji("とうきょう"));
        assertEquals("kyō", KanaRomaji.toRomaji("きょう"));
        assertEquals("bī ru", KanaRomaji.toRomaji("ビール"));
        assertEquals("ryō i ki", KanaRomaji.toRomaji("りょういき"));
        assertEquals("kō u ryō i ki", KanaRomaji.toRomaji("こううりょういき")); // 降-雨-領-域: kō + u, the second mora survives the merge
        assertEquals("ban kō", KanaRomaji.toRomaji("ばんこう"));
        assertEquals("ā", KanaRomaji.toRomaji("ああ"));
        assertEquals("ū", KanaRomaji.toRomaji("うう"));
        assertEquals("ē", KanaRomaji.toRomaji("えい"));     // え+い -> ē
        assertEquals("ō", KanaRomaji.toRomaji("おう"));     // お+う -> ō
        assertEquals("ō", KanaRomaji.toRomaji("おお"));
        assertEquals("ō", KanaRomaji.toRomaji("オー"));     // chōonpu
        assertEquals("ra i", KanaRomaji.toRomaji("らい"));  // a+i is NOT a long vowel: per-mora
    }

    @Test
    void consecutiveChoonpu() {
        // the second ー sees an already-macron vowel: no rewrite, no mora drop
        assertEquals("ō", KanaRomaji.toRomaji("オーー"));
    }

    // ------------------------------------------------------------ ん (拨音)

    @Test
    void moraicN() {
        // modern Hepburn: always n (classical m variant NOT adopted); coda attaches
        assertEquals("ni hon", KanaRomaji.toRomaji("にほん"));
        assertEquals("shin bun", KanaRomaji.toRomaji("しんぶん"));
        assertEquals("ken no ryō i ki", KanaRomaji.toRomaji("けんのりょういき"));
        assertEquals("gu ren no ra i wo me za me sa se yo",
                KanaRomaji.toRomaji("ぐれんのらいをめざめさせよ"));
        assertEquals("n", KanaRomaji.toRomaji("ん"));       // word-initial: own token
    }

    // ------------------------------------------------------------ modern kana + foreign morae

    @Test
    void modernKanaUsage() {
        assertEquals("wo", KanaRomaji.toRomaji("を"));
        assertEquals("wo", KanaRomaji.toRomaji("ヲ"));
        assertEquals("ji", KanaRomaji.toRomaji("ぢ"));
        assertEquals("ji", KanaRomaji.toRomaji("ヂ"));
        assertEquals("zu", KanaRomaji.toRomaji("づ"));
        assertEquals("zu", KanaRomaji.toRomaji("ヅ"));
        assertEquals("vu", KanaRomaji.toRomaji("ヴ"));
        assertEquals("wi", KanaRomaji.toRomaji("ゐ"));
        assertEquals("we", KanaRomaji.toRomaji("ゑ"));
    }

    @Test
    void foreignMorae() {
        assertEquals("ti", KanaRomaji.toRomaji("ティ"));
        assertEquals("di", KanaRomaji.toRomaji("ディ"));
        assertEquals("tu", KanaRomaji.toRomaji("トゥ"));
        assertEquals("du", KanaRomaji.toRomaji("ドゥ"));
        assertEquals("fa", KanaRomaji.toRomaji("ファ"));
        assertEquals("fi", KanaRomaji.toRomaji("フィ"));
        assertEquals("fe", KanaRomaji.toRomaji("フェ"));
        assertEquals("fo", KanaRomaji.toRomaji("フォ"));
        assertEquals("wi", KanaRomaji.toRomaji("ウィ"));
        assertEquals("we", KanaRomaji.toRomaji("ウェ"));
        assertEquals("wo", KanaRomaji.toRomaji("ウォ"));
        assertEquals("kwa", KanaRomaji.toRomaji("クァ"));
        assertEquals("gwa", KanaRomaji.toRomaji("グァ"));
        assertEquals("tsa", KanaRomaji.toRomaji("ツァ"));
        assertEquals("tse", KanaRomaji.toRomaji("ツェ"));
        assertEquals("she", KanaRomaji.toRomaji("シェ"));
        assertEquals("che", KanaRomaji.toRomaji("チェ"));
        assertEquals("je", KanaRomaji.toRomaji("ジェ"));
        assertEquals("va ru", KanaRomaji.toRomaji("ヴァル"));
    }

    @Test
    void wordInitialSmallKana() {
        // a small kana at word start stands alone (BASE fallback, KanaIpa)
        assertEquals("a", KanaRomaji.toRomaji("ぁ"));
        assertEquals("ya", KanaRomaji.toRomaji("ゃ"));
    }

    // ------------------------------------------------------------ strict fail-closed

    @Test
    void strictFailures() {
        assertEquals("", KanaRomaji.toRomaji("漢字"));            // kanji: KanjiIpa's job
        assertEquals("", KanaRomaji.toRomaji("アネモスよ、疾風を聴け")); // punct + kanji
        assertEquals("", KanaRomaji.toRomaji("ービール"));        // chōonpu at word start
        assertEquals("", KanaRomaji.toRomaji("ンー"));            // ー after non-vowel
        assertEquals("", KanaRomaji.toRomaji("ヷ"));              // above shift window
        assertEquals("", KanaRomaji.toRomaji("ｱ"));               // half-width katakana
        assertEquals("", KanaRomaji.toRomaji("a"));               // latin
        assertEquals("", KanaRomaji.toRomaji(""));               // blank
        assertEquals("", KanaRomaji.toRomaji(null));
    }
}
