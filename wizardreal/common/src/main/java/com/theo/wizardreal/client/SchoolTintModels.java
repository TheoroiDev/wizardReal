package com.theo.wizardreal.client;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.School;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Exposes the school of the spell bound to a tome/scroll as an item model
 * property, so {@code models/item/spell_tome.json} and
 * {@code models/item/scroll_blank.json} can override to a school-tinted
 * texture via the {@code wizardreal:school} predicate.
 *
 * <p>Property value: 0 = blank/unknown/no school (base texture),
 * 1..10 = {@link School} ordinal + 1 in declaration order
 * (1=fire ... 10=illusion). Per-school textures are produced by
 * {@code tools/comfy/recolor.py}.
 *
 * <p>Registration itself lives in the loader client inits — Forge patches
 * {@code ItemProperties.register} to a loader-specific functional interface,
 * so the common source set only supplies the property function.
 */
public final class SchoolTintModels {

    public static final ResourceLocation SCHOOL_ID =
            new ResourceLocation(WizardReal.MOD_ID, "school");

    private SchoolTintModels() {}

    public static float schoolProperty(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return 0f;
        String spellId = tag.getString("SpellId");
        if (spellId == null || spellId.isBlank()) return 0f;
        Spell spell = SpellRegistry.get(spellId).orElse(null);
        if (spell != null) {
            Set<School> schools = spell.schools();
            if (schools.isEmpty()) return 0f;
            return schools.iterator().next().ordinal() + 1f;
        }
        // Dedicated server: the client registry is empty — fall back to the
        // synced spell catalog (populated on PLAYER_JOIN).
        return com.theo.wizardreal.net.SpellCatalogCache.schoolOrdinal(spellId);
    }
}
