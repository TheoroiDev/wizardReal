package com.theo.wizardreal;

import com.theo.wizardreal.api.Pronunciation;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantPolicy;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.Spell;

import java.util.List;
import java.util.Set;

/** Minimal in-memory {@link Spell} for matcher / chant / validator tests. */
public record TestSpell(
        String id,
        Pronunciation pronunciation,
        float thresholdOverride,
        float difficulty,
        boolean requiresLearning,
        List<Chant> chants,
        ChantPolicy policy
) implements Spell {

    public TestSpell {
        pronunciation = pronunciation == null ? new Pronunciation(id, List.of(), List.of()) : pronunciation;
        chants = chants == null ? List.of() : chants;
    }

    /** Instant spell with default threshold/difficulty. */
    public TestSpell(String id, Pronunciation pronunciation, float thresholdOverride, List<Chant> chants) {
        this(id, pronunciation, thresholdOverride, 1.0f, false, chants, null);
    }

    /** Instant spell with learning tuning (validator tests). */
    public TestSpell(String id, Pronunciation pronunciation, float thresholdOverride,
                     float difficulty, boolean requiresLearning, List<Chant> chants) {
        this(id, pronunciation, thresholdOverride, difficulty, requiresLearning, chants, null);
    }

    /** Instant spell with default threshold. */
    public static TestSpell of(String id, Pronunciation pronunciation) {
        return new TestSpell(id, pronunciation, -1f, List.of());
    }

    @Override
    public String nameKey() {
        return "spell." + id + ".name";
    }

    @Override
    public Set<School> schools() {
        return Set.of(School.ARCANE);
    }

    @Override
    public int manaCost() {
        return 10;
    }

    @Override
    public int cooldownTicks() {
        return 20;
    }

    @Override
    public float threshold() {
        return thresholdOverride;
    }

    @Override
    public float difficulty() {
        return difficulty;
    }

    @Override
    public boolean requiresLearning() {
        return requiresLearning;
    }

    @Override
    public ChantPolicy chantPolicy() {
        return policy == null ? ChantPolicy.DEFAULT : policy;
    }

    @Override
    public String origin() {
        return "wizardreal:test";
    }

    @Override
    public void cast(CastContext context) {
        // no-op in tests
    }
}
