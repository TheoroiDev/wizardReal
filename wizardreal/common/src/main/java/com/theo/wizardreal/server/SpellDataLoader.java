package com.theo.wizardreal.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.spell.SpellDefinition;
import com.theo.wizardreal.spell.Spells;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datapack spell loading: scans {@code data/<ns>/voicecast/spells/*.json}
 * from a {@link ResourceManager}, parses each with {@link SpellDefinition}
 * codec, and (on the server thread) rebuilds the {@code SpellRegistry}.
 *
 * <p>Built-in Java spells are re-registered first; datapack definitions with
 * the same id override them (handy for tuning). After (re)registration, active
 * chants are cancelled and the recognizer vocabulary is re-pushed.
 *
 * <p>Platform modules call {@link #collect} from their reload listener
 * (Fabric {@code SimpleSynchronousResourceReloadListener} / Forge
 * {@code AddReloadListenerEvent}) and hand the result to {@link #apply}.
 */
public final class SpellDataLoader {
    public static final String DIRECTORY = "voicecast/spells";

    /**
     * Server instance captured at SERVER_STARTING (datapack load runs between
     * STARTING and STARTED, so it must already be set when reload listeners fire).
     */
    private static volatile MinecraftServer server;

    /** Files collected before a server existed (client boot); flushed by {@link #setServer}. */
    private static volatile List<Loaded> pending;

    /** One successfully parsed spell file. */
    public record Loaded(ResourceLocation fileId, SpellDefinition definition) {}

    private SpellDataLoader() {}

    /** Called from the server lifecycle hooks. */
    public static void setServer(MinecraftServer s) {
        server = s;
        List<Loaded> p = pending;
        if (s != null && p != null) {
            pending = null;
            s.execute(() -> registerAll(p));
        }
    }

    /**
     * Parse every spell JSON under {@code data/<ns>/voicecast/spells/}.
     * Failures are logged and skipped. Uses {@code result()} (not partial):
     * a file whose effects fail to resolve is skipped entirely rather than
     * registered as a no-op spell.
     */
    public static List<Loaded> collect(ResourceManager manager) {
        Map<ResourceLocation, Resource> files =
                manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"));
        List<Loaded> out = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Resource> entry : files.entrySet()) {
            try (BufferedReader reader = entry.getValue().openAsReader()) {
                var result = SpellDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader));
                Optional<SpellDefinition> ok = result.result();
                if (ok.isPresent()) {
                    out.add(new Loaded(entry.getKey(), ok.get()));
                } else {
                    result.error().ifPresent(error -> WizardReal.LOGGER.warn(
                            "Spell parse failed in {}: {}", entry.getKey(), error));
                }
            } catch (Exception e) {
                WizardReal.LOGGER.warn("Failed to read spell file {}", entry.getKey(), e);
            }
        }
        return out;
    }

    /** Marshal registration onto the server thread. Safe from any reload executor. */
    public static void apply(List<Loaded> loaded) {
        MinecraftServer s = server;
        if (s == null) {
            pending = loaded;
            WizardReal.LOGGER.info("Spell reload deferred: no server instance yet ({} files pending)",
                    loaded.size());
            return;
        }
        s.execute(() -> registerAll(loaded));
    }

    private static void registerAll(List<Loaded> loaded) {
        SpellRegistry.clear();
        Spells.register(); // built-ins first...
        int builtin = SpellRegistry.all().size();
        for (Loaded file : loaded) {
            try {
                SpellRegistry.replace(file.definition().toSpell()); // datapack overrides same-id built-ins
            } catch (Exception e) {
                WizardReal.LOGGER.warn("Failed to register spell from {}: {}",
                        file.fileId(), e.getMessage());
            }
        }
        ChantManager.get().clearAll(server);
        ServerVoiceCast.pushVocabulary();
        // Catalog publication point: the registry was rebuilt (ids may have
        // changed), so every online player's snapshot is stale.
        SpellCatalogService.publishAll();
        WizardReal.LOGGER.info("Spell registry reloaded: {} builtin + {} datapack ({} files parsed)",
                builtin, SpellRegistry.all().size() - builtin, loaded.size());
    }
}
