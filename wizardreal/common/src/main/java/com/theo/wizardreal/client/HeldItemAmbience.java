package com.theo.wizardreal.client;

import com.theo.wizardreal.item.StaffItem;
import com.theo.wizardreal.particle.WizardRealParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side cosmetics: a faint magical wisp drifts off a held staff,
 * hinting at its enchantment. Purely visual — spawned on the client only,
 * ~5 particles/second per held staff, suppressed while channeling (the
 * server-side cast effects in {@link com.theo.wizardreal.item.StaffItem}
 * take over then).
 */
public final class HeldItemAmbience {

    private HeldItemAmbience() {}

    public static void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        if (player.isUsingItem() || player.isShiftKeyDown()) return;
        if (player.tickCount % 12 != 0) return;

        SimpleParticleType wisp = (SimpleParticleType) WizardRealParticles.WISP.get();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof StaffItem) {
                spawn(mc, player, hand, wisp);
            }
        }
    }

    private static void spawn(Minecraft mc, Player player, InteractionHand hand,
                              SimpleParticleType wisp) {
        float yaw = player.getYRot() * ((float) Math.PI / 180F);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);   // facing direction
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);  // main-hand side
        double side = hand == InteractionHand.MAIN_HAND ? 0.38 : -0.38;
        Vec3 eye = player.getEyePosition();
        double x = eye.x + rx * side + fx * 0.25 + (mc.level.random.nextDouble() - 0.5) * 0.12;
        double y = eye.y - 0.35 + (mc.level.random.nextDouble() - 0.5) * 0.10;
        double z = eye.z + rz * side + fz * 0.25 + (mc.level.random.nextDouble() - 0.5) * 0.12;
        mc.level.addParticle(wisp, x, y, z, 0.0, 0.02 + mc.level.random.nextDouble() * 0.02, 0.0);
    }
}
