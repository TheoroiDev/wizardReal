package com.theo.wizardreal.api.catalog;

import java.util.List;

/**
 * Server-built spell catalog snapshot (keys only — the client resolves
 * display text in the active language when exporting). This is the payload
 * of the S2C {@code wizardreal:spell_catalog} channel and the input for the
 * client-side {@code spell_catalog.json} export.
 */
public record CatalogPayload(List<CatalogOrigin> origins) {

    /** One origin ("wizardreal:wizardry") with its spells, insertion-ordered. */
    public record CatalogOrigin(String id, String nameKey, List<CatalogSpell> spells) {
    }

    /**
     * One spell entry. {@code learned} reflects the requesting player's
     * knowledge at build time; {@code chantDisplayKeys} are per-variant
     * ordered display keys (ritual chant lines).
     */
    public record CatalogSpell(String id, String nameKey, boolean learned, boolean requiresLearning,
                               boolean ritual, List<String> schools, int manaCost, float cooldownSeconds,
                               List<String> aliases, List<String> ipa, List<List<String>> chantDisplayKeys) {
    }
}
