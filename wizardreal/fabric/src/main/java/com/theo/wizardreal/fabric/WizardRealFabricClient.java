package com.theo.wizardreal.fabric;

import com.theo.voicecast.client.VoiceCastClient;
import com.theo.wizardreal.client.ChantClient;
import com.theo.wizardreal.client.MagicHud;
import com.theo.wizardreal.client.StaffCastHandler;
import com.theo.wizardreal.client.WizardSparkParticle;
import com.theo.wizardreal.net.MagicSyncNetwork;
import com.theo.wizardreal.particle.WizardRealParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Client wiring for Be a Real Wizard on Fabric. Registers S2C receivers
 * and draws HUDs (all gameplay/recognition is server-side; this only renders).
 */
public final class WizardRealFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChantClient.init();
        MagicSyncNetwork.registerClientReceiver();

        ParticleFactoryRegistry.getInstance().register(
                (ParticleType<SimpleParticleType>) WizardRealParticles.SPARK.get(),
                WizardSparkParticle.Factory::new);
        ParticleFactoryRegistry.getInstance().register(
                (ParticleType<SimpleParticleType>) WizardRealParticles.RUNE.get(),
                WizardSparkParticle.Factory::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> StaffCastHandler.tick());

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            ChantClient.render(drawContext, tickDelta);
            MagicHud.render(drawContext, net.minecraft.client.Minecraft.getInstance());
        });
    }
}
