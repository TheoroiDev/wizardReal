package com.theo.wizardreal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Simple upward-drifting billboard particle used by spell effects. The sprite
 * comes from the particle type's JSON description ({@code wizardreal:spark} /
 * {@code wizardreal:rune}); one factory class serves both.
 */
public class WizardSparkParticle extends TextureSheetParticle {

    WizardSparkParticle(ClientLevel world, double x, double y, double z,
                        double velocityX, double velocityY, double velocityZ, float scale) {
        super(world, x, y, z, velocityX, velocityY, velocityZ);
        this.gravity = 0.0f;
        this.friction = 0.96f;
        this.lifetime = 16 + this.random.nextInt(12);
        this.quadSize *= scale;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteProvider;
        private final float scale;

        public Factory(SpriteSet spriteProvider) {
            this(spriteProvider, 1.0f);
        }

        public Factory(SpriteSet spriteProvider, float scale) {
            this.spriteProvider = spriteProvider;
            this.scale = scale;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world,
                                       double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ) {
            WizardSparkParticle particle = new WizardSparkParticle(
                    world, x, y, z, velocityX, velocityY, velocityZ, scale);
            particle.pickSprite(this.spriteProvider);
            return particle;
        }
    }
}
