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
import java.util.List;

/**
 * S2C {@code wizardreal:spell_catalog}: full spell-catalog snapshot for the
 * receiving player (keys only). The client caches it (future HUD/tooling
 * use) and exports {@code <game-dir>/wizardreal/spell_catalog.json}.
 *
 * <p>Layout (formatVersion 1):
 * <pre>
 * byte formatVersion = 1
 * varInt originCount { utf originId≤128, utf nameKey≤160,
 *     varInt spellCount { utf id≤128, utf nameKey≤160, bool learned, bool requiresLearning,
 *         bool ritual, varInt schoolCount{utf school≤32}, varInt manaCost,
 *         float cooldownSeconds, varInt aliasCount{utf alias≤96},
 *         varInt ipaCount{utf ipa≤96}, varInt chantCount{varInt lineCount{utf key≤160}} } }
 * </pre>
 */
public final class SpellCatalogNetwork {
    public static final ResourceLocation CHANNEL = WizardReal.id("spell_catalog");
    public static final byte FORMAT_VERSION = 1;

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
                buf.writeVarInt(spell.aliases().size());
                for (String alias : spell.aliases()) buf.writeUtf(alias, 96);
                buf.writeVarInt(spell.ipa().size());
                for (String ipa : spell.ipa()) buf.writeUtf(ipa, 96);
                buf.writeVarInt(spell.chantDisplayKeys().size());
                for (List<String> lines : spell.chantDisplayKeys()) {
                    buf.writeVarInt(lines.size());
                    for (String key : lines) buf.writeUtf(key, 160);
                }
            }
        }
        return buf;
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
                int aliasCount = buf.readVarInt();
                List<String> aliases = new ArrayList<>(aliasCount);
                for (int k = 0; k < aliasCount; k++) aliases.add(buf.readUtf(96));
                int ipaCount = buf.readVarInt();
                List<String> ipa = new ArrayList<>(ipaCount);
                for (int k = 0; k < ipaCount; k++) ipa.add(buf.readUtf(96));
                int chantCount = buf.readVarInt();
                List<List<String>> chants = new ArrayList<>(chantCount);
                for (int c = 0; c < chantCount; c++) {
                    int lineCount = buf.readVarInt();
                    List<String> lines = new ArrayList<>(lineCount);
                    for (int l = 0; l < lineCount; l++) lines.add(buf.readUtf(160));
                    chants.add(List.copyOf(lines));
                }
                spells.add(new CatalogPayload.CatalogSpell(spellId, spellNameKey, learned, requiresLearning,
                        ritual, List.copyOf(schools), manaCost, cooldownSeconds,
                        List.copyOf(aliases), List.copyOf(ipa), List.copyOf(chants)));
            }
            origins.add(new CatalogPayload.CatalogOrigin(id, nameKey, List.copyOf(spells)));
        }
        return new CatalogPayload(List.copyOf(origins));
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
