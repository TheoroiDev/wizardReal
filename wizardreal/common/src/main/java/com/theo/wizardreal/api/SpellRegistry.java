package com.theo.wizardreal.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SpellRegistry {
    private static final Map<String, Spell> SPELLS = new LinkedHashMap<>();

    private SpellRegistry() {}

    public static void register(Spell spell) {
        if (spell == null || spell.id() == null) throw new IllegalArgumentException("spell");
        if (SPELLS.containsKey(spell.id())) {
            throw new IllegalStateException("Duplicate spell id: " + spell.id());
        }
        SPELLS.put(spell.id(), spell);
    }

    /**
     * Register a spell, replacing an existing entry with the same id (used by
     * datapack definitions overriding built-ins). Insertion order is kept.
     */
    public static void replace(Spell spell) {
        if (spell == null || spell.id() == null) throw new IllegalArgumentException("spell");
        SPELLS.put(spell.id(), spell);
    }

    public static Optional<Spell> get(String id) {
        return Optional.ofNullable(SPELLS.get(id));
    }

    public static Collection<Spell> all() {
        return Collections.unmodifiableCollection(SPELLS.values());
    }

    public static void clear() {
        SPELLS.clear();
    }
}
