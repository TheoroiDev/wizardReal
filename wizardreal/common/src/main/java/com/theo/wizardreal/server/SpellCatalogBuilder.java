package com.theo.wizardreal.server;

import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Builds the {@link CatalogPayload} for one player: every registered spell
 * grouped by origin (insertion order kept), with the player's learned flags
 * from {@link PlayerMagicState}.
 */
public final class SpellCatalogBuilder {

    private SpellCatalogBuilder() {}

    public static CatalogPayload build(MinecraftServer server, UUID player) {
        PlayerMagicState state = PlayerMagicState.get(server);
        Map<String, List<CatalogPayload.CatalogSpell>> byOrigin = new LinkedHashMap<>();
        for (Spell spell : SpellRegistry.all()) {
            boolean learned = state.knowsSpell(player, spell.id());
            boolean requiresLearning = spell.requiresLearning();
            List<String> schools = spell.schools().stream()
                    .map(school -> school.name().toLowerCase(Locale.ROOT)).toList();
            List<List<String>> chants = new ArrayList<>();
            for (var chant : spell.chants()) {
                chants.add(chant.lines().stream().map(ChantLine::displayKey).toList());
            }
            var pronunciation = spell.pronunciation();
            var entry = new CatalogPayload.CatalogSpell(
                    spell.id(), spell.nameKey(), learned, requiresLearning, !spell.chants().isEmpty(),
                    schools, spell.manaCost(), spell.cooldownTicks() / 20f,
                    pronunciation == null ? List.of() : pronunciation.aliases(),
                    pronunciation == null ? List.of() : pronunciation.ipa(),
                    chants);
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
}
