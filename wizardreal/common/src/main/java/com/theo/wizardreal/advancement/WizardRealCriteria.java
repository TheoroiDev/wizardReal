package com.theo.wizardreal.advancement;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.theo.wizardreal.WizardReal;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;

/**
 * Custom advancement triggers (wizardReal#49 新手断点修复): the advancement
 * tree is pure datapack EXCEPT the two hooks that have no vanilla predicate —
 * a successful voice cast (optionally filtered by minimum chant lines and/or
 * spell ids) and a spell's mastery crossing 100%.
 *
 * <p>Fired from the validated cast path ({@code ChantManager} completion /
 * early-release and the instant-cast branch) and the learning settlement —
 * never from raw recognitions, so a false positive cannot hand out progress.
 */
public final class WizardRealCriteria {

    private WizardRealCriteria() {}

    /** "初次低语" family: a successful voice cast. JSON props:
     *  {@code lines} (int, minimum completed chant lines; instant = 1) and
     *  {@code spells} (array of namespaced ids). */
    public static final VoiceCastTrigger VOICE_CAST = new VoiceCastTrigger();

    /** "千锤百炼": a spell's mastery t crossed 100%. JSON prop:
     *  {@code spells} (optional filter; default = any spell). */
    public static final MasteryTrigger MASTERY = new MasteryTrigger();

    // ------------------------------------------------------------ trigger

    public static class VoiceCastTrigger extends SimpleCriterionTrigger<Instance> {
        public static final ResourceLocation ID = new ResourceLocation(WizardReal.MOD_ID, "voice_cast");

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        protected Instance createInstance(JsonObject json, ContextAwarePredicate predicate,
                                          DeserializationContext ctx) {
            int minLines = json.has("lines") ? json.get("lines").getAsInt() : 1;
            Set<String> spells = readSpells(json);
            return new Instance(ID, predicate, minLines, spells);
        }

        /** Fire for one successful voice cast. */
        public void trigger(ServerPlayer player, String spellId, int completedLines) {
            this.trigger(player, inst -> inst.matches(spellId, completedLines));
        }
    }

    public static class MasteryTrigger extends SimpleCriterionTrigger<Instance> {
        public static final ResourceLocation ID = new ResourceLocation(WizardReal.MOD_ID, "mastery");

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        protected Instance createInstance(JsonObject json, ContextAwarePredicate predicate,
                                          DeserializationContext ctx) {
            return new Instance(ID, predicate, readSpells(json));
        }

        /** Fire when a spell's mastery t crosses 100%. */
        public void trigger(ServerPlayer player, String spellId) {
            this.trigger(player, inst -> inst.matches(spellId));
        }
    }

    private static Set<String> readSpells(JsonObject json) {
        Set<String> spells = new HashSet<>();
        if (json.has("spells")) {
            JsonArray arr = json.getAsJsonArray("spells");
            for (int i = 0; i < arr.size(); i++) spells.add(arr.get(i).getAsString());
        }
        return Set.copyOf(spells);
    }

    // ----------------------------------------------------------- instance

    static class Instance extends AbstractCriterionTriggerInstance {
        private final int minLines;
        private final Set<String> spells;

        Instance(ResourceLocation id, ContextAwarePredicate predicate, int minLines, Set<String> spells) {
            super(id, predicate);
            this.minLines = minLines;
            this.spells = spells;
        }

        Instance(ResourceLocation id, ContextAwarePredicate predicate, Set<String> spells) {
            this(id, predicate, 1, spells);
        }

        boolean matches(String spellId, int completedLines) {
            if (completedLines < minLines) return false;
            return spells.isEmpty() || spells.contains(spellId);
        }

        boolean matches(String spellId) {
            return matches(spellId, Integer.MAX_VALUE);
        }

        @Override
        public JsonObject serializeToJson(SerializationContext ctx) {
            JsonObject json = super.serializeToJson(ctx);
            if (minLines > 1) json.addProperty("lines", minLines);
            if (!spells.isEmpty()) {
                JsonArray arr = new JsonArray();
                spells.stream().sorted().forEach(arr::add);
                json.add("spells", arr);
            }
            return json;
        }
    }
}
