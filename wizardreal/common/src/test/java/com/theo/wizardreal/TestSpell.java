package com.theo.wizardreal;

import com.theo.voicecast.api.Pronunciation;
import com.theo.wizardreal.api.CastContext;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.Spell;

import java.util.List;
import java.util.Set;

/** Minimal in-memory {@link Spell} for matcher / chant tests. */
public record TestSpell(
        String id,
        Pronunciation pronunciation,
        float thresholdOverride,
        List<Chant> chants
) implements Spell {

    public TestSpell {
        pronunciation = pronunciation == null ? new Pronunciation(id, List.of(), List.of()) : pronunciation;
        chants = chants == null ? List.of() : chants;
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
    public String origin() {
        return "wizardreal:test";
    }

    @Override
    public void cast(CastContext context) {
        // no-op in tests
    }
}
