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
            root.put("format", 4);
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

    /**
     * Export schema v3 (catalog v4): language-annotated trigger + structured
     * chant lines. Line {@code text} is the resolved display text (legacy
     * lang keys go through I18n; language-keyed lines are literal text that
     * passes through unchanged); {@code key} keeps the raw wire value for
     * traceability; {@code readings} is the server-derived annotation map
     * (pinyin/romaji/ipa — present keys only, may be empty).
     */
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
        json.put("difficulty", spell.difficulty());
        json.put("learning", spell.learning());
        json.put("skip_allowed", spell.skipAllowed());

        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("aliases", spell.triggerAliases());
        trigger.put("ipa", spell.ipa());
        json.put("trigger", trigger);

        json.put("effects", spell.descKeys());

        Map<String, Object> chants = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<CatalogPayload.CatalogLine>>> e
                : spell.chantVariants().entrySet()) {
            List<Object> variants = new ArrayList<>();
            for (List<CatalogPayload.CatalogLine> lines : e.getValue()) {
                List<Object> lineJson = new ArrayList<>();
                for (CatalogPayload.CatalogLine line : lines) {
                    Map<String, Object> lineEntry = new LinkedHashMap<>();
                    lineEntry.put("key", line.text());
                    lineEntry.put("text", I18n.get(line.text()));
                    lineEntry.put("readings", line.readings());
                    lineJson.add(lineEntry);
                }
                Map<String, Object> chant = new LinkedHashMap<>();
                chant.put("lines", lineJson);
                variants.add(chant);
            }
            chants.put(e.getKey(), variants);
        }
        json.put("chants", chants);

        List<Object> stages = new ArrayList<>();
        for (CatalogPayload.CatalogStage stage : spell.stages()) {
            Map<String, Object> stageJson = new LinkedHashMap<>();
            stageJson.put("after_lines", stage.afterLines());
            stageJson.put("mastery", stage.mastery());
            if (stage.manaCost() >= 0) stageJson.put("mana_cost", stage.manaCost());
            if (stage.cooldownSeconds() >= 0) stageJson.put("cooldown_seconds", stage.cooldownSeconds());
            stageJson.put("effects", stage.descKeys());
            stages.add(stageJson);
        }
        if (!stages.isEmpty()) json.put("chant_stages", stages);
        return json;
    }
}
