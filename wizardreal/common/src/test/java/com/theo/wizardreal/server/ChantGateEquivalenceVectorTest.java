package com.theo.wizardreal.server;

import com.theo.voicecast.api.Alternative;
import com.theo.voicecast.api.Decision;
import com.theo.voicecast.api.RecognitionResult;
import com.theo.voicecast.model.Json;
import com.theo.wizardreal.TestSpell;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Pronunciation;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared equivalence vectors, WizardReal side (semantic contract v2, C1b G3):
 * the SAME c1b_vectors.json drives the voicecast adjudicator tests and this
 * gate test (byte-identical copies). The voicecast side pins the DECISION
 * (what was said); this side pins the GAMEPLAY OUTCOME (kind + variant) the
 * pre-v2 chain produced for the same utterance — the vectors prove the
 * refactor did not change any spellId verdict end to end.
 */
class ChantGateEquivalenceVectorTest {

    private static final float DELTA = 1e-4f;

    @AfterEach
    void clearRegistry() {
        SpellRegistry.clear();
    }

    @Test
    void sharedVectorsMapToTheSameGameplayOutcomes() throws Exception {
        Map<String, Object> doc = loadDoc();
        List<Map<String, Object>> vocabulary = mapList(doc.get("vocabulary"));
        List<Map<String, Object>> vectors = mapList(doc.get("vectors"));
        assertTrue(vectors.size() >= 30, "at least 30 shared vectors required");
        SpellRegistry.clear();
        buildRegistry(vocabulary);

        int mismatched = 0;
        List<String> failures = new ArrayList<>();
        for (Map<String, Object> vec : vectors) {
            Map<String, Object> expected = Json.getMap(vec, "expected");
            RecognitionResult result = resultOf(expected);
            int level = (int) longOf(vec.get("level"));
            ChantGate.Decision d = ChantGate.route(result, level);

            String id = Json.getString(vec, "id", "?");
            ChantGate.Kind expKind = ChantGate.Kind.valueOf(Json.getString(expected, "kind", "NONE"));
            Object expVariant = expected.get("variant");
            List<String> problems = new ArrayList<>();
            if (d.kind() != expKind) problems.add("kind " + d.kind() + " != " + expKind);
            if (d.spell() == null && !expKind.equals(ChantGate.Kind.NONE)) {
                problems.add("spell null");
            } else if (d.spell() != null && !Json.getString(expected, "spellId", "").equals(d.spell().id())) {
                problems.add("spell " + d.spell().id() + " != " + Json.getString(expected, "spellId", ""));
            }
            if (expVariant == null) {
                if (d.variant() != -1) problems.add("variant " + d.variant() + " != -1");
            } else if (d.variant() != (int) longOf(expVariant)) {
                problems.add("variant " + d.variant() + " != " + expVariant);
            }
            if (!problems.isEmpty()) {
                mismatched++;
                if (failures.size() < 10) failures.add(id + ": " + String.join("; ", problems));
            }
        }
        assertEquals(0, mismatched, "equivalence vectors failed: " + failures);
    }

    // ------------------------------------------------------------- harness

    /** RecognitionResult built from the vector's EXPECTED decision fields —
     *  what voicecast's adjudicated final carries over the wire. */
    private static RecognitionResult resultOf(Map<String, Object> expected) {
        Decision decision = Decision.valueOf(Json.getString(expected, "decision", "REJECTED"));
        List<Alternative> alts = new ArrayList<>();
        for (Object o : expected.containsKey("alternatives") ? Json.getList(expected, "alternatives") : List.of()) {
            Map<String, Object> a = Json.asMap(o);
            alts.add(new Alternative(Json.getString(a, "spellId", ""), Json.getString(a, "pronId", ""),
                    (float) doubleOf(a.get("score"))));
        }
        return RecognitionResult.finality("heard", "", "", decision,
                Json.getString(expected, "spellId", ""), Json.getString(expected, "pronId", ""),
                expected.get("score") == null ? 0f : (float) doubleOf(expected.get("score")),
                alts, 0L);
    }

    /** Registry from the vector vocabulary: trigger rows become spells, chant
     *  line rows group into variants by (spell, lang, variant) in vocabulary
     *  order (mirroring the language-keyed spell JSON expansion). */
    private static void buildRegistry(List<Map<String, Object>> vocabulary) {
        Map<String, TestSpellBuilder> builders = new LinkedHashMap<>();
        for (Map<String, Object> e : vocabulary) {
            String id = Json.getString(e, "id", "");
            String spellId = id.contains(".chant.") ? id.substring(0, id.indexOf(".chant.")) : id;
            List<String> ipa = Json.getStringList(e, "ipa");
            List<String> aliases = Json.getStringList(e, "aliases");
            Map<String, List<String>> languages = new LinkedHashMap<>();
            if (e.get("languages") instanceof Map<?, ?> langs) {
                for (Map.Entry<?, ?> en : langs.entrySet()) {
                    List<String> bucket = new ArrayList<>();
                    for (Object o : (List<?>) en.getValue()) bucket.add(String.valueOf(o));
                    languages.put(String.valueOf(en.getKey()), bucket);
                }
            }
            Pronunciation pron = new Pronunciation(id, ipa, aliases, languages);
            if (id.contains(".chant.")) {
                // <spell>.chant.[lang.]<variant>:<index>
                String rest = id.substring(id.indexOf(".chant.") + 7);
                String lang = "";
                int colon = rest.indexOf(':');
                String head = rest.substring(0, colon);
                int index = Integer.parseInt(rest.substring(colon + 1));
                int dot = head.indexOf('.');
                if (dot >= 0) {
                    lang = head.substring(0, dot);
                    head = head.substring(dot + 1);
                }
                int variant = Integer.parseInt(head);
                builders.computeIfAbsent(spellId, k -> new TestSpellBuilder())
                        .line(lang, variant, index, pron);
            } else {
                builders.computeIfAbsent(spellId, k -> new TestSpellBuilder()).trigger(pron);
            }
        }
        for (Map.Entry<String, TestSpellBuilder> en : builders.entrySet()) {
            SpellRegistry.replace(en.getValue().build(en.getKey()));
        }
    }

    /** One spell under construction. */
    private static final class TestSpellBuilder {
        private Pronunciation trigger;
        private final List<String> groupOrder = new ArrayList<>();
        private final Map<String, java.util.TreeMap<Integer, Pronunciation>> groups = new LinkedHashMap<>();

        void trigger(Pronunciation p) {
            this.trigger = p;
        }

        void line(String lang, int variant, int index, Pronunciation p) {
            String key = lang + "|" + variant;
            if (!groups.containsKey(key)) {
                groups.put(key, new java.util.TreeMap<>());
                groupOrder.add(key);
            }
            groups.get(key).put(index, p);
        }

        Spell build(String spellId) {
            List<Chant> out = new ArrayList<>();
            for (String key : groupOrder) {
                java.util.TreeMap<Integer, Pronunciation> lines = groups.get(key);
                List<ChantLine> chantLines = new ArrayList<>();
                for (int i = 0; i <= lines.lastKey(); i++) {
                    Pronunciation p = lines.get(i);
                    chantLines.add(new ChantLine(null, p != null ? p : new Pronunciation(
                            spellId + ".line" + i, List.of(), List.of())));
                }
                out.add(new Chant(chantLines));
            }
            return new TestSpell(spellId, trigger != null ? trigger
                    : new Pronunciation(spellId, List.of(), List.of()), -1f, out);
        }
    }

    private static Map<String, Object> loadDoc() throws IOException {
        String text = Files.readString(resource("c1b/c1b_vectors.json"), StandardCharsets.UTF_8);
        return Json.parseObject(text);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Object v) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : (List<Object>) v) out.add((Map<String, Object>) o);
        return out;
    }

    private static double doubleOf(Object o) {
        return ((Number) o).doubleValue();
    }

    private static long longOf(Object o) {
        return ((Number) o).longValue();
    }

    private static Path resource(String name) throws IOException {
        try {
            return Path.of(ChantGateEquivalenceVectorTest.class.getResource("/" + name).toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }
}
