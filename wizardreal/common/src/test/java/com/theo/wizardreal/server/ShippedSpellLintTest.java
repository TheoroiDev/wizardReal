package com.theo.wizardreal.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.effect.BuiltinEffects;
import com.theo.wizardreal.spell.SpellDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lint gate over the shipped spell roster: the load-time validator's findings
 * must be EMPTY for the L1-distinctness and chant-stage ladder rules (both are
 * authored-data defects that silently degrade recognition at runtime — they
 * must fail CI instead of only logging at server load).
 */
class ShippedSpellLintTest {

    private static final Path SPELLS_DIR =
            Path.of("src/main/resources/data/wizardreal/voicecast/spells");

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        BuiltinEffects.register();
    }

    @Test
    void shippedSpellsPassLoadValidation() throws IOException {
        List<Spell> spells = new ArrayList<>();
        try (Stream<Path> files = Files.list(SPELLS_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(file)));
                assertTrue(result.result().isPresent(),
                        "shipped spell JSON failed to parse: " + file.getFileName());
                spells.add(result.result().get().toSpell());
            }
        }
        List<String> warnings = SpellLoadValidator.validate(spells);
        List<String> blocking = warnings.stream()
                .filter(w -> w.startsWith("L1 conflict") || w.startsWith("chant_stages"))
                .toList();
        assertTrue(blocking.isEmpty(), "shipped spells failed lint:\n" + String.join("\n", blocking));
    }
}
