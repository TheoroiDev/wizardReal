package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.g2p.Readings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * Catalog v4 (chant annotation layer): chant lines additionally carry
 * build-time derived readings (pinyin/romaji/ipa, {@link Readings}).
 */
public final class SpellCatalogBuilder {

    /** Neutral language-bucket key for legacy (language-less) data. */
    public static final String LANG_NEUTRAL = "";

    /** Pure feedback primitives — not shown in effect summaries. */
    private static final Set<String> COSMETIC_EFFECTS = Set.of(
            "wizardreal:sound", "wizardreal:particles", "wizardreal:visual");

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
                    chantVariants(spell.chants()),
                    descKeys(effectsOf(spell)),
                    stages(spell));
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

    private static List<com.theo.wizardreal.effect.SpellEffect> effectsOf(Spell spell) {
        return spell instanceof com.theo.wizardreal.spell.DataSpell data
                ? data.effects() : List.of();
    }

    /** Effect-summary lang keys ({@code wizardreal.effect.<type>}), cosmetic
     *  primitives (sound/particles/visual) skipped. */
    private static List<String> descKeys(List<com.theo.wizardreal.effect.SpellEffect> effects) {
        List<String> out = new ArrayList<>();
        for (var effect : effects) {
            if (effect == null) continue;
            String id = effect.effectId().toString();
            if (COSMETIC_EFFECTS.contains(id)) continue;
            out.add("wizardreal.effect." + effect.effectId().getPath());
        }
        return List.copyOf(out);
    }

    /** Chant-stage ladder mapped for the catalog (mana/cooldown -1 = inherit). */
    private static List<CatalogPayload.CatalogStage> stages(Spell spell) {
        List<CatalogPayload.CatalogStage> out = new ArrayList<>();
        for (var stage : spell.chantStages()) {
            out.add(new CatalogPayload.CatalogStage(
                    stage.afterLines(), stage.masteryThreshold(),
                    stage.manaCost() == null ? -1 : stage.manaCost(),
                    stage.cooldownTicks() == null ? -1f : stage.cooldownTicks() / 20f,
                    descKeys(stage.effects())));
        }
        return List.copyOf(out);
    }

    /** Trigger words per language bucket; unclaimed flat aliases stay neutral. */
    private static Map<String, List<String>> triggerAliases(com.theo.wizardreal.api.Pronunciation pronunciation) {
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

    /** Chant variants grouped by their lines' language ({@code ""} = legacy).
     *  Catalog v4: each line becomes a structured {@link CatalogPayload.CatalogLine}
     *  whose readings are derived HERE, once, at build time — anchored to the
     *  line's display text (= first alias, the value the recognizer scores)
     *  with the line's hand-curated IPA winning over G2P drafts
     *  ({@link Readings}, fail-closed). The chant's language bucket (first
     *  line's pronunciation) drives the annotation set for every line of the
     *  chant. Package-private for the v4 structure tests. */
    static Map<String, List<List<CatalogPayload.CatalogLine>>> chantVariants(List<Chant> chants) {
        Map<String, List<List<CatalogPayload.CatalogLine>>> out = new LinkedHashMap<>();
        for (Chant chant : chants) {
            String lang = LANG_NEUTRAL;
            List<ChantLine> lines = chant.lines();
            if (!lines.isEmpty() && lines.get(0).pronunciation() != null) {
                var buckets = lines.get(0).pronunciation().languages();
                if (!buckets.isEmpty()) lang = buckets.keySet().iterator().next();
            }
            List<CatalogPayload.CatalogLine> texts = new ArrayList<>(lines.size());
            for (ChantLine line : lines) {
                String text = line.displayText();
                List<String> handIpa = line.pronunciation() == null
                        ? List.of() : line.pronunciation().ipa();
                texts.add(new CatalogPayload.CatalogLine(text,
                        Readings.derive(text, lang, handIpa)));
            }
            out.computeIfAbsent(lang, k -> new ArrayList<>()).add(texts);
        }
        Map<String, List<List<CatalogPayload.CatalogLine>>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<CatalogPayload.CatalogLine>>> e : out.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
