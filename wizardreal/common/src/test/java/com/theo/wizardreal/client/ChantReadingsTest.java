package com.theo.wizardreal.client;

import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.config.WizardRealConfig.ReadingsSettings;
import com.theo.wizardreal.config.WizardRealConfig.ReadingsSettings.LanguagePolicy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * HUD annotation plumbing (wo_b §1.3): payload spell lookup, variant/bucket
 * alignment with per-line text fallback, the D2 selection matrix, and the
 * zero-regression line layout (no reading row -> pre-annotation offsets).
 */
class ChantReadingsTest {

    // ---- fixtures ---------------------------------------------------------

    private static CatalogPayload.CatalogLine line(String text, String... kv) {
        Map<String, String> readings = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) readings.put(kv[i], kv[i + 1]);
        return new CatalogPayload.CatalogLine(text, readings);
    }

    private static CatalogPayload.CatalogSpell spell(Map<String, List<List<CatalogPayload.CatalogLine>>> variants) {
        return new CatalogPayload.CatalogSpell("wizardreal:anemos", "spell.wizardreal.anemos.name",
                true, false, true, List.of("air"), 5, 2f, 1f, 100f, true,
                List.of(), Map.of(), variants, List.of(), List.of());
    }

    private static ReadingsSettings settings(LanguagePolicy policy, String... langs) {
        return new ReadingsSettings(true, policy, List.of(langs),
                com.theo.wizardreal.g2p.Readings.PinyinStyle.MARKS);
    }

    // ---- spell lookup -----------------------------------------------------

    @Test
    void findSpellToleratesNullPayloadAndMissingId() {
        assertNull(ChantReadings.findSpell(null, "wizardreal:anemos"));
        CatalogPayload payload = new CatalogPayload(List.of(
                new CatalogPayload.CatalogOrigin("wizardreal:wizardry", "origin.wizardreal.wizardry",
                        List.of(spell(Map.of())))));
        assertNull(ChantReadings.findSpell(payload, "wizardreal:other"));
        assertEquals("wizardreal:anemos",
                ChantReadings.findSpell(payload, "wizardreal:anemos").id());
    }

    // ---- line -> readings mapping ------------------------------------------

    @Test
    void variantAlignmentResolvesByWholeVariant() {
        CatalogPayload.CatalogSpell spell = spell(Map.of(
                "zh", List.of(List.of(
                        line("真空刃", "pinyin", "zhēn kōng rèn"),
                        line("以漆黑遮蔽苍穹", "pinyin", "yǐ qī hēi zhē bì cāng qióng")))));
        ChantReadings.Resolved r = ChantReadings.resolve(spell,
                List.of("真空刃", "以漆黑遮蔽苍穹"), 1);
        assertEquals("zh", r.bucket());
        assertEquals("yǐ qī hēi zhē bì cāng qióng", r.readings().get("pinyin"));
    }

    @Test
    void textFallbackHitsFirstPayloadLineWhenNoVariantMatches() {
        CatalogPayload.CatalogSpell spell = spell(Map.of(
                "ja", List.of(List.of(line("アネモス", "romaji", "a ne mo su")))));
        // HUD variant does not match any payload variant wholesale; the single
        // HUD line's TEXT exists in the payload -> per-line fallback
        ChantReadings.Resolved r = ChantReadings.resolve(spell, List.of("アネモス"), 0);
        assertEquals("ja", r.bucket());
        assertEquals("a ne mo su", r.readings().get("romaji"));
    }

    @Test
    void ambiguousDuplicateTakesFirstHit() {
        CatalogPayload.CatalogSpell spell = spell(Map.of(
                "zh", List.of(
                        List.of(line("风", "pinyin", "fēng")),
                        List.of(line("风", "pinyin", "SECOND")))));
        ChantReadings.Resolved r = ChantReadings.resolve(spell, List.of("风"), 0);
        assertEquals("fēng", r.readings().get("pinyin")); // first hit wins
    }

    @Test
    void unmappedLineYieldsNone() {
        CatalogPayload.CatalogSpell spell = spell(Map.of(
                "zh", List.of(List.of(line("真空刃", "pinyin", "zhēn kōng rèn")))));
        assertSame(ChantReadings.Resolved.NONE, ChantReadings.resolve(spell, List.of("别的字"), 0));
        assertSame(ChantReadings.Resolved.NONE, ChantReadings.resolve(spell, List.of("真空刃"), -1));
        assertSame(ChantReadings.Resolved.NONE, ChantReadings.resolve(spell, List.of("真空刃"), 5));
        assertSame(ChantReadings.Resolved.NONE, ChantReadings.resolve(null, List.of("真空刃"), 0));
        assertSame(ChantReadings.Resolved.NONE, ChantReadings.resolve(spell, List.of(), 0));
    }

    // ---- D2 selection matrix ------------------------------------------------

    private static final Map<String, String> ZH_READINGS = Map.of("pinyin", "zhēn", "ipa", "ʈʂən");
    private static final Map<String, String> JA_READINGS = Map.of("romaji", "a ne mo su", "ipa", "a nɛ mo sɯ");

    @Test
    void policyOffAnnotatesNothing() {
        assertNull(ChantReadings.select("zh", "en_us", settings(LanguagePolicy.OFF), ZH_READINGS));
        assertNull(ChantReadings.select("ja", "en_us", settings(LanguagePolicy.OFF), JA_READINGS));
    }

    @Test
    void policyAutoAnnotatesOnlyNonDisplayLanguages() {
        // zh lines while displaying zh_cn: no annotation
        assertNull(ChantReadings.select("zh", "zh_cn", settings(LanguagePolicy.AUTO), ZH_READINGS));
        // same line while displaying en_us: annotated
        assertEquals("zhēn", ChantReadings.select("zh", "en_us", settings(LanguagePolicy.AUTO), ZH_READINGS));
        // ja line while displaying zh_cn: annotated (ja != zh)
        assertEquals("a ne mo su", ChantReadings.select("ja", "zh_cn", settings(LanguagePolicy.AUTO), JA_READINGS));
        // neutral/unknown bucket: never qualifies
        assertNull(ChantReadings.select("", "en_us", settings(LanguagePolicy.AUTO), ZH_READINGS));
        assertNull(ChantReadings.select(null, "en_us", settings(LanguagePolicy.AUTO), ZH_READINGS));
        // display language unknown: nothing to compare against
        assertNull(ChantReadings.select("zh", null, settings(LanguagePolicy.AUTO), ZH_READINGS));
    }

    @Test
    void policySelectedUsesConfiguredBucketSet() {
        ReadingsSettings s = settings(LanguagePolicy.SELECTED, "ja");
        assertNull(ChantReadings.select("zh", "en_us", s, ZH_READINGS));
        assertEquals("a ne mo su", ChantReadings.select("ja", "en_us", s, JA_READINGS));
        assertNull(ChantReadings.select(null, "en_us", s, JA_READINGS));
        // empty selected set selects nothing
        assertNull(ChantReadings.select("ja", "en_us", settings(LanguagePolicy.SELECTED), JA_READINGS));
    }

    @Test
    void policyAllAnnotatesAnythingPresent() {
        assertEquals("zhēn", ChantReadings.select("zh", "zh_cn", settings(LanguagePolicy.ALL), ZH_READINGS));
        assertEquals("a ne mo su", ChantReadings.select("ja", "zh_cn", settings(LanguagePolicy.ALL), JA_READINGS));
    }

    @Test
    void keyPriorityPinyinThenRomajiThenIpa() {
        // zh line with pinyin+ipa -> pinyin
        assertEquals("zhēn", ChantReadings.select("zh", "en_us",
                settings(LanguagePolicy.ALL), Map.of("ipa", "ʈʂən", "pinyin", "zhēn")));
        // ja line with romaji+ipa -> romaji
        assertEquals("a ne mo su", ChantReadings.select("ja", "en_us",
                settings(LanguagePolicy.ALL), Map.of("ipa", "a nɛ mo sɯ", "romaji", "a ne mo su")));
        // only ipa -> ipa
        assertEquals("ʈʂən", ChantReadings.select("zh", "en_us",
                settings(LanguagePolicy.ALL), Map.of("ipa", "ʈʂən")));
        // no readings at all -> null under every policy
        assertNull(ChantReadings.select("zh", "en_us", settings(LanguagePolicy.ALL), Map.of()));
        assertNull(ChantReadings.select("zh", "en_us", settings(LanguagePolicy.ALL), null));
    }

    // ---- HUD layout zero-regression -----------------------------------------

    @Test
    void lineLayoutWithoutReadingRowIsUnchanged() {
        // pre-annotation law: y = startY + i * LINE_H, no exceptions
        for (int i = 0; i < 6; i++) {
            assertEquals(100 + i * 11, ChantHud.lineY(100, i, 2, false));
        }
    }

    @Test
    void lineLayoutWithReadingRowPushesCurrentAndBelow() {
        // ruby-above (user ruling 2026-09-22): the current line shifts down
        // so the reading fits in the gap above it — everything above keeps
        // its offset
        assertEquals(100 + 0 * 11, ChantHud.lineY(100, 0, 1, true));
        assertEquals(100 + 1 * 11 + ChantHud.RUBY_SHIFT, ChantHud.lineY(100, 1, 1, true));
        // lines below the annotated current line shift by RUBY_SHIFT as well
        assertEquals(100 + 2 * 11 + ChantHud.RUBY_SHIFT, ChantHud.lineY(100, 2, 1, true));
        assertEquals(100 + 3 * 11 + ChantHud.RUBY_SHIFT, ChantHud.lineY(100, 3, 1, true));
    }
}
