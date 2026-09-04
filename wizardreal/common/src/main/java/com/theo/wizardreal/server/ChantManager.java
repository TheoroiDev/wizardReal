package com.theo.wizardreal.server;

import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.ChantPolicy;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.config.WizardRealConfig;
import com.theo.wizardreal.net.ChantNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side ritual chanting state. D9 entry rework: the idle-state L1 gate
 * (see {@link ChantGate}) starts the chant pre-locked — the entry utterance
 * counts as completed line 1. While chanting each recognized utterance is
 * matched against the current incantation line; correct lines advance, a wrong
 * line retries the SAME line (three consecutive post-grace wrong lines fail the
 * chant), and speaking the spell-name line mid-chant releases early at the
 * completed-lines power tier ({@code chant_policy.skip_allowed}).
 *
 * <p>Matching rules, grace windows and the injected timeout live in the pure
 * {@link ChantEngine} state machine (unit-testable); this class only wires the
 * engine's results to network packets, lockouts, the darkness penalty for
 * failed chants (短暂连续失败叠加时长), damage interrupts (07 M1) and the
 * validated cast path. State is kept per player and only touched on the server
 * main thread (callers marshal via {@code server.execute}).
 */
public final class ChantManager {
    // After a chant COMPLETES, ignore every further utterance from that player
    // for a moment: the final word of a chant usually also matches the ritual
    // trigger, and recognition emits multiple finals per utterance (vosk final
    // + IPA final / endpoint flush) — the first completes the chant, the rest
    // would instantly re-trigger it.
    private static final long COMPLETION_LOCKOUT_MS = 3000L;
    // Shorter lock after an explicit cancel (left-click / timeout) so the
    // player can restart a ritual immediately while still swallowing the
    // audio tail of the cancelled line.
    private static final long CANCEL_LOCKOUT_MS = 1200L;

    private static final ChantManager INSTANCE = new ChantManager();
    public static ChantManager get() { return INSTANCE; }

    private ChantManager() {}

    private final Map<UUID, ChantEngine> active = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lockoutUntil = new ConcurrentHashMap<>();
    /** Consecutive chant failures per player: {count, lastFailMs} (darkness stacking). */
    private final Map<UUID, long[]> failStreaks = new ConcurrentHashMap<>();
    private volatile WizardRealConfig.ChantSettings settings;

    public boolean isChanting(ServerPlayer player) {
        return active.containsKey(player.getUUID());
    }

    /**
     * Whether the player may start a new chant / cast at all: false while
     * chanting and for a short window after a chant ended (re-trigger guard,
     * see {@link #COMPLETION_LOCKOUT_MS}).
     */
    public boolean isLocked(ServerPlayer player) {
        if (active.containsKey(player.getUUID())) return true;
        Long until = lockoutUntil.get(player.getUUID());
        return until != null && System.currentTimeMillis() < until;
    }

    private void lock(ServerPlayer player, long durationMs) {
        lockoutUntil.put(player.getUUID(), System.currentTimeMillis() + durationMs);
    }

    private WizardRealConfig.ChantSettings settings(ServerPlayer player) {
        WizardRealConfig.ChantSettings s = settings;
        if (s == null) {
            MinecraftServer server = player.getServer();
            s = WizardRealConfig.load(server != null ? server.getServerDirectory().toPath()
                    : WizardRealConfig.DEFAULT_GAME_DIR).chant();
            settings = s;
        }
        return s;
    }

    /** Timeout for a spell's chants: per-line mode = 10s x lines (configurable), fixed mode = fixed value. */
    private long timeoutMs(ServerPlayer player, Spell spell) {
        int maxLines = 1;
        for (Chant c : spell.chants()) maxLines = Math.max(maxLines, c.lines().size());
        return settings(player).timeoutFor(maxLines);
    }

    /** Enter chanting state for a ritual spell, pre-locked by the idle L1 gate
     * (D9 首行即门): the entry utterance already counted as completed line 1. */
    public void startAtLine(ServerPlayer player, Spell spell, int variant) {
        active.put(player.getUUID(), new ChantEngine(spell, System.currentTimeMillis(),
                timeoutMs(player, spell), variant));

        List<List<String>> variantLines = new ArrayList<>();
        for (Chant c : spell.chants()) {
            List<String> keys = new ArrayList<>();
            for (ChantLine line : c.lines()) keys.add(line.displayText());
            variantLines.add(keys);
        }
        ChantNetwork.sendStart(player, spell.id(), variantLines);
        ChantNetwork.sendProgress(player, variant, 1, false);
        WizardReal.LOGGER.info("{} began chanting {} (variant {} locked by L1 gate)",
                player.getName().getString(), spell.id(), variant);
    }

    /** Legacy entry (trigger word starts the chant unlocked). Kept for the
     * legacy-format window and robustness; the L1 gate is the primary path. */
    public void start(ServerPlayer player, Spell spell) {
        active.put(player.getUUID(), new ChantEngine(spell, System.currentTimeMillis(),
                timeoutMs(player, spell), -1));
        List<List<String>> variantLines = new ArrayList<>();
        for (Chant c : spell.chants()) {
            List<String> keys = new ArrayList<>();
            for (ChantLine line : c.lines()) keys.add(line.displayText());
            variantLines.add(keys);
        }
        ChantNetwork.sendStart(player, spell.id(), variantLines);
        WizardReal.LOGGER.info("{} began chanting {}", player.getName().getString(), spell.id());
    }

    /**
     * Feed one recognized utterance to an in-progress chant.
     * @return true if the utterance was consumed by the chant (caller should not
     *         try instant casting).
     */
    public boolean feed(ServerPlayer player, String heard, List<String> heardIpa, float confidence) {
        ChantEngine engine = active.get(player.getUUID());
        if (engine == null) return false;

        ChantEngine.FeedResult r = engine.feed(heard, heardIpa, System.currentTimeMillis());
        if (!r.consumed()) return false;

        if (r.timeout()) {
            fail(player, engine);
            return true;
        }
        for (ChantEngine.Progress p : r.progress()) {
            ChantNetwork.sendProgress(player, p.variant(), p.lineIndex(), p.error());
        }
        if (r.failed()) {
            // Three consecutive post-grace wrong lines: 念砸了 -> failure (darkness).
            fail(player, engine);
            return true;
        }
        if (r.earlyRelease()) {
            earlyRelease(player, engine, r.completedLines());
            return true;
        }
        if (r.finished()) {
            complete(player, engine);
        }
        return true;
    }

    private void complete(ServerPlayer player, ChantEngine engine) {
        Spell spell = engine.spell();
        int completedLines = engine.currentLineCount();
        active.remove(player.getUUID());
        lock(player, COMPLETION_LOCKOUT_MS);
        ChantNetwork.sendEnd(player, true);
        // Power tier for the full chant (chant_policy.power_per_line, default 1.0).
        SpellCastHandler.handleCast(player, spell.id(), powerFor(spell, completedLines));
        WizardReal.LOGGER.info("{} completed chant for {} ({} lines)",
                player.getName().getString(), spell.id(), completedLines);
    }

    /** D9 咒名跳章: the spell-name line was spoken mid-chant; cast at the
     * completed-lines tier (skip_allowed governs, enforced here). */
    private void earlyRelease(ServerPlayer player, ChantEngine engine, int completedLines) {
        Spell spell = engine.spell();
        ChantPolicy policy = spell.chantPolicy();
        active.remove(player.getUUID());
        if (policy != null && !policy.skipAllowed()) {
            // 禁咒: the jump is forbidden — treat as a failed chant (no cast).
            WizardReal.LOGGER.info("{} tried to jump chapters on forbidden chant {}", 
                    player.getName().getString(), spell.id());
            fail(player, engine);
            return;
        }
        lock(player, COMPLETION_LOCKOUT_MS);
        ChantNetwork.sendEnd(player, true);
        SpellCastHandler.handleCast(player, spell.id(), powerFor(spell, completedLines));
        WizardReal.LOGGER.info("{} released {} early ({} lines complete)", 
                player.getName().getString(), spell.id(), completedLines);
    }

    private float powerFor(Spell spell, int completedLines) {
        ChantPolicy policy = spell.chantPolicy();
        return policy == null ? 1.0f : policy.powerFor(completedLines);
    }

    /**
     * M1 咏唱打断: damage taken mid-chant. Chants of 3+ lines roll back one
     * line; shorter chants fail outright (darkness). Uninterruptible chants
     * (chant_policy.interruptible=false) ignore damage.
     * @return true if the damage consumed a chant state change.
     */
    public boolean onPlayerDamaged(ServerPlayer player, float amount) {
        ChantEngine engine = active.get(player.getUUID());
        if (engine == null) return false;
        ChantPolicy policy = engine.spell().chantPolicy();
        if (policy != null && !policy.interruptible()) return false;
        if (engine.currentLineCount() >= 3) {
            ChantEngine.Progress p = engine.rollbackLine(System.currentTimeMillis());
            ChantNetwork.sendProgress(player, p.variant(), p.lineIndex(), false);
            hint(player, "wizardreal.chant.interrupted");
            WizardReal.LOGGER.info("{}'s chant was interrupted (rolled back to line {})",
                    player.getName().getString(), p.lineIndex());
        } else {
            hint(player, "wizardreal.chant.interrupted");
            fail(player, engine);
        }
        return true;
    }

    /**
     * 破弃快施 (skip-cast) gate: the idle trigger/spell-name path. Requires
     * {@code chant_policy.skip_allowed} AND the learning threshold
     * ({@code t >= skipChantThreshold}). The learning system lands with SS4 —
     * until then the learning gate denies every skip (default learning 10% is
     * below the 50% threshold anyway).
     * @return true when the skip cast was performed.
     */
    public boolean trySkipCast(ServerPlayer player, Spell spell) {
        ChantPolicy policy = spell.chantPolicy();
        if (policy != null && !policy.skipAllowed()) {
            hint(player, "wizardreal.chant.skip_locked");
            return false;
        }
        // TODO(SS4 learning): skip requires learningPercent(spell) >= [learning].skipChantThreshold.
        hint(player, "wizardreal.chant.skip_locked");
        WizardReal.LOGGER.debug("Skip-cast for {} denied (learning gate pending SS4)", spell.id());
        return false;
    }

    /** Failed chant: darkness with stacking duration (短时间连续失败叠加). */
    private void fail(ServerPlayer player, ChantEngine engine) {
        active.remove(player.getUUID());
        lock(player, CANCEL_LOCKOUT_MS);
        ChantNetwork.sendEnd(player, false);
        applyFailBlindness(player);
        WizardReal.LOGGER.info("{}'s chant for {} failed", player.getName().getString(), engine.spell().id());
    }

    private void applyFailBlindness(ServerPlayer player) {
        WizardRealConfig.ChantSettings s = settings(player);
        if (!s.failBlindness()) return;
        long now = System.currentTimeMillis();
        long windowMs = s.failBlindnessWindowSeconds() * 1000L;
        long[] streak = failStreaks.get(player.getUUID());
        int count = (streak != null && now - streak[1] <= windowMs) ? (int) streak[0] + 1 : 1;
        failStreaks.put(player.getUUID(), new long[]{count, now});
        float seconds = s.failBlindnessBase() + s.failBlindnessStep() * (count - 1);
        int ticks = Math.max(1, (int) (seconds * 20));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0));
        hint(player, "wizardreal.chant.blind");
    }

    private void hint(ServerPlayer player, String key) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
    }

    public void cancel(ServerPlayer player, boolean success) {
        if (active.remove(player.getUUID()) != null) {
            lock(player, CANCEL_LOCKOUT_MS);
            ChantNetwork.sendEnd(player, success);
        }
    }

    public void onQuit(ServerPlayer player) {
        if (active.remove(player.getUUID()) != null) {
            ChantNetwork.sendEnd(player, false);
        }
        failStreaks.remove(player.getUUID());
    }

    /**
     * Cancel every active chant. Called when the spell registry is rebuilt
     * (datapack /reload) so sessions never reference stale {@link Spell}
     * objects.
     */
    public void clearAll(MinecraftServer server) {
        if (active.isEmpty()) return;
        for (UUID uuid : active.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) ChantNetwork.sendEnd(p, false);
        }
        active.clear();
        WizardReal.LOGGER.info("All active chants cancelled (spell registry reloaded)");
    }

    /** Periodic timeout sweep (called from the server tick). */
    public void tick(MinecraftServer server) {
        long now = System.currentTimeMillis();
        active.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            ChantEngine engine = e.getValue();
            if (p == null) return true; // player gone; quit handler covers packet
            if (now - engine.lastActivityMs() <= engine.timeoutMs()) return false;
            ChantNetwork.sendEnd(p, false);
            applyFailBlindness(p);
            lock(p, CANCEL_LOCKOUT_MS);
            return true;
        });
        // Expire stale lockouts and failure streaks so the maps cannot grow.
        lockoutUntil.values().removeIf(until -> now >= until);
        failStreaks.values().removeIf(s -> now - s[1] > 10L * 60L * 1000L);
    }
}
