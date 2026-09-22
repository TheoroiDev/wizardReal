package com.theo.wizardreal.client;

import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.config.WizardRealConfig.ReadingsSettings;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Client-side mapping from a HUD chant line to its payload readings and the
 * D2 language-policy selection (annotation layer R-B, plan
 * docs/plans/chant_reading_annotation.md §4.4). Pure JVM — unit-testable.
 *
 * <p><b>Line → readings mapping (wo_b §1.3)</b>: the HUD's line texts ARE the
 * server's {@code ChantLine#displayText} values (the same strings the catalog
 * carries), so alignment is text-anchored:
 * <ol>
 *   <li><b>Variant/bucket alignment (preferred)</b> — a payload chant variant
 *       (bucket key + index) whose whole line-text sequence equals the HUD
 *       variant's; its {@code lineIndex} entry is taken with the bucket it
 *       was found under.</li>
 *   <li><b>Per-line text fallback</b> — when no variant matches wholesale
 *       (e.g. wire-truncated texts, degenerate data): the first payload line
 *       in the spell whose text equals the HUD line (ambiguous duplicates
 *       take the first hit).</li>
 * </ol>
 *
 * <p><b>Selection (D2 ∩ available keys)</b> — same semantics as the
 * wizardpedia book page, independently implemented here (zero cross-repo
 * dependency): {@code auto} = annotate only when the line's language bucket
 * prefix differs from the MC display language prefix (zh vs zh_cn → no,
 * ja vs zh_cn → yes); {@code off}; {@code selected} = the configured bucket
 * set; {@code all} = anything with a reading. Among the present reading keys
 * the fixed priority is pinyin &gt; romaji &gt; ipa (the wizardreal HUD config
 * carries no method toggles — D6's注音法 switches are a book-page feature).
 */
public final class ChantReadings {
    private ChantReadings() {}

    /** Fixed key priority for the HUD annotation (first present wins). */
    private static final String[] KEY_PRIORITY = {
            CatalogPayload.READING_PINYIN, CatalogPayload.READING_ROMAJI, CatalogPayload.READING_IPA};

    /** One resolved HUD line: its language bucket (may be {@code null} when
     *  unmapped) + the payload readings map. */
    public record Resolved(String bucket, Map<String, String> readings) {
        public static final Resolved NONE = new Resolved(null, Map.of());
    }

    /** Find the catalog spell behind a HUD chant ({@code null} when the
     *  payload has no such spell). */
    public static CatalogPayload.CatalogSpell findSpell(CatalogPayload payload, String spellId) {
        if (payload == null || spellId == null || spellId.isEmpty()) return null;
        for (CatalogPayload.CatalogOrigin origin : payload.origins()) {
            for (CatalogPayload.CatalogSpell spell : origin.spells()) {
                if (spellId.equals(spell.id())) return spell;
            }
        }
        return null;
    }

    /** Resolve the payload line behind the HUD's current chant line. */
    public static Resolved resolve(CatalogPayload.CatalogSpell spell, List<String> hudLines, int lineIndex) {
        if (spell == null || hudLines == null || hudLines.isEmpty()
                || lineIndex < 0 || lineIndex >= hudLines.size()) {
            return Resolved.NONE;
        }
        // 1) variant/bucket alignment: whole HUD variant == a payload variant
        for (Map.Entry<String, List<List<CatalogPayload.CatalogLine>>> e
                : spell.chantVariants().entrySet()) {
            for (List<CatalogPayload.CatalogLine> variant : e.getValue()) {
                if (!sameTexts(variant, hudLines)) continue;
                CatalogPayload.CatalogLine line = variant.get(lineIndex);
                if (hudLines.get(lineIndex).equals(line.text())) {
                    return new Resolved(e.getKey(), line.readings());
                }
            }
        }
        // 2) per-line text fallback: first hit across all buckets (ambiguous
        //    duplicates take the first hit, per wo_b §1.3)
        String want = hudLines.get(lineIndex);
        for (Map.Entry<String, List<List<CatalogPayload.CatalogLine>>> e
                : spell.chantVariants().entrySet()) {
            for (List<CatalogPayload.CatalogLine> variant : e.getValue()) {
                for (CatalogPayload.CatalogLine line : variant) {
                    if (want.equals(line.text())) return new Resolved(e.getKey(), line.readings());
                }
            }
        }
        return Resolved.NONE;
    }

    private static boolean sameTexts(List<CatalogPayload.CatalogLine> variant, List<String> hudLines) {
        if (variant.size() != hudLines.size()) return false;
        for (int i = 0; i < variant.size(); i++) {
            if (!hudLines.get(i).equals(variant.get(i).text())) return false;
        }
        return true;
    }

    /**
     * D2 policy ∩ present keys → one annotation string, or {@code null} to
     * show nothing.
     *
     * @param bucket   the line's language bucket (may be {@code null}/empty)
     * @param mcLang   the MC display language code (e.g. {@code "zh_cn"})
     * @param settings the {@code [chantReadings]} config
     */
    public static String select(String bucket, String mcLang, ReadingsSettings settings,
                                Map<String, String> readings) {
        if (readings == null || readings.isEmpty() || settings == null) return null;
        switch (settings.languagePolicy()) {
            case OFF -> {
                return null;
            }
            case AUTO -> {
                if (!bucketDiffersFromDisplay(bucket, mcLang)) return null;
            }
            case SELECTED -> {
                if (bucket == null || !settings.selectedLanguages()
                        .contains(bucket.trim().toLowerCase(Locale.ROOT))) {
                    return null;
                }
            }
            case ALL -> { /* any present reading qualifies */ }
        }
        for (String key : KEY_PRIORITY) {
            String value = readings.get(key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    /** {@code auto}: two-letter bucket prefix vs the display-language prefix
     *  (zh vs zh_cn → same → false; ja vs zh_cn → true). Neutral/unknown
     *  buckets never qualify (nothing to compare against). */
    private static boolean bucketDiffersFromDisplay(String bucket, String mcLang) {
        String b = prefix(bucket);
        String m = prefix(mcLang);
        return !b.isEmpty() && !m.isEmpty() && !b.equalsIgnoreCase(m);
    }

    /** Language-code prefix: everything before {@code _} (or the whole code). */
    private static String prefix(String code) {
        if (code == null) return "";
        String s = code.trim();
        int at = s.indexOf('_');
        return (at >= 0 ? s.substring(0, at) : s).toLowerCase(Locale.ROOT);
    }
}
