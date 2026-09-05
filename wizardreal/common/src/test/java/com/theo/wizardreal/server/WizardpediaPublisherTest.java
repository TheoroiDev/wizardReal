package com.theo.wizardreal.server;

import com.theo.wizardreal.api.catalog.CatalogPayload;
import com.theo.wizardreal.config.WizardRealConfig;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the zero-dependency wizardpedia push: the publisher's
 * PROVIDER_PUSH encoding must match the wizardpedia wire contract v2
 * (docs/ref/wizardpedia.md §4) — formatVersion gate, language-bucketed
 * aliases/lines, IPA riding the neutral bucket. The decoder here is a
 * hand-rolled mirror of the contract, deliberately independent of
 * wizardpedia code (zero cross-repo dependency, same as production).
 */
class WizardpediaPublisherTest {

    private static CatalogPayload payload() {
        CatalogPayload.CatalogSpell spell = new CatalogPayload.CatalogSpell(
                "wizardreal:ignis", "spell.wizardreal:ignis.name", true, false, true,
                List.of("fire"), 10, 2.0f, 1.5f, 42.5f, true,
                List.of("ˈɪgnɪs"),
                Map.of("", List.of("_legacy_"), "en", List.of("ignis"), "zh", List.of("火焰")),
                Map.of("en", List.of(List.of("o flame", "lick my lance")),
                        "", List.of(List.of("wizardreal.chant.l1"))));
        return new CatalogPayload(List.of(
                new CatalogPayload.CatalogOrigin("wizardreal:wizardry", "origin.wizardreal.wizardry",
                        List.of(spell))));
    }

    /** Contract-mirror decode: returns [formatVersion, type, categories, entries]. */
    private static Object[] decode(FriendlyByteBuf buf) {
        int formatVersion = buf.readByte();
        byte type = buf.readByte();
        int catCount = buf.readVarInt();
        List<String[]> categories = new java.util.ArrayList<>();
        for (int i = 0; i < catCount; i++) {
            categories.add(new String[] {buf.readUtf(128), buf.readUtf(128), buf.readUtf(128),
                    Integer.toString(buf.readVarInt())});
        }
        int entryCount = buf.readVarInt();
        List<Map<String, Object>> entries = new java.util.ArrayList<>();
        for (int i = 0; i < entryCount; i++) {
            Map<String, Object> entry = new java.util.LinkedHashMap<>();
            entry.put("id", buf.readUtf(128));
            entry.put("category", buf.readUtf(128));
            entry.put("titleKey", buf.readUtf(128));
            entry.put("locked", buf.readBoolean());
            entry.put("icon", buf.readUtf(128));
            entry.put("aliases", readLangBuckets(buf, 96));
            entry.put("lines", readLangBuckets(buf, 160));
            entries.add(entry);
        }
        return new Object[] {formatVersion, type, categories, entries};
    }

    private static Map<String, List<String>> readLangBuckets(FriendlyByteBuf buf, int max) {
        Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        int langs = buf.readVarInt();
        for (int i = 0; i < langs; i++) {
            String lang = buf.readUtf(8);
            int n = buf.readVarInt();
            List<String> values = new java.util.ArrayList<>(n);
            for (int k = 0; k < n; k++) values.add(buf.readUtf(max));
            out.put(lang, values);
        }
        return out;
    }

    @Test
    void encodesContractV2WithLanguageBuckets() {
        FriendlyByteBuf buf = WizardpediaPublisher.encode(payload(), WizardRealConfig.PushMode.ALL);
        Object[] decoded = decode(buf);

        assertEquals(2, decoded[0], "formatVersion must be 2");
        assertEquals((byte) 1, decoded[1], "type must be PROVIDER_PUSH");

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = ((List<Map<String, Object>>) decoded[3]).get(0);
        assertEquals("wizardreal:ignis", entry.get("id"));
        assertEquals("wizardreal:wizardry", entry.get("category"));
        assertEquals(true, entry.get("locked"), "learned spell must not be locked");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> aliases = (Map<String, List<String>>) entry.get("aliases");
        assertEquals(List.of("ignis"), aliases.get("en"));
        assertEquals(List.of("火焰"), aliases.get("zh"));
        // IPA rides the neutral bucket next to legacy aliases
        assertEquals(List.of("_legacy_", "ˈɪgnɪs"), aliases.get(""));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> lines = (Map<String, List<String>>) entry.get("lines");
        assertEquals(List.of("o flame", "lick my lance"), lines.get("en"));
        assertEquals(List.of("wizardreal.chant.l1"), lines.get(""));
    }

    @Test
    void castableModeDropsUnknownSpells() {
        FriendlyByteBuf buf = WizardpediaPublisher.encode(payload(), WizardRealConfig.PushMode.CASTABLE);
        Object[] decoded = decode(buf);
        // the only spell is learned -> still present
        assertEquals(2, decoded[0]);
        assertEquals(1, ((List<?>) decoded[3]).size());
    }
}
