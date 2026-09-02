package com.theo.wizardreal.net;

import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.catalog.CatalogPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Common-safe holder of the latest client-side {@link CatalogPayload} (the
 * {@code wizardreal:spell_catalog} snapshot pushed on PLAYER_JOIN).
 *
 * <p>Client code ({@code SpellCatalogState}) writes it; common code reads it.
 * The creative tab and item-model school tinting use it as the MP fallback:
 * when connected to a dedicated server the client-side {@code SpellRegistry}
 * is empty (spell data only loads on {@code SERVER_DATA} reloads, which never
 * fire on the physical client), so the synced catalog is the only source of
 * spell ids/names/schools there.
 */
public final class SpellCatalogCache {

    /** One creatable spell: id + lang key + primary schools (client view). */
    public record Entry(String id, String nameKey, List<School> schools) {}

    private static volatile CatalogPayload last;

    private SpellCatalogCache() {}

    public static CatalogPayload last() {
        return last;
    }

    public static void set(CatalogPayload payload) {
        last = payload;
    }

    /**
     * Spell list for creative-tab generation: the live registry when it is
     * populated (singleplayer — integrated server shares the JVM), else the
     * synced catalog snapshot. Empty on a dedicated server (tabs are built
     * from the client there anyway).
     */
    public static List<Entry> creativeEntries() {
        List<Entry> entries = new ArrayList<>();
        for (com.theo.wizardreal.api.Spell spell : com.theo.wizardreal.api.SpellRegistry.all()) {
            entries.add(new Entry(spell.id().toString(), spell.nameKey(),
                    List.copyOf(spell.schools())));
        }
        if (!entries.isEmpty()) return entries;
        CatalogPayload payload = last;
        if (payload == null) return List.of();
        for (CatalogPayload.CatalogOrigin origin : payload.origins()) {
            for (CatalogPayload.CatalogSpell spell : origin.spells()) {
                List<School> schools = new ArrayList<>();
                for (String name : spell.schools()) {
                    try {
                        schools.add(School.valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ignored) {
                        // datapack introduced a school this build doesn't know
                    }
                }
                entries.add(new Entry(spell.id(), spell.nameKey(), List.copyOf(schools)));
            }
        }
        return entries;
    }

    /** Lang key for a spell id from the synced catalog, or null. */
    public static String nameKey(String spellId) {
        CatalogPayload payload = last;
        if (payload == null || spellId == null || spellId.isBlank()) return null;
        for (CatalogPayload.CatalogOrigin origin : payload.origins()) {
            for (CatalogPayload.CatalogSpell spell : origin.spells()) {
                if (spell.id().equals(spellId)) return spell.nameKey();
            }
        }
        return null;
    }

    /** Primary school ordinal + 1 from the synced catalog, or 0 (blank/base). */
    public static float schoolOrdinal(String spellId) {
        CatalogPayload payload = last;
        if (payload == null || spellId == null || spellId.isBlank()) return 0f;
        for (CatalogPayload.CatalogOrigin origin : payload.origins()) {
            for (CatalogPayload.CatalogSpell spell : origin.spells()) {
                if (spell.id().equals(spellId)) {
                    for (String name : spell.schools()) {
                        try {
                            return School.valueOf(name.toUpperCase(Locale.ROOT)).ordinal() + 1f;
                        } catch (IllegalArgumentException ignored) {
                            // fall through to the next school
                        }
                    }
                }
            }
        }
        return 0f;
    }
}
