package com.theo.wizardreal.forge;

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
import java.util.List;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

@Mod(WizardReal.MOD_ID)
public final class WizardRealForge {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WizardReal.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), WizardReal.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, WizardReal.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, WizardReal.MOD_ID);

    public static final ResourceKey<CreativeModeTab> MAIN_TAB_KEY =
            ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), new ResourceLocation(WizardReal.MOD_ID, "main"));

    @SuppressWarnings({"deprecation", "removal"})
    public WizardRealForge() {

        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        TABS.register(modEventBus);
        PARTICLE_TYPES.register(modEventBus);
        SOUND_EVENTS.register(modEventBus);

        TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.BOTTOM, 0)
                .title(Component.translatable("itemGroup.wizardreal.main"))
                .icon(() -> new ItemStack(Items.STICK))
                .build());

        WizardRealItems.STAFF_APPRENTICE = ITEMS.register("staff_apprentice",
                () -> StaffItem.apprentice(new Item.Properties()));
        WizardRealItems.STAFF_FIRE = ITEMS.register("staff_fire",
                () -> StaffItem.fire(new Item.Properties()));
        WizardRealItems.STAFF_LIGHTNING = ITEMS.register("staff_lightning",
                () -> StaffItem.lightning(new Item.Properties()));
        WizardRealItems.STAFF_SDEVV = ITEMS.register("staff_sdevv",
                () -> StaffItem.dev(new Item.Properties()));
        WizardRealItems.SCROLL_BLANK = ITEMS.register("scroll_blank",
                () -> new ScrollItem(new Item.Properties()));
        WizardRealItems.SPELL_TOME = ITEMS.register("spell_tome",
                () -> new SpellTomeItem(new Item.Properties()));

        WizardRealParticles.SPARK = PARTICLE_TYPES.register("spark",
                WizardSimpleParticle::new)::get;
        WizardRealParticles.RUNE = PARTICLE_TYPES.register("rune",
                WizardSimpleParticle::new)::get;

        for (String soundId : WizardRealSounds.IDS) {
            SOUND_EVENTS.register(soundId, () -> SoundEvent.createVariableRangeEvent(WizardRealSounds.id(soundId)));
        }

        WizardReal.init();
    }

}
