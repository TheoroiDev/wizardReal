package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.config.WizardRealConfig;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Zero-compile-time-dependency push of the spell catalog into the
 * <b>wizardpedia</b> compendium, over wizardpedia's public S2C channel
 * {@code wizardpedia:catalog} (PROVIDER_PUSH; contract v2 — see the
 * wizardpedia README "Provider integration").
 *
 * <p>Mapping (catalog contract):
 * <ul>
 *   <li>categories = spell origins ({@code origin.<id>} name key, one entry
 *       per origin present in the payload);</li>
 *   <li>entries = spells: {@code titleKey} = spell name key,
 *       {@code locked} = !learned, aliases = trigger words per language
 *       bucket + IPA (neutral), lines = chant lines per language
 *       (legacy lines are lang keys, language-keyed lines literal text).</li>
 * </ul>
 *
 * <p>Channel id + formatVersion are hardcoded here BY DESIGN — that is the
 * whole point of the wire contract (wizardreal must not depend on
 * wizardpedia at compile time). A format change in wizardpedia that bumps
 * its formatVersion will be rejected by wizardpedia's receiver with a warn,
 * never desync.
 *
 * <p>Gated by {@code [wizardpedia] pushMode} in
 * {@code config/wizardreal/wizardreal.toml} ({@code all|castable|off}) and by
 * {@code isModLoaded("wizardpedia")}. Push points are the same three as the
 * self export (see {@link SpellCatalogService}).
 */
public final class WizardpediaPublisher {

    /** wizardpedia:catalog wire contract (hardcoded per contract, v2). */
    private static final int FORMAT_VERSION = 2;
    private static final byte TYPE_PROVIDER_PUSH = 1;
    private static final ResourceLocation CHANNEL = new ResourceLocation("wizardpedia", "catalog");

    /** Origin id → icon item for known origins; unknown origins get no icon. */
    private static final Map<String, String> ORIGIN_ICONS = Map.of(
            "wizardreal:wizardry", "wizardreal:staff_apprentice");

    private WizardpediaPublisher() {}

    /**
     * Push the catalog snapshot for one player (server thread). Called from
     * {@link SpellCatalogService#publish} — the payload already carries this
     * player's learned flags.
     */
    public static void push(ServerPlayer player, CatalogPayload payload) {
        if (!Platform.isModLoaded("wizardpedia")) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        WizardRealConfig config = WizardRealConfig.load(server.getServerDirectory().toPath());
        if (config.pushMode() == WizardRealConfig.PushMode.OFF) return;

        NetworkManager.sendToPlayer(player, CHANNEL, encode(payload, config.pushMode()));
        WizardReal.LOGGER.debug("wizardpedia catalog push sent to {}", player.getName().getString());
    }

    /** Encode a PROVIDER_PUSH packet per the wizardpedia wire contract v2. */
    static FriendlyByteBuf encode(CatalogPayload payload, WizardRealConfig.PushMode pushMode) {
        List<CatalogPayload.CatalogOrigin> origins = filterOrigins(payload, pushMode);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(FORMAT_VERSION);
        buf.writeByte(TYPE_PROVIDER_PUSH);

        buf.writeVarInt(origins.size());
        for (int i = 0; i < origins.size(); i++) {
            CatalogPayload.CatalogOrigin origin = origins.get(i);
            buf.writeUtf(origin.id(), 128);
            buf.writeUtf(origin.nameKey(), 128);
            buf.writeUtf(ORIGIN_ICONS.getOrDefault(origin.id(), ""), 128);
            buf.writeVarInt(i * 10); // sortIndex: origin order, tens for future interleaving
        }

        int entryCount = origins.stream().mapToInt(o -> o.spells().size()).sum();
        buf.writeVarInt(entryCount);
        for (CatalogPayload.CatalogOrigin origin : origins) {
            for (CatalogPayload.CatalogSpell spell : origin.spells()) {
                buf.writeUtf(spell.id(), 128);
                buf.writeUtf(origin.id(), 128);
                buf.writeUtf(spell.nameKey(), 128);
                buf.writeBoolean(spell.learned() || !spell.requiresLearning()); // locked = not unlocked yet
                buf.writeUtf("wizardreal:spell_tome", 128);
                // Trigger words per language bucket; IPA rides the neutral bucket.
                // Mutable copies: buckets come from the payload as immutable lists.
                Map<String, List<String>> aliases = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> e : spell.triggerAliases().entrySet()) {
                    aliases.put(e.getKey(), new ArrayList<>(e.getValue()));
                }
                aliases.computeIfAbsent("", k -> new ArrayList<>()).addAll(spell.ipa());
                writeLangBuckets(buf, aliases, 96);
                // Chant lines per language (legacy lines are lang keys, language-keyed
                // lines literal text — the client's translatable fallback shows both).
                Map<String, List<String>> linesByLang = new LinkedHashMap<>();
                for (Map.Entry<String, List<List<String>>> e : spell.chantVariants().entrySet()) {
                    List<String> flat = new ArrayList<>();
                    for (List<String> variant : e.getValue()) flat.addAll(variant);
                    linesByLang.put(e.getKey(), flat);
                }
                writeLangBuckets(buf, linesByLang, 160);
            }
        }
        return buf;
    }

    /** varInt bucket count, then per language: utf lang code + varInt values. */
    private static void writeLangBuckets(FriendlyByteBuf buf, Map<String, List<String>> buckets, int maxLen) {
        buf.writeVarInt(buckets.size());
        for (Map.Entry<String, List<String>> e : buckets.entrySet()) {
            buf.writeUtf(e.getKey(), 8);
            buf.writeVarInt(e.getValue().size());
            for (String value : e.getValue()) buf.writeUtf(value, maxLen);
        }
    }

    /** castable mode keeps only spells the player can actually cast. */
    private static List<CatalogPayload.CatalogOrigin> filterOrigins(
            CatalogPayload payload, WizardRealConfig.PushMode pushMode) {
        if (pushMode != WizardRealConfig.PushMode.CASTABLE) return payload.origins();
        List<CatalogPayload.CatalogOrigin> out = new ArrayList<>();
        for (CatalogPayload.CatalogOrigin origin : payload.origins()) {
            List<CatalogPayload.CatalogSpell> castable = origin.spells().stream()
                    .filter(spell -> spell.learned() || !spell.requiresLearning()).toList();
            if (!castable.isEmpty()) out.add(new CatalogPayload.CatalogOrigin(origin.id(), origin.nameKey(), castable));
        }
        return List.copyOf(out);
    }
}
