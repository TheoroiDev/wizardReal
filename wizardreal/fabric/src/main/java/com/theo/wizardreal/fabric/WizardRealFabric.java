package com.theo.wizardreal.fabric;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.item.ScrollItem;
import com.theo.wizardreal.item.SpellTomeItem;
import com.theo.wizardreal.item.StaffItem;
import com.theo.wizardreal.item.WizardRealItems;
import com.theo.wizardreal.particle.WizardRealParticles;
import com.theo.wizardreal.particle.WizardSimpleParticle;
import com.theo.wizardreal.server.SpellDataLoader;
import com.theo.wizardreal.sound.WizardRealSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class WizardRealFabric implements ModInitializer {
    @Override
    public void onInitialize() {

        // Datapack spell loading: scan + parse + rebuild the registry on /reload.
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(
                new SimpleSynchronousResourceReloadListener() {
                    @Override
                    public ResourceLocation getFabricId() {
                        return WizardReal.id("spell_data");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        SpellDataLoader.apply(SpellDataLoader.collect(manager));
                    }
                });

        WizardRealItems.STAFF_APPRENTICE = register("staff_apprentice", StaffItem.apprentice(new Item.Properties()));
        WizardRealItems.STAFF_FIRE = register("staff_fire", StaffItem.fire(new Item.Properties()));
        WizardRealItems.STAFF_LIGHTNING = register("staff_lightning", StaffItem.lightning(new Item.Properties()));
        WizardRealItems.STAFF_SDEVV = register("staff_sdevv", StaffItem.dev(new Item.Properties()));
        WizardRealItems.SCROLL_BLANK = register("scroll_blank", new ScrollItem(new Item.Properties()));
        WizardRealItems.SPELL_TOME = register("spell_tome", new SpellTomeItem(new Item.Properties()));

        WizardRealParticles.SPARK = registerParticle("spark", new WizardSimpleParticle());
        WizardRealParticles.RUNE = registerParticle("rune", new WizardSimpleParticle());
        WizardRealParticles.WISP = registerParticle("wisp", new WizardSimpleParticle());

        for (String soundId : WizardRealSounds.IDS) {
            Registry.register(BuiltInRegistries.SOUND_EVENT, WizardRealSounds.id(soundId),
                    SoundEvent.createVariableRangeEvent(WizardRealSounds.id(soundId)));
        }

        // Create a dedicated WizardReal creative tab (icon = stick for now).
        // Positioning is handled by Fabric API's ItemGroupsMixin: every mod
        // tab gets a dynamically assigned page/row/column (mod tabs start on
        // page 2 of the creative screen; reach it via the < > arrows or the
        // search bar). Explicit row/column values are overwritten and must
        // NOT be set here.
        CreativeModeTab wizardrealTab = FabricItemGroup.builder()
                .icon(() -> new ItemStack(Items.STICK))
                .title(Component.translatable("itemGroup.wizardreal.main"))
                .displayItems((displayContext, entries) -> {
                    entries.accept(WizardRealItems.STAFF_APPRENTICE.get());
                    entries.accept(WizardRealItems.STAFF_FIRE.get());
                    entries.accept(WizardRealItems.STAFF_LIGHTNING.get());
                    entries.accept(WizardRealItems.STAFF_SDEVV.get());
                    entries.accept(WizardRealItems.SCROLL_BLANK.get());
                    entries.accept(WizardRealItems.SPELL_TOME.get());

                    // Add one spell tome and one scroll for every registered spell
                    for (Spell spell : SpellRegistry.all()) {
                        ItemStack tome = new ItemStack(WizardRealItems.SPELL_TOME.get());
                        SpellTomeItem.setSpellId(tome, spell.id());
                        entries.accept(tome);

                        ItemStack scroll = new ItemStack(WizardRealItems.SCROLL_BLANK.get());
                        ScrollItem.setSpellId(scroll, spell.id());
                        entries.accept(scroll);
                    }
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, new ResourceLocation(WizardReal.MOD_ID, "main"), wizardrealTab);

        WizardReal.init();
    }

    private static java.util.function.Supplier<Item> register(String id, Item item) {
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(WizardReal.MOD_ID, id), item);
        return () -> item;
    }

    private static java.util.function.Supplier<ParticleType<?>> registerParticle(String id, SimpleParticleType type) {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, new ResourceLocation(WizardReal.MOD_ID, id), type);
        return () -> type;
    }
}
