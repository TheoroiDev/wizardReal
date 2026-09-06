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
 * <p>Catalog v3 (voice overhaul SS8 + chant-stage ladder, magic_eco 03):
 * trigger aliases and chant lines carry a <b>language annotation</b> (map key
 * = two-letter language code, {@code ""} = language-neutral bucket); effect
 * descriptions ride as lang keys in the neutral bucket; the chant-stage
 * ladder is pushed per spell so compendiums can render tier cycles.
 */
public record CatalogPayload(List<CatalogOrigin> origins) {

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
                               Map<String, List<List<String>>> chantVariants,
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
