package com.theo.wizardreal.spell;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.theo.wizardreal.effect.BuiltinEffects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every shipped datapack spell must parse through {@link SpellDefinition#CODEC}
 * (batch-0 rewrite guard: one malformed file silently drops a spell). */
class ShippedSpellResourcesTest {

    private static final Path SPELLS_DIR =
            Path.of("src/main/resources/data/wizardreal/voicecast/spells");

    @BeforeAll
    static void bootstrap() {
        // effect codecs read BuiltInRegistries (entity types etc.) — needs the
        // vanilla bootstrap before any CODEC static-init runs in a plain JVM.
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // sibling lint tests bootstrap in the same JVM — registration is idempotent
        if (com.theo.wizardreal.effect.EffectRegistry.get(
                new net.minecraft.resources.ResourceLocation("wizardreal", "projectile")) == null) {
            BuiltinEffects.register();
        }
    }

    @Test
    void allShippedSpellJsonsParse() throws IOException {
        assertTrue(Files.isDirectory(SPELLS_DIR), "spell resource dir missing: " + SPELLS_DIR.toAbsolutePath());
        int count = 0;
        try (Stream<Path> files = Files.list(SPELLS_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(file)));
                var error = result.error().map(Object::toString).orElse("");
                assertTrue(result.result().isPresent(),
                        "shipped spell JSON failed to parse: " + file.getFileName() + " — " + error);
                count++;
            }
        }
        assertTrue(count >= 15, "expected the batch-0 matrix (>=15 spells), got " + count);
    }

    @Test
    void allShippedSpellsPassLoadValidation() throws IOException {
        // The same lint the server runs on /reload, executed over the shipped
        // datapack so generator regressions fail the build instead of surfacing
        // as runtime warnings. IPA coverage gaps are expected inside the
        // ipafill batch window (the load report says the same).
        var spells = new java.util.ArrayList<com.theo.wizardreal.api.Spell>();
        try (Stream<Path> files = Files.list(SPELLS_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var spell = SpellDefinition.CODEC.parse(JsonOps.INSTANCE,
                                JsonParser.parseString(Files.readString(file)))
                        .result().orElseThrow();
                spells.add(spell.toSpell());
            }
        }
        var warnings = com.theo.wizardreal.server.SpellLoadValidator.validate(spells);
        var hard = warnings.stream()
                .filter(w -> !w.startsWith("IPA coverage gap"))
                .toList();
        assertTrue(hard.isEmpty(), "shipped spells failed load validation:\n  "
                + String.join("\n  ", hard));
    }
}
