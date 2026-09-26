package com.theo.wizardreal.spell;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lang name lock (wizardReal#42): every shipped spell resolves its display
 * name through {@code AbstractSpell.nameKey()} = {@code spell.<id>.name} with
 * the NAMESPACED (colon) id. This test pins the lang files to that contract —
 * the 0.6.0 audit found 59 of 75 spells carrying legacy dot-style keys that
 * rendered as raw keys in-game.
 */
class SpellLangNameLockTest {

    private static final Path SPELLS_DIR =
            Path.of("src/main/resources/data/wizardreal/voicecast/spells");
    private static final Path LANG_DIR =
            Path.of("src/main/resources/assets/wizardreal/lang");

    /** spell.wizardreal[.:]<short>.(name|stage.N) — spell-name-shaped keys. */
    private static final Pattern SPELL_KEY =
            Pattern.compile("^spell\\.wizardreal[.:]([a-z0-9_]+)\\.(name|stage\\.\\d)$");

    private static Map<String, String> lang(String file) throws IOException {
        Map<String, String> out = new HashMap<>();
        var obj = JsonParser.parseString(Files.readString(LANG_DIR.resolve(file))).getAsJsonObject();
        for (var e : obj.entrySet()) out.put(e.getKey(), e.getValue().getAsString());
        return out;
    }

    private static List<String> spellIds() throws IOException {
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(SPELLS_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                ids.add(JsonParser.parseString(Files.readString(file))
                        .getAsJsonObject().get("id").getAsString());
            }
        }
        assertFalse(ids.isEmpty(), "no spell JSONs found");
        return ids;
    }

    @Test
    void everySpellHasColonNameKeyInBothLangs() throws IOException {
        for (String id : spellIds()) {
            String key = "spell." + id + ".name";
            for (String lang : new String[] {"en_us.json", "zh_cn.json"}) {
                String value = lang(lang).get(key);
                assertTrue(value != null && !value.isBlank(),
                        "missing/blank display name: " + lang + " → " + key);
            }
        }
    }

    @Test
    void displayNamesArePairwiseDistinctPerLang() throws IOException {
        for (String lang : new String[] {"en_us.json", "zh_cn.json"}) {
            Map<String, String> table = lang(lang);
            Map<String, String> seen = new HashMap<>();
            for (String id : spellIds()) {
                String name = table.get("spell." + id + ".name");
                String prev = seen.put(name, id);
                assertTrue(prev == null,
                        "duplicate display name '" + name + "' on " + id + " and " + prev
                                + " (" + lang + ") — 同屏重名");
            }
        }
    }

    @Test
    void noLegacyDotStyleAndNoOrphanSpellNameKeys() throws IOException {
        Set<String> shorts = new HashSet<>();
        for (String id : spellIds()) shorts.add(id.substring("wizardreal:".length()));
        for (String lang : new String[] {"en_us.json", "zh_cn.json"}) {
            for (String key : lang(lang).keySet()) {
                var m = SPELL_KEY.matcher(key);
                if (!m.matches()) continue;
                assertTrue(shorts.contains(m.group(1)),
                        "spell-name key without a backing spell JSON (dead or retired): "
                                + lang + " → " + key);
                assertFalse(key.startsWith("spell.wizardreal."),
                        "legacy dot-style spell key (runtime resolves colon style): " + key);
            }
        }
    }
}
