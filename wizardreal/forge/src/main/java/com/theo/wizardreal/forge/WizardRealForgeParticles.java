package com.theo.wizardreal.forge;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.client.WizardSparkParticle;
import com.theo.wizardreal.particle.WizardRealParticles;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers particle factories on Forge (client dist only, mod event bus).
 */
@Mod.EventBusSubscriber(modid = WizardReal.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WizardRealForgeParticles {

    private WizardRealForgeParticles() {}

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet((ParticleType<SimpleParticleType>) WizardRealParticles.SPARK.get(),
                WizardSparkParticle.Factory::new);
        event.registerSpriteSet((ParticleType<SimpleParticleType>) WizardRealParticles.RUNE.get(),
                WizardSparkParticle.Factory::new);
    }
}
