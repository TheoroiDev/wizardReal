package com.theo.wizardreal.forge;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.client.ChantClient;
import com.theo.wizardreal.net.MagicSyncNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Client initialization for Be a Real Wizard on Forge: registers S2C
 * receivers (overlay registration is on {@link WizardRealForgeClientHud},
 * the client tick is on {@link WizardRealForgeClientTick}).
 *
 * <p>{@link FMLClientSetupEvent} is a MOD-bus event — this class MUST stay on
 * {@code Bus.MOD}. With the default FORGE bus the handler silently never
 * fires, which left the chant HUD and the mana sync without their S2C
 * receivers on Forge (the Fabric side was unaffected).
 */
@Mod.EventBusSubscriber(modid = WizardReal.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class WizardRealForgeClient {

    private WizardRealForgeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ChantClient.init();
            MagicSyncNetwork.registerClientReceiver();
        });
    }
}
