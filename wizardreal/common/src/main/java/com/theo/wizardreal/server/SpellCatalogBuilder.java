package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Builds the {@link CatalogPayload} for one player: every registered spell
 * grouped by origin (insertion order kept), with the player's learned flags
 * from {@link PlayerMagicState}.
 *
 * <p>Catalog v2 (voice overhaul SS8): trigger aliases are split by language
 * bucket straight from the spell's {@code Pronunciation}; chant variants are
 * grouped under the language of their lines (language-keyed lines carry a
 * single-language bucket, legacy lines the neutral {@code ""} bucket).
 */
public final class SpellCatalogBuilder {

    /** Neutral language-bucket key for legacy (language-less) data. */
    public static final String LANG_NEUTRAL = "";

    private SpellCatalogBuilder() {}

    public static CatalogPayload build(MinecraftServer server, UUID player) {
        PlayerMagicState state = PlayerMagicState.get(server);
        float knownThreshold = com.theo.wizardreal.config.WizardRealConfig
                .loadCached(server.getServerDirectory().toPath()).learning().knownThreshold();
        Map<String, List<CatalogPayload.CatalogSpell>> byOrigin = new LinkedHashMap<>();
        for (Spell spell : SpellRegistry.all()) {
            float t = state.learningPercent(player, spell.id(), spell.difficulty());
            // D-D2: known is derived from learning points (wizardpedia could
            // not reconcile the old knownSpells/forgottenSpells sets).
            boolean learned = LearningCurve.known(t, knownThreshold);
            List<String> schools = spell.schools().stream()
                    .map(school -> school.name().toLowerCase(Locale.ROOT)).toList();
            var pronunciation = spell.pronunciation();
            var entry = new CatalogPayload.CatalogSpell(
                    spell.id(), spell.nameKey(), learned, spell.requiresLearning(),
                    !spell.chants().isEmpty(),
                    schools, spell.manaCost(), spell.cooldownTicks() / 20f,
                    spell.difficulty(), t, spell.chantPolicy().skipAllowed(),
                    pronunciation == null ? List.of() : pronunciation.ipa(),
                    triggerAliases(pronunciation),
                    chantVariants(spell.chants()));
            byOrigin.computeIfAbsent(spell.origin(), k -> new ArrayList<>()).add(entry);
        }
        List<CatalogPayload.CatalogOrigin> origins = new ArrayList<>();
        for (Map.Entry<String, List<CatalogPayload.CatalogSpell>> e : byOrigin.entrySet()) {
            // Lang-key convention: origin ids use ':' but display keys use '.'
            // ("wizardreal:wizardry" -> "origin.wizardreal.wizardry").
            String nameKey = "origin." + e.getKey().replace(':', '.');
            origins.add(new CatalogPayload.CatalogOrigin(e.getKey(), nameKey, e.getValue()));
        }
        return new CatalogPayload(List.copyOf(origins));
    }

    /** Trigger words per language bucket; unclaimed flat aliases stay neutral. */
    private static Map<String, List<String>> triggerAliases(com.theo.voicecast.api.Pronunciation pronunciation) {
        if (pronunciation == null) return Map.of();
        Map<String, List<String>> out = new LinkedHashMap<>(pronunciation.languages());
        if (out.isEmpty()) {
            return pronunciation.aliases().isEmpty() ? Map.of() : Map.of(LANG_NEUTRAL, pronunciation.aliases());
        }
        LinkedHashSet<String> claimed = new LinkedHashSet<>();
        for (List<String> bucket : pronunciation.languages().values()) claimed.addAll(bucket);
        List<String> neutral = new ArrayList<>();
        for (String alias : pronunciation.aliases()) {
            if (!claimed.contains(alias)) neutral.add(alias);
        }
        if (!neutral.isEmpty()) out.put(LANG_NEUTRAL, neutral);
        return Collections.unmodifiableMap(out);
    }

    /** Chant variants grouped by their lines' language ({@code ""} = legacy). */
    private static Map<String, List<List<String>>> chantVariants(List<Chant> chants) {
        Map<String, List<List<String>>> out = new LinkedHashMap<>();
        for (Chant chant : chants) {
            String lang = LANG_NEUTRAL;
            List<ChantLine> lines = chant.lines();
            if (!lines.isEmpty() && lines.get(0).pronunciation() != null) {
                var buckets = lines.get(0).pronunciation().languages();
                if (!buckets.isEmpty()) lang = buckets.keySet().iterator().next();
            }
            List<String> texts = lines.stream().map(ChantLine::displayText).toList();
            out.computeIfAbsent(lang, k -> new ArrayList<>()).add(texts);
        }
        Map<String, List<List<String>>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<String>>> e : out.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
