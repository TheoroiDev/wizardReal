package com.theo.wizardreal.api.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Tiny loader-agnostic event bus for spell-related server events.
 * Mirrors the design of VoiceCastEvents (no reflection/annotations).
 */
public final class SpellEvents {
    private static final List<Consumer<SpellCastEvent>> CAST_LISTENERS = new CopyOnWriteArrayList<>();

    private SpellEvents() {}

    /** Fired server-side just before a spell is executed; listeners may cancel it. */
    public static void onCast(Consumer<SpellCastEvent> listener) {
        CAST_LISTENERS.add(listener);
    }

    public static void postCast(SpellCastEvent event) {
        for (Consumer<SpellCastEvent> c : CAST_LISTENERS) {
            try {
                c.accept(event);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
