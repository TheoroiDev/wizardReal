package com.theo.wizardreal.api;

import com.theo.voicecast.api.Pronunciation;

import java.util.List;

/**
 * A single line of a long incantation: the text shown on the client HUD
 * (legacy {@code displayKey} lang key, or — for 0.4.0 language-keyed chants —
 * the line's primary alias, which follows the player's grammar bucket) and the
 * pronunciation templates used to match the spoken line server-side.
 */
public record ChantLine(String displayKey, Pronunciation pronunciation) {

    /** Text to display for this line: the legacy lang key when present, else
     * the primary alias of the line's pronunciation (language-keyed chants
     * carry no lang file entry — the displayed text is the spoken text). */
    public String displayText() {
        if (displayKey != null && !displayKey.isEmpty()) return displayKey;
        List<String> aliases = pronunciation == null ? List.of() : pronunciation.aliases();
        return aliases.isEmpty() ? "" : aliases.get(0);
    }
}
