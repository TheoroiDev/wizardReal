package com.theo.wizardreal.api.catalog;

import java.util.List;
import java.util.Map;

/**
 * Server-built spell catalog snapshot (keys or literal text — the client
 * resolves lang keys in the active language when exporting). This is the
 * payload of the S2C {@code wizardreal:spell_catalog} channel and the input
 * for the client-side {@code spell_catalog.json} export and the
 * zero-dependency wizardpedia push.
 *
 * <p>Catalog v4 (chant annotation layer, plan
 * docs/plans/chant_reading_annotation.md): chant lines are STRUCTURED —
 * {@link CatalogLine} carries the display text plus its derived
 * {@code readings} map (fixed keys {@code pinyin/romaji/ipa}, absent keys =
 * not derivable, fail-closed). Trigger aliases keep their language
 * annotation; the chant-stage ladder is pushed per spell.
 */
public record CatalogPayload(List<CatalogOrigin> origins) {

    /** Fixed readings key set (annotation layer). */
    public static final String READING_PINYIN = "pinyin";
    /** Fixed readings key set (annotation layer). */
    public static final String READING_ROMAJI = "romaji";
    /** Fixed readings key set (annotation layer). */
    public static final String READING_IPA = "ipa";

    /**
     * One chant line: the display text ({@code ChantLine#displayText} — the
     * legacy lang key or the line's first alias, the same value the
     * recognizer scores against) plus its derived readings. {@code readings}
     * is an insertion-ordered map over the fixed key set, possibly empty
     * (en lines, unconvertible text — fail-closed); values are display
     * strings derived server-side at catalog build time.
     */
    public record CatalogLine(String text, Map<String, String> readings) {

        public CatalogLine {
            readings = readings == null || readings.isEmpty() ? Map.of() : Map.copyOf(readings);
        }

        /** Line without readings (unannotated bucket / plain text). */
        public static CatalogLine plain(String text) {
            return new CatalogLine(text, Map.of());
        }
    }

    /** One origin ("wizardreal:wizardry") with its spells, insertion-ordered. */
    public record CatalogOrigin(String id, String nameKey, List<CatalogSpell> spells) {
    }

    /**
     * One spell entry. {@code learned} is derived server-side from
     * {@code learning} against the configured known threshold (D-D2);
     * {@code learning} is the raw mastery percentage (0..100).
     * {@code descKeys} are effect-summary lang keys for the base spell
     * (stage 0); {@code stages} is the ascending chant-stage ladder.
     */
    public record CatalogSpell(String id, String nameKey, boolean learned, boolean requiresLearning,
                               boolean ritual, List<String> schools, int manaCost, float cooldownSeconds,
                               float difficulty, float learning, boolean skipAllowed,
                               List<String> ipa,
                               Map<String, List<String>> triggerAliases,
                               Map<String, List<List<CatalogLine>>> chantVariants,
                               List<String> descKeys,
                               List<CatalogStage> stages) {
    }

    /**
     * One chant-stage ladder tier ({@code chant_stages}, magic_eco 03).
     * {@code manaCost}/{@code cooldownSeconds} of {@code -1} mean "inherit
     * the spell's base value". {@code descKeys} are this tier's
     * effect-summary lang keys (replacing the base ones when resolved here).
     */
    public record CatalogStage(int afterLines, float mastery, int manaCost, float cooldownSeconds,
                               List<String> descKeys) {
    }
}
