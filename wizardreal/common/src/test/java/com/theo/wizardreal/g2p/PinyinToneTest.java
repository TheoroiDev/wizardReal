package com.theo.wizardreal.g2p;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tone mark composer acceptance (wo_a §1.2 — the work order's case list plus
 * placement-rule coverage). Input = TONE3 forms as the data table carries
 * them (v = ü; neutral = 5).
 */
class PinyinToneTest {

    @Test
    void workOrderCases() {
        assertEquals("zhēn", PinyinTone.marked("zhen1"));
        assertEquals("kōng", PinyinTone.marked("kong1"));
        assertEquals("rèn", PinyinTone.marked("ren4"));
        assertEquals("guì", PinyinTone.marked("gui4"));   // 标后: ui marks i
        assertEquals("liǔ", PinyinTone.marked("liu3"));   // 标后: iu marks u
        assertEquals("lǜ", PinyinTone.marked("lv4"));     // v -> ü with caron
        assertEquals("qīng", PinyinTone.marked("qing1"));
    }

    @Test
    void placementRules() {
        // rule 1: a wins (uai/iao/uai/an/uang...)
        assertEquals("hǎo", PinyinTone.marked("hao3"));
        assertEquals("xiǎo", PinyinTone.marked("xiao3"));
        assertEquals("guài", PinyinTone.marked("guai4"));
        assertEquals("wàn", PinyinTone.marked("wan4"));
        // rule 2: o (ou/ong/iong)
        assertEquals("zhōng", PinyinTone.marked("zhong1"));
        assertEquals("yōng", PinyinTone.marked("yong1"));
        assertEquals("ǒu", PinyinTone.marked("ou3"));
        // rule 3: e (ei/ie/ue/uan-less e forms)
        assertEquals("huì", PinyinTone.marked("hui4"));   // 标后: ui -> i
        assertEquals("yuè", PinyinTone.marked("yue4"));
        assertEquals("wén", PinyinTone.marked("wen2"));
        // rule 4/5: i/u singles and ü
        assertEquals("nǐ", PinyinTone.marked("ni3"));
        assertEquals("shuō", PinyinTone.marked("shuo1"));
        assertEquals("yīng", PinyinTone.marked("ying1"));
        assertEquals("lù", PinyinTone.marked("lu4"));
        assertEquals("jǔ", PinyinTone.marked("ju3"));     // j+u orthography, mark u
        assertEquals("èr", PinyinTone.marked("er4"));
    }

    @Test
    void neutralAndBare() {
        assertEquals("ba", PinyinTone.marked("ba5"));     // neutral: no mark
        assertEquals("ma", PinyinTone.marked("ma"));
        assertEquals("de", PinyinTone.marked("de5"));
    }

    @Test
    void numericStyle() {
        assertEquals("zhen1", PinyinTone.numeric("zhen1"));
        assertEquals("lü4", PinyinTone.numeric("lv4"));   // v -> ü, digit kept
        assertEquals("zhen", PinyinTone.numeric("zhen5")); // neutral: bare
        assertEquals("kong3", PinyinTone.numeric("kong3"));
    }

    @Test
    void syllabicNasalCombining() {
        assertEquals("n\u0301", PinyinTone.marked("n2")); // 嗯: decomposed n + combining acute
        assertEquals("m\u0301", PinyinTone.marked("m2")); // 呣
        assertEquals("n", PinyinTone.marked("n"));        // bare: no mark
    }
}
