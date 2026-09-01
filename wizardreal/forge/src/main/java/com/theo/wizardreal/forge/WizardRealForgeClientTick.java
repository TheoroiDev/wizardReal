package com.theo.wizardreal.forge;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.client.HeldItemAmbience;
import com.theo.wizardreal.client.StaffCastHandler;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client tick on the FORGE bus (game bus). {@link TickEvent.ClientTickEvent}
 * is a game-bus event; keep it OFF the MOD-bus client setup class.
 */
@Mod.EventBusSubscriber(modid = WizardReal.MOD_ID, value = Dist.CLIENT)
public final class WizardRealForgeClientTick {

    private WizardRealForgeClientTick() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            StaffCastHandler.tick();
            HeldItemAmbience.tick(Minecraft.getInstance());
        }
    }
}
