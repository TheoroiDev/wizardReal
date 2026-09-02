package com.theo.wizardreal.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.config.WizardRealConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

/**
 * Client-side holder of the latest {@link CatalogPayload} (cached for future
 * tooling even when the file export is off) and writer of
 * {@code <game-dir>/wizardreal/spell_catalog.json}.
 *
 * <p>Names/lines are resolved with the active game language, falling back to
 * the raw key ({@code I18n.get} behavior) per the export contract. Writing is
 * defensive: any failure logs and skips — the cache stays usable.
 */
public final class SpellCatalogState {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static volatile CatalogPayload last;

    private SpellCatalogState() {}

    public static CatalogPayload last() {
        return last;
    }

    /** Called on the client thread after a spell_catalog packet arrives. */
    public static void handle(CatalogPayload payload) {
        last = payload;
        com.theo.wizardreal.net.SpellCatalogCache.set(payload);  // common-safe view for tabs/tinting
        WizardRealConfig config = WizardRealConfig.load(Minecraft.getInstance().gameDirectory.toPath());
        if (config.fileMode() != WizardRealConfig.FileMode.OFF) {
            writeExport(payload, config.fileMode());
        }
    }

    private static void writeExport(CatalogPayload payload, WizardRealConfig.FileMode mode) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Path file = mc.gameDirectory.toPath().resolve("wizardreal").resolve("spell_catalog.json");

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("format", 1);
            root.put("player", mc.getUser().getName());
            root.put("language", mc.getLanguageManager().getSelected());

            Map<String, Object> origins = new LinkedHashMap<>();
            for (CatalogPayload.CatalogOrigin origin : payload.origins()) {
                Map<String, Object> originJson = new LinkedHashMap<>();
                originJson.put("name", I18n.get(origin.nameKey()));
                List<Object> spells = new ArrayList<>();
                for (CatalogPayload.CatalogSpell spell : origin.spells()) {
                    if (mode == WizardRealConfig.FileMode.CASTABLE
                            && !spell.learned() && spell.requiresLearning()) {
                        continue;
                    }
                    spells.add(spellJson(spell));
                }
                originJson.put("spells", spells);
                origins.put(origin.id(), originJson);
            }
            root.put("origins", origins);

            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            WizardReal.LOGGER.info("Spell catalog written: {} ({} origins)", file, payload.origins().size());
        } catch (Exception e) {
            WizardReal.LOGGER.warn("Failed to write spell catalog export", e);
        }
    }

    private static Map<String, Object> spellJson(CatalogPayload.CatalogSpell spell) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", spell.id());
        json.put("name", I18n.get(spell.nameKey()));
        json.put("learned", spell.learned());
        json.put("requires_learning", spell.requiresLearning());
        json.put("ritual", spell.ritual());
        json.put("schools", spell.schools());
        json.put("mana_cost", spell.manaCost());
        json.put("cooldown_seconds", spell.cooldownSeconds());

        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("aliases", spell.aliases());
        trigger.put("ipa", spell.ipa());
        json.put("trigger", trigger);

        List<Object> chants = new ArrayList<>();
        for (List<String> lines : spell.chantDisplayKeys()) {
            List<Object> lineJson = new ArrayList<>();
            for (String key : lines) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("key", key);
                line.put("text", I18n.get(key));
                lineJson.add(line);
            }
            Map<String, Object> chant = new LinkedHashMap<>();
            chant.put("lines", lineJson);
            chants.add(chant);
        }
        json.put("chants", chants);
        return json;
    }
}
