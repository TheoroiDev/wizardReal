package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Pronunciation;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catalog v4 structure tests (wo_a §1.5): the builder's chant-variant
 * derivation turns {@link ChantLine}s into structured
 * {@link CatalogPayload.CatalogLine}s with build-time readings — zh gets
 * toned pinyin + (hand-curated or derived) IPA, ja gets romaji + IPA, en and
 * legacy lang-key lines stay empty. Language-bucket grouping is unchanged
 * from v3 (first line's pronunciation bucket).
 */
class SpellCatalogBuilderTest {

    @AfterEach
    void clearRegistry() {
        com.theo.wizardreal.api.SpellRegistry.clear();
        com.theo.wizardreal.g2p.G2p.invalidate();
    }

    private static ChantLine line(String text, String lang, String... ipa) {
        return new ChantLine(null, new Pronunciation(text, List.of(ipa),
                List.of(text), lang == null ? Map.of() : Map.of(lang, List.of(text))));
    }

    private static ChantLine legacy(String displayKey) {
        return new ChantLine(displayKey, new Pronunciation(displayKey, List.of(),
                List.of(displayKey)));
    }

    @Test
    void zhLineGetsPinyinAndCuratedIpa() {
        Map<String, List<List<CatalogPayload.CatalogLine>>> variants =
                SpellCatalogBuilder.chantVariants(List.of(new Chant(List.of(
                        line("真空刃", "zh", "ʈʂən kʰʊŋ ʐən")))));
        List<CatalogPayload.CatalogLine> lines = variants.get("zh").get(0);
        assertEquals("真空刃", lines.get(0).text());
        assertEquals("zhēn kōng rèn", lines.get(0).readings().get(CatalogPayload.READING_PINYIN));
        assertEquals("ʈʂən kʰʊŋ ʐən", lines.get(0).readings().get(CatalogPayload.READING_IPA));
    }

    @Test
    void zhLineWithoutCuratedIpaGetsG2pDraft() {
        Map<String, List<List<CatalogPayload.CatalogLine>>> variants =
                SpellCatalogBuilder.chantVariants(List.of(new Chant(List.of(
                        line("火", "zh")))));
        CatalogPayload.CatalogLine l = variants.get("zh").get(0).get(0);
        assertEquals("huǒ", l.readings().get(CatalogPayload.READING_PINYIN));
        assertEquals("xwɔ", l.readings().get(CatalogPayload.READING_IPA));
    }

    @Test
    void jaLineGetsRomajiAndCuratedIpa() {
        Map<String, List<List<CatalogPayload.CatalogLine>>> variants =
                SpellCatalogBuilder.chantVariants(List.of(new Chant(List.of(
                        line("アネモス", "ja", "a nɛ mo sɯ")))));
        CatalogPayload.CatalogLine l = variants.get("ja").get(0).get(0);
        assertEquals("a ne mo su", l.readings().get(CatalogPayload.READING_ROMAJI));
        assertEquals("a nɛ mo sɯ", l.readings().get(CatalogPayload.READING_IPA));
    }

    @Test
    void enAndLegacyLinesStayEmpty() {
        Map<String, List<List<CatalogPayload.CatalogLine>>> variants =
                SpellCatalogBuilder.chantVariants(List.of(
                        new Chant(List.of(line("o flame", "en"))),
                        new Chant(List.of(legacy("wizardreal.chant.l1")))));
        assertTrue(variants.get("en").get(0).get(0).readings().isEmpty());
        assertTrue(variants.get("").get(0).get(0).readings().isEmpty());
    }

    @Test
    void punctuationLineGetsPinyinAndCuratedIpa() {
        // punctuation is a separator (hand IPA carries no pause tokens): the
        // full line derives pinyin while the curated ipa rides unchanged
        Map<String, List<List<CatalogPayload.CatalogLine>>> variants =
                SpellCatalogBuilder.chantVariants(List.of(new Chant(List.of(
                        line("来兮，无羁之罡风", "zh", "laɪ ɕi u tɕi ʈʂɨ kɑŋ fəŋ")))));
        CatalogPayload.CatalogLine l = variants.get("zh").get(0).get(0);
        assertEquals("来兮，无羁之罡风", l.text());
        assertEquals("lái xī wú jī zhī gāng fēng", l.readings().get(CatalogPayload.READING_PINYIN));
        assertEquals("laɪ ɕi u tɕi ʈʂɨ kɑŋ fəŋ", l.readings().get(CatalogPayload.READING_IPA));
    }

    @Test
    void variantGroupingUnchanged() {
        // two variants under the same bucket keep their order and grouping
        Map<String, List<List<CatalogPayload.CatalogLine>>> variants =
                SpellCatalogBuilder.chantVariants(List.of(
                        new Chant(List.of(line("一", "zh"))),
                        new Chant(List.of(line("二", "zh")))));
        assertEquals(2, variants.get("zh").size());
        assertEquals("一", variants.get("zh").get(0).get(0).text());
        assertEquals("二", variants.get("zh").get(1).get(0).text());
    }
}
