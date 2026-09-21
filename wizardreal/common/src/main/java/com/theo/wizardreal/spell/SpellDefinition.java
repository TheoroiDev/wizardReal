package com.theo.wizardreal.spell;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.SpellStage;
import com.theo.wizardreal.effect.EffectRegistry;
import com.theo.wizardreal.effect.SpellEffect;
import com.theo.wizardreal.api.Pronunciation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Codec-parsed datapack spell definition
 * ({@code data/<ns>/voicecast/spells/<file>.json}). Converted to a live
 * {@link DataSpell} via {@link #toSpell()}.
 *
 * <pre>
 * {
 *   "id": "wizardreal:ignis",
 *   "schools": ["fire"],
 *   "mana_cost": 5, "cooldown_ticks": 40,
 *   "requires_learning": false,
 *   "origin": "wizardreal:wizardry",
 *   "threshold": 0.6,
 *   "trigger": { "languages": { "en": [...], "zh": [...] }, "ipa": [...] },
 *   "chants": { "languages": { "<lang>": { "trigger": {...}, "cast": {...}, "body": [[...], [...]] } } },
 *   "effects": [ { "type": "wizardreal:projectile", ... } ]
 * }
 * </pre>
 *
 * <p>Schema source of truth: this CODEC + {@code schema/spell.schema.json} (repo root);
 * {@code docs/spells/spell_json.md} (workspace-root docs/) is the human-readable rendering.
 *
 * <p>0.4.0 dual-read (voice overhaul D1/D-C6): {@code trigger.languages} replaces
 * the flat multilingual {@code trigger.aliases} (old key still parses and becomes
 * the legacy bucket routed to every engine), and {@code chants} accepts the new
 * language-keyed object alongside the legacy variant array. The legacy chant
 * format is read-only compat: it retires with the spell-matrix rewrite.
 */
public record SpellDefinition(
        ResourceLocation id,
        Set<School> schools,
        int manaCost,
        int cooldownTicks,
        boolean requiresLearning,
        String origin,
        float threshold,
        TriggerDef trigger,
        ChantsDef chants,
        ChantPolicyDef chantPolicy,
        float difficulty,
        List<SpellEffect> effects,
        List<StageDef> stages
) {
    public static final Codec<School> SCHOOL_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(School.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown school: " + name);
                }
            },
            school -> school.name().toLowerCase(Locale.ROOT));

    public static final Codec<Set<School>> SCHOOLS_CODEC = SCHOOL_CODEC.listOf().xmap(
            list -> {
                if (list.isEmpty()) return Collections.emptySet();
                return Collections.unmodifiableSet(EnumSet.copyOf(list));
            },
            List::copyOf);

    public static final Codec<SpellDefinition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(SpellDefinition::id),
                    SCHOOLS_CODEC.fieldOf("schools").forGetter(SpellDefinition::schools),
                    Codec.INT.optionalFieldOf("mana_cost", 10).forGetter(SpellDefinition::manaCost),
                    Codec.INT.optionalFieldOf("cooldown_ticks", 40).forGetter(SpellDefinition::cooldownTicks),
                    Codec.BOOL.optionalFieldOf("requires_learning", false).forGetter(SpellDefinition::requiresLearning),
                    Codec.STRING.optionalFieldOf("origin", "wizardreal:wizardry").forGetter(SpellDefinition::origin),
                    Codec.FLOAT.optionalFieldOf("threshold", -1.0f).forGetter(SpellDefinition::threshold),
                    TriggerDef.CODEC.fieldOf("trigger").forGetter(SpellDefinition::trigger),
                    ChantsDef.CODEC.optionalFieldOf("chants", ChantsDef.EMPTY).forGetter(SpellDefinition::chants),
                    ChantPolicyDef.CODEC.optionalFieldOf("chant_policy", ChantPolicyDef.EMPTY)
                            .forGetter(SpellDefinition::chantPolicy),
                    Codec.floatRange(0.5f, 3.0f).optionalFieldOf("difficulty", 1.0f)
                            .forGetter(SpellDefinition::difficulty),
                    EffectRegistry.codec().listOf().fieldOf("effects").forGetter(SpellDefinition::effects),
                    StageDef.CODEC.listOf().optionalFieldOf("chant_stages", List.of())
                            .forGetter(SpellDefinition::stages)
            ).apply(instance, SpellDefinition::new));

    /** Convert to the live {@link Spell} instance registered in {@code SpellRegistry}. */
    public DataSpell toSpell() {
        List<Chant> builtChants = new ArrayList<>();
        if (chants.keyed() != null) {
            expandLanguageKeyedChants(builtChants);
        } else {
            expandLegacyChants(builtChants);
        }
        Pronunciation pronunciation = trigger.pronunciation(id.toString());
        List<SpellStage> builtStages = new ArrayList<>();
        for (StageDef s : stages) {
            builtStages.add(new SpellStage(s.afterLines(), s.mastery(), s.effects(),
                    s.manaCost(), s.cooldownTicks()));
        }
        return new DataSpell(id, schools, manaCost, cooldownTicks, requiresLearning, origin,
                threshold, difficulty, pronunciation, builtChants, effects, chantPolicy.toPolicy(), builtStages);
    }

    /** 0.4.0 language-keyed expansion: for each language, every {@code body}
     * element becomes one variant chain (trigger + middle lines + cast). Line
     * pronunciations carry a single-language bucket so the voicecast session
     * router feeds them only to that language's engine; line ids embed the
     * language ({@code <spell>.chant.<lang>.<v>:<i>}) for the recognizer's
     * line-structure derivation (semantic contract v2, C1b). */
    private void expandLanguageKeyedChants(List<Chant> out) {
        for (Map.Entry<String, ChantLanguagesDef.LangChant> e : chants.keyed().languages().entrySet()) {
            String lang = e.getKey().trim().toLowerCase(Locale.ROOT);
            ChantLanguagesDef.LangChant lc = e.getValue();
            List<List<LineMeta>> variants =
                    lc.body().isEmpty() ? List.of(List.of()) : lc.body();
            if (variants.size() < 2) {
                WizardReal.LOGGER.warn("Spell '{}' chant language '{}' has {} variant(s); two per language are expected",
                        id, lang, variants.size());
            }
            for (int v = 0; v < variants.size(); v++) {
                List<ChantLine> lines = new ArrayList<>();
                int index = 0;
                lines.add(langLine(id + ".chant." + lang + "." + v + ":" + (index++), lang, lc.trigger()));
                for (LineMeta mid : variants.get(v)) {
                    lines.add(langLine(id + ".chant." + lang + "." + v + ":" + (index++), lang, mid));
                }
                lines.add(langLine(id + ".chant." + lang + "." + v + ":" + index, lang, lc.cast()));
                out.add(new Chant(lines));
            }
        }
    }

    private void expandLegacyChants(List<Chant> out) {
        for (int v = 0; v < chants.legacy().size(); v++) {
            ChantDef cd = chants.legacy().get(v);
            List<ChantLine> lines = new ArrayList<>();
            for (int i = 0; i < cd.lines().size(); i++) {
                ChantDef.LineDef ln = cd.lines().get(i);
                lines.add(new ChantLine(ln.displayKey(),
                        new Pronunciation(id + ".chant." + v + ":" + i, ln.ipa(), ln.aliases())));
            }
            out.add(new Chant(lines));
        }
    }

    private static ChantLine langLine(String pronunciationId, String lang, LineMeta meta) {
        Pronunciation p = new Pronunciation(pronunciationId, meta.ipa(), List.of(),
                Map.of(lang, meta.aliases()));
        return new ChantLine(null, p);
    }

    /** Trigger word / phrase metadata: the utterance that starts (or casts) the spell.
     * 0.4.0: {@code languages} buckets (two-letter codes) take precedence when
     * present; the flat {@code aliases} then act as legacy extras routed to
     * every engine. Without buckets the flat aliases are the legacy bucket. */
    public record TriggerDef(List<String> aliases, List<String> ipa, Map<String, List<String>> languages) {
        public static final Codec<TriggerDef> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.listOf().optionalFieldOf("aliases", List.of()).forGetter(TriggerDef::aliases),
                        Codec.STRING.listOf().optionalFieldOf("ipa", List.of()).forGetter(TriggerDef::ipa),
                        Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf())
                                .optionalFieldOf("languages", Map.of()).forGetter(TriggerDef::languages)
                ).apply(instance, TriggerDef::new));

        Pronunciation pronunciation(String spellId) {
            if (languages.isEmpty()) {
                return new Pronunciation(spellId, ipa, aliases);
            }
            Map<String, List<String>> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> e : languages.entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) continue;
                normalized.put(e.getKey().trim().toLowerCase(Locale.ROOT), List.copyOf(e.getValue()));
            }
            if (normalized.isEmpty()) {
                return new Pronunciation(spellId, ipa, aliases);
            }
            return new Pronunciation(spellId, ipa, aliases, Collections.unmodifiableMap(normalized));
        }
    }

    /** The {@code chants} field in either shape (dual-read): the 0.4.0
     * language-keyed object or the legacy variant array. */
    public record ChantsDef(ChantLanguagesDef keyed, List<ChantDef> legacy) {
        public static final ChantsDef EMPTY = new ChantsDef(null, List.of());

        public static final Codec<ChantsDef> CODEC = Codec.either(ChantLanguagesDef.CODEC, ChantDef.CODEC.listOf())
                .xmap(
                        e -> e.left().isPresent()
                                ? new ChantsDef(e.left().get(), List.of())
                                : new ChantsDef(null, e.right().orElse(List.of())),
                        f -> f.keyed() != null ? Either.left(f.keyed()) : Either.right(f.legacy()));

        public boolean isEmpty() { return keyed == null && legacy.isEmpty(); }
    }

    /** 0.4.0 language-keyed chants: {@code "chants": {"languages": {"<lang>": ...}}}.
     * {@code trigger} (first line / L1 entry gate) and {@code cast} (spell-name
     * release line) are fixed per language — the shape guarantees all variants of
     * one language share them; {@code body} holds the variants' middle lines
     * (one element per variant, each an ordered list of middle lines). */
    public record ChantLanguagesDef(Map<String, LangChant> languages) {
        public static final Codec<ChantLanguagesDef> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.unboundedMap(Codec.STRING, LangChant.CODEC).fieldOf("languages")
                                .forGetter(ChantLanguagesDef::languages)
                ).apply(instance, ChantLanguagesDef::new));

        public record LangChant(LineMeta trigger, LineMeta cast, List<List<LineMeta>> body) {
            public static final Codec<LangChant> CODEC = RecordCodecBuilder.create(instance ->
                    instance.group(
                            LineMeta.CODEC.fieldOf("trigger").forGetter(LangChant::trigger),
                            LineMeta.CODEC.fieldOf("cast").forGetter(LangChant::cast),
                            LineMeta.CODEC.listOf().listOf().optionalFieldOf("body", List.of())
                                    .forGetter(LangChant::body)
                    ).apply(instance, LangChant::new));
        }
    }

    /** New-format chant line: recognition metadata only (display follows the
     * grammar bucket — the player's engine language — so there is no display_key). */
    public record LineMeta(List<String> aliases, List<String> ipa) {
        public static final Codec<LineMeta> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.listOf().optionalFieldOf("aliases", List.of()).forGetter(LineMeta::aliases),
                        Codec.STRING.listOf().optionalFieldOf("ipa", List.of()).forGetter(LineMeta::ipa)
                ).apply(instance, LineMeta::new));
    }

    /** The {@code chant_policy} block (0.4.0): power tiers, skip permission,
     * interruptibility and the optional pact. Unset booleans default to true;
     * an absent/inert pact ({@code power_multiplier} 1.0) maps to {@code null}. */
    public record ChantPolicyDef(List<Float> powerPerLine, boolean skipAllowed, boolean interruptible,
                                 PactDef pact) {
        public static final ChantPolicyDef EMPTY = new ChantPolicyDef(List.of(), true, true, PactDef.INERT);

        public static final Codec<ChantPolicyDef> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.FLOAT.listOf().optionalFieldOf("power_per_line", List.of())
                                .forGetter(ChantPolicyDef::powerPerLine),
                        Codec.BOOL.optionalFieldOf("skip_allowed", true).forGetter(ChantPolicyDef::skipAllowed),
                        Codec.BOOL.optionalFieldOf("interruptible", true).forGetter(ChantPolicyDef::interruptible),
                        PactDef.CODEC.optionalFieldOf("pact", PactDef.INERT).forGetter(ChantPolicyDef::pact)
                ).apply(instance, ChantPolicyDef::new));

        public com.theo.wizardreal.api.ChantPolicy toPolicy() {
            float[] tiers = powerPerLine.isEmpty() ? null : new float[powerPerLine.size()];
            if (tiers != null) {
                for (int i = 0; i < tiers.length; i++) tiers[i] = powerPerLine.get(i);
            }
            boolean inertPact = pact == null || PactDef.INERT.equals(pact);
            return new com.theo.wizardreal.api.ChantPolicy(tiers, skipAllowed, interruptible,
                    inertPact ? null
                            : new com.theo.wizardreal.api.ChantPolicy.Pact(pact.require(), pact.powerMultiplier()));
        }

        public record PactDef(String require, float powerMultiplier) {
            /** Absent-pact placeholder: all-lines requirement with no multiplier. */
            public static final PactDef INERT = new PactDef(
                    com.theo.wizardreal.api.ChantPolicy.Pact.ALL_LINES, 1.0f);

            public static final Codec<PactDef> CODEC = RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.optionalFieldOf("require",
                                    com.theo.wizardreal.api.ChantPolicy.Pact.ALL_LINES).forGetter(PactDef::require),
                            Codec.FLOAT.optionalFieldOf("power_multiplier", 1.5f).forGetter(PactDef::powerMultiplier)
                    ).apply(instance, PactDef::new));
        }
    }

    /** Chant-stage ladder entry ({@code chant_stages}, magic_eco 03): qualitative
     * upgrade tiers gated by completed chant lines AND mastery percent. */
    public record StageDef(int afterLines, float mastery, List<SpellEffect> effects,
                           Integer manaCost, Integer cooldownTicks) {
        public static final Codec<StageDef> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("after_lines").forGetter(StageDef::afterLines),
                        Codec.floatRange(0f, 100f).optionalFieldOf("mastery", 0f).forGetter(StageDef::mastery),
                        EffectRegistry.codec().listOf().fieldOf("effects").forGetter(StageDef::effects),
                        Codec.INT.optionalFieldOf("mana_cost").forGetter(s -> java.util.Optional.ofNullable(s.manaCost())),
                        Codec.INT.optionalFieldOf("cooldown_ticks").forGetter(s -> java.util.Optional.ofNullable(s.cooldownTicks()))
                ).apply(instance, (after, mastery, eff, mana, cd) ->
                        new StageDef(after, mastery, eff, mana.orElse(null), cd.orElse(null))));
    }

    /** Ritual chant variants (empty for instant spells). Legacy array format. */
    public record ChantDef(List<LineDef> lines) {
        public static final Codec<ChantDef> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        LineDef.CODEC.listOf().fieldOf("lines").forGetter(ChantDef::lines)
                ).apply(instance, ChantDef::new));

        /** One chant line: display lang key + recognizer metadata. */
        public record LineDef(String displayKey, List<String> aliases, List<String> ipa) {
            public static final Codec<LineDef> CODEC = RecordCodecBuilder.create(instance ->
                    instance.group(
                            Codec.STRING.fieldOf("display_key").forGetter(LineDef::displayKey),
                            Codec.STRING.listOf().optionalFieldOf("aliases", List.of()).forGetter(LineDef::aliases),
                            Codec.STRING.listOf().optionalFieldOf("ipa", List.of()).forGetter(LineDef::ipa)
                    ).apply(instance, LineDef::new));
        }
    }
}
