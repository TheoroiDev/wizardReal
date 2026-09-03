package com.theo.wizardreal.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.effect.EffectRegistry;
import com.theo.wizardreal.effect.SpellEffect;
import com.theo.voicecast.api.Pronunciation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
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
 *   "trigger": { "aliases": [...], "ipa": [...] },
 *   "chants": [ { "lines": [ { "display_key": "...", "aliases": [...], "ipa": [...] } ] } ],
 *   "effects": [ { "type": "wizardreal:projectile", ... } ]
 * }
 * </pre>
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
        List<ChantDef> chants,
        List<SpellEffect> effects
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
                    ChantDef.CODEC.listOf().optionalFieldOf("chants", List.of()).forGetter(SpellDefinition::chants),
                    EffectRegistry.codec().listOf().fieldOf("effects").forGetter(SpellDefinition::effects)
            ).apply(instance, SpellDefinition::new));

    /** Convert to the live {@link Spell} instance registered in {@code SpellRegistry}. */
    public DataSpell toSpell() {
        List<Chant> builtChants = new ArrayList<>();
        for (int v = 0; v < chants.size(); v++) {
            ChantDef cd = chants.get(v);
            List<ChantLine> lines = new ArrayList<>();
            for (int i = 0; i < cd.lines().size(); i++) {
                ChantDef.LineDef ln = cd.lines().get(i);
                lines.add(new ChantLine(ln.displayKey(),
                        new Pronunciation(id + ".chant." + v + ":" + i, ln.ipa(), ln.aliases())));
            }
            builtChants.add(new Chant(lines));
        }
        Pronunciation pronunciation = new Pronunciation(id.toString(), trigger.ipa(), trigger.aliases());
        return new DataSpell(id, schools, manaCost, cooldownTicks, requiresLearning, origin,
                threshold, pronunciation, builtChants, effects);
    }

    /** Trigger word / phrase metadata: the utterance that starts (or casts) the spell. */
    public record TriggerDef(List<String> aliases, List<String> ipa) {
        public static final Codec<TriggerDef> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.STRING.listOf().fieldOf("aliases").forGetter(TriggerDef::aliases),
                        Codec.STRING.listOf().optionalFieldOf("ipa", List.of()).forGetter(TriggerDef::ipa)
                ).apply(instance, TriggerDef::new));
    }

    /** Ritual chant variants (empty for instant spells). */
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
