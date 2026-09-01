package com.theo.wizardreal.item;

import java.util.function.Supplier;
import net.minecraft.world.item.Item;

/**
 * Central item registry for WizardReal.
 *
 * <p>Fields are populated by platform-specific initialisation
 * ({@code Registry.register} on Fabric, {@code DeferredRegister} on Forge)
 * so that items are registered at the correct lifecycle on each loader.
 */
public final class WizardRealItems {

    private WizardRealItems() {}

    // ----- Staves -----------------------------------------------------
    public static Supplier<Item> STAFF_APPRENTICE;
    public static Supplier<Item> STAFF_FIRE;
    public static Supplier<Item> STAFF_LIGHTNING;
    public static Supplier<Item> STAFF_SDEVV;

    // ----- Consumables ------------------------------------------------
    public static Supplier<Item> SCROLL_BLANK;

    // ----- Spell Tomes ------------------------------------------------
    public static Supplier<Item> SPELL_TOME;
}
