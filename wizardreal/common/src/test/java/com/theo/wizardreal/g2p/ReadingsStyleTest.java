package com.theo.wizardreal.g2p;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * pinyinStyle dual output (wo_b §1.4, D8): the SAME hanzi line derives the
 * tone-marked form (default) or the digit form per style; romaji and IPA
 * are tone-free and never change.
 */
class ReadingsStyleTest {

    @Test
    void sameLineTwoStyles() {
        String marks = Readings.derive("真空刃", "zh", List.of(), Readings.PinyinStyle.MARKS)
                .get(Readings.PINYIN);
        String numbers = Readings.derive("真空刃", "zh", List.of(), Readings.PinyinStyle.NUMBERS)
                .get(Readings.PINYIN);
        assertEquals("zhēn kōng rèn", marks);
        assertEquals("zhen1 kong1 ren4", numbers);
    }

    @Test
    void umlautSyllableBothStyles() {
        // lv4 -> lǜ (marked) / lü4 (numeric, v->ü still applied)
        assertEquals("lǜ", Readings.derive("绿", "zh", List.of(), Readings.PinyinStyle.MARKS)
                .get(Readings.PINYIN));
        assertEquals("lü4", Readings.derive("绿", "zh", List.of(), Readings.PinyinStyle.NUMBERS)
                .get(Readings.PINYIN));
    }

    @Test
    void threeArgOverloadDefaultsToMarks() {
        assertEquals(
                Readings.derive("真空刃", "zh", List.of(), Readings.PinyinStyle.MARKS),
                Readings.derive("真空刃", "zh", List.of()));
    }

    @Test
    void romajiAndIpaUnaffectedByStyle() {
        List<String> noHand = List.of();
        Map<String, String> marks = Readings.derive("アネモス", "ja", noHand, Readings.PinyinStyle.MARKS);
        Map<String, String> numbers = Readings.derive("アネモス", "ja", noHand, Readings.PinyinStyle.NUMBERS);
        assertEquals(marks, numbers);
        assertEquals("a ne mo su", marks.get(Readings.ROMAJI));
    }

    @Test
    void handIpaSurvivesBothStyles() {
        List<String> hand = List.of("ʈʂən kʰʊŋ ʐən");
        assertEquals("ʈʂən kʰʊŋ ʐən",
                Readings.derive("真空刃", "zh", hand, Readings.PinyinStyle.NUMBERS).get(Readings.IPA));
        assertEquals("zhen1 kong1 ren4",
                Readings.derive("真空刃", "zh", hand, Readings.PinyinStyle.NUMBERS).get(Readings.PINYIN));
    }
}
