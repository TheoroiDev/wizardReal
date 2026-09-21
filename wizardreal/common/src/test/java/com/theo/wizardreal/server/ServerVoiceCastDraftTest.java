package com.theo.wizardreal.server;

import com.theo.wizardreal.api.Pronunciation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** G2P draft fill at vocabulary-push time ([voice] g2pDrafts). */
class ServerVoiceCastDraftTest {

    private static Pronunciation pron(String id, List<String> ipa, Map<String, List<String>> langs) {
        return new Pronunciation(id, ipa, List.of(), langs);
    }

    @Test
    void curatedTemplatesPassThrough() {
        Pronunciation p = pron("wizardreal:x", List.of("ʃiːld"), Map.of("en", List.of("shield")));
        assertEquals(p, ServerVoiceCast.withDrafts(p, Set.of()));
    }

    @Test
    void emptyTemplateGetsZhDraft() {
        Pronunciation p = pron("wizardreal:y.chant.zh.0:0", List.of(), Map.of("zh", List.of("熔甲")));
        Pronunciation out = ServerVoiceCast.withDrafts(p, Set.of("zh"));
        assertEquals(List.of("ʐʊŋ tɕja"), out.ipa());
        assertEquals(p.id(), out.id());
        assertEquals(p.languages(), out.languages());
    }

    @Test
    void languageRestrictedDraft() {
        Pronunciation p = pron("wizardreal:y.chant.zh.0:0", List.of(), Map.of("zh", List.of("熔甲")));
        // zh disabled -> no draft from the zh bucket, entry stays template-less
        assertEquals(p, ServerVoiceCast.withDrafts(p, Set.of("en")));
    }

    @Test
    void unknownScriptStaysEmpty() {
        Pronunciation p = pron("wizardreal:z", List.of(), Map.of("en", List.of("xqzzvo")));
        assertEquals(p, ServerVoiceCast.withDrafts(p, Set.of()));
    }
}
