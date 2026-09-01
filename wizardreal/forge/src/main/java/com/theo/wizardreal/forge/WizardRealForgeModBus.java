package com.theo.wizardreal.forge;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.item.ScrollItem;
import com.theo.wizardreal.item.SpellTomeItem;
import com.theo.wizardreal.item.WizardRealItems;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Mod-bus event handlers via {@link SubscribeEvent} annotation registration.
 *
 * <p>Deliberately NOT registered through {@code IEventBus.addListener(this::method)}
 * method references: under Loom 1.17's Forge remap chain the generic signature
 * extraction on synthesized method references fails, and EventBus throws
 * "takes an argument that is not a subtype of the base type" at mod
 * construction (runServer/runClient both died at CONSTRUCT). Annotation
 * registration reads the plain method parameter types and is immune.
 */
@Mod.EventBusSubscriber(modid = WizardReal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
final class WizardRealForgeModBus {

    private WizardRealForgeModBus() {}

    @SubscribeEvent
    public static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != WizardRealForge.MAIN_TAB_KEY) {
            return;
        }

        put(event, new ItemStack(WizardRealItems.STAFF_APPRENTICE.get()));
        put(event, new ItemStack(WizardRealItems.STAFF_FIRE.get()));
        put(event, new ItemStack(WizardRealItems.STAFF_LIGHTNING.get()));
        put(event, new ItemStack(WizardRealItems.STAFF_SDEVV.get()));
        put(event, new ItemStack(WizardRealItems.SCROLL_BLANK.get()));
        put(event, new ItemStack(WizardRealItems.SPELL_TOME.get()));

        // Add one spell tome and one scroll for every registered spell
        for (Spell spell : SpellRegistry.all()) {
            ItemStack tome = new ItemStack(WizardRealItems.SPELL_TOME.get());
            SpellTomeItem.setSpellId(tome, spell.id());
            put(event, tome);

            ItemStack scroll = new ItemStack(WizardRealItems.SCROLL_BLANK.get());
            ScrollItem.setSpellId(scroll, spell.id());
            put(event, scroll);
        }
    }

    private static void put(BuildCreativeModeTabContentsEvent event, ItemStack stack) {
        event.getEntries().put(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }
}
