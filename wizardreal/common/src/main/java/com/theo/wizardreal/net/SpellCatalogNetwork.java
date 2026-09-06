package com.theo.wizardreal.net;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.client.SpellCatalogState;
import dev.architectury.networking.NetworkManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C {@code wizardreal:spell_catalog}: full spell-catalog snapshot for the
 * receiving player (keys or literal text). The client caches it (future
 * HUD/tooling use) and exports {@code <game-dir>/wizardreal/spell_catalog.json}.
 *
 * <p>Layout (formatVersion 3 — catalog v3: language annotation + mastery
 * scalars + effect descriptions + chant-stage ladder):
 * <pre>
 * byte formatVersion = 3
 * varInt originCount { utf originId≤128, utf nameKey≤160,
 *     varInt spellCount { utf id≤128, utf nameKey≤160, bool learned, bool requiresLearning,
 *         bool ritual, varInt schoolCount{utf school≤32}, varInt manaCost,
 *         float cooldownSeconds, float difficulty, float learning, bool skipAllowed,
 *         varInt ipaCount{utf ipa≤96},
 *         varInt aliasLangCount { utf lang≤8, varInt n{utf alias≤96} },
 *         varInt chantLangCount { utf lang≤8,
 *             varInt variantCount { varInt lineCount{utf line≤160} } },
 *         varInt descCount { utf key≤160 },
 *         varInt stageCount { varInt afterLines, float mastery, varInt manaCost(-1=inherit),
 *             float cooldownSeconds(-1=inherit), varInt descCount { utf key≤160 } } } }
 * </pre>
 *
 * <p>{@code ""} is the language-neutral bucket (legacy flat data).
 */
public final class SpellCatalogNetwork {
    public static final ResourceLocation CHANNEL = WizardReal.id("spell_catalog");
    public static final byte FORMAT_VERSION = 3;

    private SpellCatalogNetwork() {}

    /** Client-side receiver registration (platform client init only). */
    public static void registerClientReceiver() {
        NetworkManager.registerReceiver(NetworkManager.s2c(), CHANNEL, SpellCatalogNetwork::handle);
    }

    public static void send(ServerPlayer player, CatalogPayload payload) {
        NetworkManager.sendToPlayer(player, CHANNEL, write(payload));
    }

    static FriendlyByteBuf write(CatalogPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(FORMAT_VERSION);
        buf.writeVarInt(payload.origins().size());
        for (CatalogPayload.CatalogOrigin origin : payload.origins()) {
            buf.writeUtf(origin.id(), 128);
            buf.writeUtf(origin.nameKey(), 160);
            buf.writeVarInt(origin.spells().size());
            for (CatalogPayload.CatalogSpell spell : origin.spells()) {
                buf.writeUtf(spell.id(), 128);
                buf.writeUtf(spell.nameKey(), 160);
                buf.writeBoolean(spell.learned());
                buf.writeBoolean(spell.requiresLearning());
                buf.writeBoolean(spell.ritual());
                buf.writeVarInt(spell.schools().size());
                for (String school : spell.schools()) buf.writeUtf(school, 32);
                buf.writeVarInt(spell.manaCost());
                buf.writeFloat(spell.cooldownSeconds());
                buf.writeFloat(spell.difficulty());
                buf.writeFloat(spell.learning());
                buf.writeBoolean(spell.skipAllowed());
                buf.writeVarInt(spell.ipa().size());
                for (String ipa : spell.ipa()) buf.writeUtf(ipa, 96);
                writeLangMap(buf, spell.triggerAliases(), (b, aliases) -> {
                    b.writeVarInt(aliases.size());
                    for (String alias : aliases) b.writeUtf(alias, 96);
                });
                writeLangMap(buf, spell.chantVariants(), (b, variants) -> {
                    b.writeVarInt(variants.size());
                    for (List<String> lines : variants) {
                        b.writeVarInt(lines.size());
                        for (String line : lines) b.writeUtf(line, 160);
                    }
                });
                buf.writeVarInt(spell.descKeys().size());
                for (String key : spell.descKeys()) buf.writeUtf(key, 160);
                buf.writeVarInt(spell.stages().size());
                for (CatalogPayload.CatalogStage stage : spell.stages()) {
                    buf.writeVarInt(stage.afterLines());
                    buf.writeFloat(stage.mastery());
                    buf.writeVarInt(stage.manaCost());
                    buf.writeFloat(stage.cooldownSeconds());
                    buf.writeVarInt(stage.descKeys().size());
                    for (String key : stage.descKeys()) buf.writeUtf(key, 160);
                }
            }
        }
        return buf;
    }

    private static <T> void writeLangMap(FriendlyByteBuf buf, Map<String, T> map, LangWriter<T> writer) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, T> e : map.entrySet()) {
            buf.writeUtf(e.getKey(), 8);
            writer.write(buf, e.getValue());
        }
    }

    private interface LangWriter<T> {
        void write(FriendlyByteBuf buf, T value);
    }

    static CatalogPayload read(FriendlyByteBuf buf) {
        byte version = buf.readByte();
        if (version != FORMAT_VERSION) return null;
        int originCount = buf.readVarInt();
        List<CatalogPayload.CatalogOrigin> origins = new ArrayList<>(originCount);
        for (int i = 0; i < originCount; i++) {
            String id = buf.readUtf(128);
            String nameKey = buf.readUtf(160);
            int spellCount = buf.readVarInt();
            List<CatalogPayload.CatalogSpell> spells = new ArrayList<>(spellCount);
            for (int s = 0; s < spellCount; s++) {
                String spellId = buf.readUtf(128);
                String spellNameKey = buf.readUtf(160);
                boolean learned = buf.readBoolean();
                boolean requiresLearning = buf.readBoolean();
                boolean ritual = buf.readBoolean();
                int schoolCount = buf.readVarInt();
                List<String> schools = new ArrayList<>(schoolCount);
                for (int k = 0; k < schoolCount; k++) schools.add(buf.readUtf(32));
                int manaCost = buf.readVarInt();
                float cooldownSeconds = buf.readFloat();
                float difficulty = buf.readFloat();
                float learning = buf.readFloat();
                boolean skipAllowed = buf.readBoolean();
                int ipaCount = buf.readVarInt();
                List<String> ipa = new ArrayList<>(ipaCount);
                for (int k = 0; k < ipaCount; k++) ipa.add(buf.readUtf(96));
                Map<String, List<String>> triggerAliases = readLangMap(buf, b -> {
                    int n = b.readVarInt();
                    List<String> out = new ArrayList<>(n);
                    for (int k = 0; k < n; k++) out.add(b.readUtf(96));
                    return List.copyOf(out);
                });
                Map<String, List<List<String>>> chants = readLangMap(buf, b -> {
                    int variantCount = b.readVarInt();
                    List<List<String>> variants = new ArrayList<>(variantCount);
                    for (int v = 0; v < variantCount; v++) {
                        int lineCount = b.readVarInt();
                        List<String> lines = new ArrayList<>(lineCount);
                        for (int l = 0; l < lineCount; l++) lines.add(b.readUtf(160));
                        variants.add(List.copyOf(lines));
                    }
                    return List.copyOf(variants);
                });
                int descCount = buf.readVarInt();
                List<String> descKeys = new ArrayList<>(descCount);
                for (int k = 0; k < descCount; k++) descKeys.add(buf.readUtf(160));
                int stageCount = buf.readVarInt();
                List<CatalogPayload.CatalogStage> stages = new ArrayList<>(stageCount);
                for (int st = 0; st < stageCount; st++) {
                    int afterLines = buf.readVarInt();
                    float mastery = buf.readFloat();
                    int stageMana = buf.readVarInt();
                    float stageCooldown = buf.readFloat();
                    int stageDescCount = buf.readVarInt();
                    List<String> stageDesc = new ArrayList<>(stageDescCount);
                    for (int k = 0; k < stageDescCount; k++) stageDesc.add(buf.readUtf(160));
                    stages.add(new CatalogPayload.CatalogStage(afterLines, mastery, stageMana,
                            stageCooldown, List.copyOf(stageDesc)));
                }
                spells.add(new CatalogPayload.CatalogSpell(spellId, spellNameKey, learned, requiresLearning,
                        ritual, List.copyOf(schools), manaCost, cooldownSeconds,
                        difficulty, learning, skipAllowed, List.copyOf(ipa),
                        triggerAliases, chants, List.copyOf(descKeys), List.copyOf(stages)));
            }
            origins.add(new CatalogPayload.CatalogOrigin(id, nameKey, List.copyOf(spells)));
        }
        return new CatalogPayload(List.copyOf(origins));
    }

    private static <T> Map<String, T> readLangMap(FriendlyByteBuf buf, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int langCount = buf.readVarInt();
        Map<String, T> out = new LinkedHashMap<>(langCount);
        for (int i = 0; i < langCount; i++) {
            out.put(buf.readUtf(8), reader.apply(buf));
        }
        return out;
    }

    private static void handle(FriendlyByteBuf buf, NetworkManager.PacketContext ctx) {
        CatalogPayload payload = read(buf);
        if (payload == null) {
            WizardReal.LOGGER.warn("Rejected wizardreal:spell_catalog packet: formatVersion mismatch");
            return;
        }
        ctx.queue(() -> SpellCatalogState.handle(payload));
    }
}
