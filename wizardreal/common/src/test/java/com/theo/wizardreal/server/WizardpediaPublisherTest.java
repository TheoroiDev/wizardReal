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
        CatalogPayload.CatalogStage stage = new CatalogPayload.CatalogStage(
                3, 25.0f, 20, 6.0f, List.of("wizardreal.effect.explosion"));
        CatalogPayload.CatalogSpell spell = new CatalogPayload.CatalogSpell(
                "wizardreal:ignis", "spell.wizardreal:ignis.name", true, false, true,
                List.of("fire"), 10, 2.0f, 1.5f, 42.5f, true,
                List.of("ˈɪgnɪs"),
                Map.of("", List.of("_legacy_"), "en", List.of("ignis"), "zh", List.of("火焰")),
                Map.of("en", List.of(List.of("o flame", "lick my lance")),
                        "", List.of(List.of("wizardreal.chant.l1"))),
                List.of("wizardreal.effect.projectile"),
                List.of(stage));
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
            entry.put("learning", buf.readFloat());
            entry.put("manaCost", buf.readVarInt());
            entry.put("cooldownSeconds", buf.readFloat());
            entry.put("difficulty", buf.readFloat());
            entry.put("icon", buf.readUtf(128));
            entry.put("entity", buf.readUtf(128));
            List<String> tags = new java.util.ArrayList<>();
            int tagCount = buf.readVarInt();
            for (int t = 0; t < tagCount; t++) tags.add(buf.readUtf(32));
            entry.put("tags", tags);
            entry.put("aliases", readLangBuckets(buf, 96));
            entry.put("desc", readLangBuckets(buf, 160));
            entry.put("chants", readVariantBuckets(buf));
            List<Map<String, Object>> stages = new java.util.ArrayList<>();
            int stageCount = buf.readVarInt();
            for (int s = 0; s < stageCount; s++) {
                Map<String, Object> stage = new java.util.LinkedHashMap<>();
                stage.put("afterLines", buf.readVarInt());
                stage.put("mastery", buf.readFloat());
                stage.put("manaCost", buf.readVarInt());
                stage.put("cooldownSeconds", buf.readFloat());
                stage.put("desc", readLangBuckets(buf, 160));
                stages.add(stage);
            }
            entry.put("stages", stages);
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

    private static Map<String, List<List<String>>> readVariantBuckets(FriendlyByteBuf buf) {
        Map<String, List<List<String>>> out = new java.util.LinkedHashMap<>();
        int langs = buf.readVarInt();
        for (int i = 0; i < langs; i++) {
            String lang = buf.readUtf(8);
            int variants = buf.readVarInt();
            List<List<String>> list = new java.util.ArrayList<>(variants);
            for (int v = 0; v < variants; v++) {
                int n = buf.readVarInt();
                List<String> lines = new java.util.ArrayList<>(n);
                for (int k = 0; k < n; k++) lines.add(buf.readUtf(160));
                list.add(lines);
            }
            out.put(lang, list);
        }
        return out;
    }

    @Test
    void encodesContractV3WithLanguageBuckets() {
        FriendlyByteBuf buf = WizardpediaPublisher.encode(payload(), WizardRealConfig.PushMode.ALL);
        Object[] decoded = decode(buf);

        assertEquals(3, decoded[0], "formatVersion must be 3");
        assertEquals((byte) 1, decoded[1], "type must be PROVIDER_PUSH");

        @SuppressWarnings("unchecked")
        Map<String, Object> entry = ((List<Map<String, Object>>) decoded[3]).get(0);
        assertEquals("wizardreal:ignis", entry.get("id"));
        assertEquals("wizardreal:wizardry", entry.get("category"));
        assertEquals(true, entry.get("locked"), "learned spell must not be locked");
        assertEquals("", entry.get("entity"), "spells carry no entity preview");
        assertEquals(List.of("fire"), entry.get("tags"), "schools ride the tag list");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> aliases = (Map<String, List<String>>) entry.get("aliases");
        assertEquals(List.of("ignis"), aliases.get("en"));
        assertEquals(List.of("火焰"), aliases.get("zh"));
        // IPA rides the neutral bucket next to legacy aliases
        assertEquals(List.of("_legacy_", "ˈɪgnɪs"), aliases.get(""));

        @SuppressWarnings("unchecked")
        Map<String, List<String>> desc = (Map<String, List<String>>) entry.get("desc");
        assertEquals(List.of("wizardreal.effect.projectile"), desc.get(""));

        @SuppressWarnings("unchecked")
        Map<String, List<List<String>>> chants = (Map<String, List<List<String>>>) entry.get("chants");
        assertEquals(List.of(List.of("o flame", "lick my lance")), chants.get("en"));
        assertEquals(List.of(List.of("wizardreal.chant.l1")), chants.get(""));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) entry.get("stages");
        assertEquals(1, stages.size());
        assertEquals(3, stages.get(0).get("afterLines"));
        assertEquals(25.0f, stages.get(0).get("mastery"));
        assertEquals(20, stages.get(0).get("manaCost"));
        assertEquals(6.0f, stages.get(0).get("cooldownSeconds"));
        assertEquals(Map.of("", List.of("wizardreal.effect.explosion")), stages.get(0).get("desc"));
    }

    @Test
    void castableModeDropsUnknownSpells() {
        FriendlyByteBuf buf = WizardpediaPublisher.encode(payload(), WizardRealConfig.PushMode.CASTABLE);
        Object[] decoded = decode(buf);
        // the only spell is learned -> still present
        assertEquals(3, decoded[0]);
        assertEquals(1, ((List<?>) decoded[3]).size());
    }
}
