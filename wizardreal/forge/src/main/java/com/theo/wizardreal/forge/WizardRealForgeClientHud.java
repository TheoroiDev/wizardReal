package com.theo.wizardreal.forge;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.client.ChantClient;
import com.theo.wizardreal.client.MagicHud;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers HUD overlays on Forge (client dist only).
 */
@Mod.EventBusSubscriber(modid = WizardReal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WizardRealForgeClientHud {

    private WizardRealForgeClientHud() {}

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("wizardreal_chant",
                (gui, graphics, partialTick, width, height) ->
                        ChantClient.render(graphics, partialTick));
        event.registerAboveAll("wizardreal_magic",
                (gui, graphics, partialTick, width, height) ->
                        MagicHud.render(graphics, Minecraft.getInstance()));
    }
}
