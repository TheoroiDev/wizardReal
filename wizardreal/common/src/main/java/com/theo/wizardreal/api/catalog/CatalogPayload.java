package com.theo.wizardreal.api.catalog;

import java.util.List;
import java.util.Map;

/**
 * Server-built spell catalog snapshot (keys or literal text — the client
 * resolves legacy lang keys in the active language when exporting). This is
 * the payload of the S2C {@code wizardreal:spell_catalog} channel and the
 * input for the client-side {@code spell_catalog.json} export and the
 * zero-dependency wizardpedia push.
 *
 * <p>Catalog v2 (voice overhaul SS8): trigger aliases and chant lines carry a
 * <b>language annotation</b> — the map key is a two-letter language code
 * ({@code en}/{@code zh}/{@code ja}/{@code ko}); the {@code ""} key is the
 * language-neutral bucket (legacy flat data, shown on every language page).
 */
public record CatalogPayload(List<CatalogOrigin> origins) {

    /** One origin ("wizardreal:wizardry") with its spells, insertion-ordered. */
    public record CatalogOrigin(String id, String nameKey, List<CatalogSpell> spells) {
    }

    /**
     * One spell entry. {@code learned} is derived server-side from
     * {@code learning} against the configured known threshold (D-D2);
     * {@code learning} is the raw mastery percentage (0..100).
     * {@code triggerAliases} maps language code → trigger words ({@code ""} =
     * neutral/legacy bucket); {@code chantVariants} maps language code →
     * chant variants → ordered line texts, where legacy lines are lang keys
     * (resolved client-side) and language-keyed lines are literal text.
     */
    public record CatalogSpell(String id, String nameKey, boolean learned, boolean requiresLearning,
                               boolean ritual, List<String> schools, int manaCost, float cooldownSeconds,
                               float difficulty, float learning, boolean skipAllowed,
                               List<String> ipa,
                               Map<String, List<String>> triggerAliases,
                               Map<String, List<List<String>>> chantVariants) {
    }
}
