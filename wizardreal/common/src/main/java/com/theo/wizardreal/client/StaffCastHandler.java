package com.theo.wizardreal.client;

import com.theo.voicecast.client.VoiceCastClient;
import com.theo.wizardreal.item.StaffItem;
import com.theo.wizardreal.net.ChantNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side bridge that drives VoiceCast PTT from the staff right-click.
 * When the player holds a staff in their main hand, VoiceCast is enabled and
 * the right mouse button becomes push-to-talk (the staff is in its bow-like
 * use state for as long as the button is held). Releasing the button finishes
 * the utterance and the server casts the matched spell.
 *
 * <p>While channeling (staff held + right button held), a left-click attack
 * cancels an active ritual chant: the client sends the C2S cancel packet and
 * the server tears the chant down (HUD clears via the S2C END packet).
 */
public final class StaffCastHandler {

    private static boolean wasHoldingStaff;
    private static boolean wasRightClickHeld;
    private static boolean wasLeftClickHeld;

    private StaffCastHandler() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            VoiceCastClient.INSTANCE.setEnabled(false);
            wasHoldingStaff = false;
            wasRightClickHeld = false;
            wasLeftClickHeld = false;
            return;
        }

        ItemStack mainHand = player.getMainHandItem();
        boolean holdingStaff = mainHand.getItem() instanceof StaffItem;

        if (holdingStaff != wasHoldingStaff) {
            VoiceCastClient.INSTANCE.setEnabled(holdingStaff);
            wasHoldingStaff = holdingStaff;
        }

        if (!holdingStaff) {
            wasRightClickHeld = false;
            wasLeftClickHeld = false;
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean rightClickHeld = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (rightClickHeld != wasRightClickHeld) {
            VoiceCastClient.INSTANCE.setPttHeld(rightClickHeld);
            wasRightClickHeld = rightClickHeld;
        }

        // Left-click cancels an active chant while channeling. Raw GLFW
        // polling (not the attack key): while the item is in its use state
        // vanilla suppresses attacks, and the cancel must fire regardless.
        boolean leftClickHeld = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (rightClickHeld && leftClickHeld && !wasLeftClickHeld && ChantClient.CHANT.active) {
            ChantNetwork.sendCancel();
            player.swing(InteractionHand.MAIN_HAND);
        }
        wasLeftClickHeld = leftClickHeld;
    }
}
