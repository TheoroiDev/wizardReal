package com.theo.wizardreal.spell;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.effect.EffectRegistry;
import com.theo.wizardreal.effect.SpellEffect;
import com.theo.wizardreal.api.CastContext;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.4.0 spell schema dual-read: language-keyed chants + trigger language
 * buckets (new), alongside the legacy flat aliases + chant variant array. */
class SpellDefinitionParseTest {

    public record TestEffect(int n) implements SpellEffect {
        public static final com.mojang.serialization.MapCodec<TestEffect> CODEC =
                com.mojang.serialization.codecs.RecordCodecBuilder.mapCodec(i -> i.group(
                        com.mojang.serialization.Codec.INT.optionalFieldOf("n", 0).forGetter(TestEffect::n)
                ).apply(i, TestEffect::new));

        @Override public ResourceLocation effectId() { return new ResourceLocation("wizardreal", "test_effect"); }
        @Override public void apply(CastContext ctx) { /* never invoked in parse tests */ }
    }

    @BeforeAll
    static void registerTestEffect() {
        EffectRegistry.register(new ResourceLocation("wizardreal", "test_effect"), TestEffect.CODEC);
    }

    private static SpellDefinition parse(String json) {
        return SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElseThrow();
    }

    private static final String HEAD = """
            {"id":"wizardreal:t","schools":["fire"],
             "effects":[{"type":"wizardreal:test_effect"}],
            """;

    @Test
    void legacyFormatParsesAndExpands() {
        SpellDefinition def = parse(HEAD + """
                "trigger":{"aliases":["ignis","fire"],"ipa":["ˈɪɡnɪs"]},
                "chants":[{"lines":[
                    {"display_key":"t.l1","aliases":["line one"]},
                    {"display_key":"t.l2","aliases":["ignis"]}]}]
                }""");
        DataSpell spell = def.toSpell();
        // Legacy trigger: flat aliases form the legacy bucket (no language routing).
        assertTrue(spell.pronunciation().languages().isEmpty());
        assertEquals(List.of("ignis", "fire"), spell.pronunciation().aliases());
        assertEquals(1, spell.chants().size());
        assertEquals("t.l1", spell.chants().get(0).lines().get(0).displayKey());
        assertEquals("t.l1", spell.chants().get(0).lines().get(0).displayText());
        assertEquals("wizardreal:t.chant.0:0",
                spell.chants().get(0).lines().get(0).pronunciation().id());
    }

    @Test
    void languageKeyedFormatParsesAndExpands() {
        SpellDefinition def = parse(HEAD + """
                "trigger":{"languages":{"en":["ignis"],"zh":["火球"]},"ipa":["ˈɪɡnɪs"]},
                "chants":{"languages":{
                    "en":{"trigger":{"aliases":["o flame"]},
                          "cast":{"aliases":["ignis"]},
                          "body":[[{"aliases":["variant-a mid"]}],[{"aliases":["variant-b"]}]]},
                    "zh":{"trigger":{"aliases":["炽焰啊"]},
                          "cast":{"aliases":["火球"]},
                          "body":[[{"aliases":["变体甲中段"]}],[{"aliases":["变体乙中段"]}]]}}}
                }""");
        DataSpell spell = def.toSpell();
        // Trigger carries the language buckets; flat view is the union.
        assertEquals(Map.of("en", List.of("ignis"), "zh", List.of("火球")),
                spell.pronunciation().languages());
        assertEquals(List.of("ignis", "火球"), spell.pronunciation().aliases());
        // 2 languages x 2 body variants = 4 chains, each trigger + 1 mid + cast.
        assertEquals(4, spell.chants().size());
        Chant enVariant0 = spell.chants().get(0);
        assertEquals(3, enVariant0.lines().size());
        ChantLine trigger = enVariant0.lines().get(0);
        assertEquals("wizardreal:t.chant.en.0:0", trigger.pronunciation().id());
        assertNull(trigger.displayKey());
        // Display follows the grammar bucket: the line's language-bucket primary alias.
        assertEquals("o flame", trigger.displayText());
        assertEquals(Map.of("en", List.of("o flame")), trigger.pronunciation().languages());
        assertEquals("ignis", enVariant0.lines().get(2).displayText());
        // Second variant of the same language shares trigger/cast text (by shape).
        Chant enVariant1 = spell.chants().get(1);
        assertEquals("o flame", enVariant1.lines().get(0).displayText());
        assertEquals("variant-b", enVariant1.lines().get(1).displayText());
        // zh chains carry zh buckets.
        Chant zhVariant0 = spell.chants().get(2);
        assertEquals("炽焰啊", zhVariant0.lines().get(0).displayText());
        assertEquals(Map.of("zh", List.of("炽焰啊")), zhVariant0.lines().get(0).pronunciation().languages());
    }

    @Test
    void triggerAliasesStayOptionalWhenLanguagesPresent() {
        SpellDefinition def = parse(HEAD + """
                "trigger":{"languages":{"en":["ignis"]}}
                }""");
        Pronunciation p = def.toSpell().pronunciation();
        assertEquals(List.of("ignis"), p.aliases());
        assertEquals(List.of("ignis"), p.aliasesFor("en"));
    }

    @Test
    void instantSpellHasNoChants() {
        SpellDefinition def = parse(HEAD + """
                "trigger":{"aliases":["ignis"]}}
                """);
        assertTrue(def.chants().isEmpty());
        assertTrue(def.toSpell().chants().isEmpty());
    }

    @Test
    void emptyBodyYieldsSingleTwoLineVariant() {
        SpellDefinition def = parse(HEAD + """
                "trigger":{"languages":{"en":["ignis"]}},
                "chants":{"languages":{"en":{
                    "trigger":{"aliases":["o flame"]},"cast":{"aliases":["ignis"]}}}}
                }""");
        List<Chant> chants = def.toSpell().chants();
        assertEquals(1, chants.size());
        assertEquals(2, chants.get(0).lines().size());
    }

    @Test
    void chantPolicyParsesIntoDataSpell() {
        SpellDefinition def = parse(HEAD + """
                "trigger":{"aliases":["x"]},
                "chant_policy":{"power_per_line":[0.5,0.75,1.0],"skip_allowed":false,
                    "pact":{"require":"all_lines","power_multiplier":1.5}}
                }""");
        com.theo.wizardreal.api.ChantPolicy p = def.toSpell().chantPolicy();
        org.junit.jupiter.api.Assertions.assertFalse(p.skipAllowed());
        assertTrue(p.interruptible());
        assertEquals(3, p.powerPerLine().length);
        assertEquals(0.5f, p.powerFor(1));
        assertEquals(1.0f, p.powerFor(3));
        // Beyond the table: clamps to the last entry.
        assertEquals(1.0f, p.powerFor(6));
        assertEquals(1.5f, p.pact().powerMultiplier());
    }
}
