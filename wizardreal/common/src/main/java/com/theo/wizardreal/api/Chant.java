package com.theo.wizardreal.api;

import java.util.List;

/**
 * One long incantation ("chant") for a ritual spell: an ordered list of lines
 * that must be spoken sentence by sentence after the spell's trigger word. A
 * spell may declare several chants (variants); the first chanted line locks
 * which one is used.
 */
public record Chant(List<ChantLine> lines) {
    public Chant {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
