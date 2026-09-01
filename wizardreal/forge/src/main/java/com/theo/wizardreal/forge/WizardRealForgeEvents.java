package com.theo.wizardreal.forge;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.server.SpellDataLoader;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * GAME-bus (FORGE) event handlers.
 *
 * <p>{@link AddReloadListenerEvent} is NOT an {@code IModBusEvent} — it fires
 * on the game bus; registering it on the MOD bus (either via method reference
 * or {@code @SubscribeEvent}) fails at CONSTRUCT with "not a subtype of the
 * base type interface IModBusEvent".
 */
@Mod.EventBusSubscriber(modid = WizardReal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
final class WizardRealForgeEvents {

    private WizardRealForgeEvents() {}

    /** Datapack spell loading: scan + parse + rebuild the registry on /reload. */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<List<SpellDataLoader.Loaded>>() {
            @Override
            protected List<SpellDataLoader.Loaded> prepare(ResourceManager manager,
                                                           ProfilerFiller profiler) {
                return SpellDataLoader.collect(manager);
            }

            @Override
            protected void apply(List<SpellDataLoader.Loaded> data, ResourceManager manager,
                                 ProfilerFiller profiler) {
                SpellDataLoader.apply(data);
            }
        });
    }
}
