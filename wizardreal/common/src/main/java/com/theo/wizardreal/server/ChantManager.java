package com.theo.wizardreal.server;

import com.theo.voicecast.match.AdjudicatorBackedChantLineMatcher;
import com.theo.voicecast.match.ChantLineMatcher;
import com.theo.voicecast.match.LenientLineMatcher;
import com.theo.voicecast.server.CastMode;
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
    /** wr#43 评分带: a PERFECT chant resonates — this cast's power multiplier.
     *  Two-axis rule: resonance boosts the effect only, never mastery. */
    static final float RESONANCE_POWER_MULTIPLIER = 1.10f;
    /**
     * Production line-progression binding (voiceCast#48 W2, wired by #43):
     * the adjudicator's verdict decides progression and — critically for the
     * score bands — grades every matched line ({@code LineMatch.score});
     * out-of-vocabulary lines and offline paths fall back to the lenient
     * boolean. Before #43 this field's only consumer was the test suite and
     * chants silently ran the pre-#48 boolean matcher (every line 1.0/0.0 —
     * the band would have been vacuous and resonance always-on).
     */
    private static final ChantLineMatcher LINE_MATCHER = new AdjudicatorBackedChantLineMatcher();
    // Shorter lock after an explicit cancel (left-click / timeout) so the
    // player can restart a ritual immediately while still swallowing the
    // audio tail of the cancelled line.
    private static final long CANCEL_LOCKOUT_MS = 1200L;

    private static final ChantManager INSTANCE = new ChantManager();
    public static ChantManager get() { return INSTANCE; }

    private ChantManager() {}

    /**
     * Cast-mode declaration follows the chant lifecycle (issue #30 R-3/D-15):
     * while a chant is active the player's session hears only the current
     * spell ({@link CastMode#CHANT_CONFIRM}); the moment it ends the player
     * falls back to free casting ({@code OPEN}).
     */
    private static void declareMode(ServerPlayer player, CastMode mode, Spell spell) {
        ServerVoiceCast.setCastMode(player, mode, spell == null ? List.of() : List.of(spell.id()));
    }

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
            s = WizardRealConfig.loadCached(server != null ? server.getServerDirectory().toPath()
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
                timeoutMs(player, spell), variant, LINE_MATCHER));
        declareMode(player, CastMode.CHANT_CONFIRM, spell);
        ChantNetwork.sendStart(player, spell.id(), variantLineKeys(spell));
        ChantNetwork.sendProgress(player, variant, 1, false);
        WizardReal.LOGGER.info("{} began chanting {} (variant {} locked by L1 gate)",
                player.getName().getString(), spell.id(), variant);
    }

    /** wr#43 practice loop entry (`/wr practice <spell>`): an unlocked
     *  practice engine + PRACTICE_CONFIRM routing — zero mana/cooldown/effects,
     *  strict bands, per-word HUD feedback, half-rate daily-capped mastery. */
    public void startPractice(ServerPlayer player, Spell spell) {
        active.put(player.getUUID(), new ChantEngine(spell, System.currentTimeMillis(),
                timeoutMs(player, spell), -1, LINE_MATCHER, true));
        declareMode(player, CastMode.PRACTICE_CONFIRM, spell);
        ChantNetwork.sendStart(player, spell.id(), variantLineKeys(spell));
        hint(player, "wizardreal.practice.started");
        WizardReal.LOGGER.info("{} began practicing {}", player.getName().getString(), spell.id());
    }

    /** Whether the player's active chant is a practice rehearsal. */
    public boolean isPracticing(ServerPlayer player) {
        ChantEngine engine = active.get(player.getUUID());
        return engine != null && engine.isPractice();
    }

    /** Leave practice voluntarily (`/wr practice stop`) — no penalty. */
    public void stopPractice(ServerPlayer player) {
        ChantEngine engine = active.remove(player.getUUID());
        if (engine == null || !engine.isPractice()) return;
        declareMode(player, CastMode.OPEN, null);
        ChantNetwork.sendEnd(player, false);
        hint(player, "wizardreal.practice.stopped");
    }

    private List<List<String>> variantLineKeys(Spell spell) {
        List<List<String>> variantLines = new ArrayList<>();
        for (Chant c : spell.chants()) {
            List<String> keys = new ArrayList<>();
            for (ChantLine line : c.lines()) keys.add(line.displayText());
            variantLines.add(keys);
        }
        return variantLines;
    }

    /** Legacy entry (trigger word starts the chant unlocked). Kept for the
     * legacy-format window and robustness; the L1 gate is the primary path. */
    public void start(ServerPlayer player, Spell spell) {
        active.put(player.getUUID(), new ChantEngine(spell, System.currentTimeMillis(),
                timeoutMs(player, spell), -1, LINE_MATCHER));
        declareMode(player, CastMode.CHANT_CONFIRM, spell);
        ChantNetwork.sendStart(player, spell.id(), variantLineKeys(spell));
        WizardReal.LOGGER.info("{} began chanting {}", player.getName().getString(), spell.id());
    }

    /**
     * Feed one recognized utterance to an in-progress chant.
     * @return true if the utterance was consumed by the chant (caller should not
     *         try instant casting).
     */
    public boolean feed(ServerPlayer player, String heard, List<String> heardIpa, float confidence) {
        return feed(player, heard, heardIpa, confidence, null);
    }

    /** voiceCast#48 W2: the utterance's production adjudication rides along
     *  (pronId/decision/score) — the progression matcher weighs it. */
    public boolean feed(ServerPlayer player, String heard, List<String> heardIpa, float confidence,
                        com.theo.voicecast.match.ChantLineMatcher.ChantVerdict verdict) {
        ChantEngine engine = active.get(player.getUUID());
        if (engine == null) return false;

        ChantEngine.FeedResult r = engine.feed(heard, heardIpa, System.currentTimeMillis(), verdict);
        if (!r.consumed()) return false;

        if (r.timeout()) {
            fail(player, engine);
            return true;
        }
        for (ChantEngine.Progress p : r.progress()) {
            ChantNetwork.sendProgress(player, p.variant(), p.lineIndex(), p.error());
        }
        if (engine.isPractice() && !heard.isBlank()) {
            // wr#43 逐词对齐: the display text is a lang key only the client can
            // localize, so the server ships the raw utterance and the client
            // runs the (pure) aligner against its localized line words.
            ChantNetwork.sendPracticeWords(player, heard);
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
        declareMode(player, CastMode.OPEN, null);
        lock(player, COMPLETION_LOCKOUT_MS);
        ChantNetwork.sendEnd(player, true);

        // wr#43 practice completion: zero mana, zero cooldown, no effect —
        // the rehearsal settles half-rate mastery (daily capped) instead.
        if (engine.isPractice()) {
            float applied = LearningService.onPractice(player, spell);
            float avg = engine.averageScore();
            var band = com.theo.wizardreal.match.ScoreBands.DEFAULT.bandOf(avg);
            hint(player, switch (band) {
                case PERFECT -> "wizardreal.practice.band.perfect";
                case EXCELLENT -> "wizardreal.practice.band.excellent";
                case PASS -> "wizardreal.practice.band.pass";
                default -> "wizardreal.practice.ended";
            });
            WizardReal.LOGGER.info("{} practiced {} ({} lines, avg score {}, mastery +{})",
                    player.getName().getString(), spell.id(), completedLines,
                    String.format(java.util.Locale.ROOT, "%.2f", avg), applied);
            return;
        }

        // Power tier for the full chant (chant_policy.power_per_line, default 1.0),
        // plus the chant-stage resolution (magic_eco 03): completed lines AND the
        // caster's mastery decide which stage's effects fire. 空转咏唱 (issue
        // #41): an empty bar never wastes the recital — the stage degrades to
        // what the current mana affords (base unaffordable = fizzle).
        int stage = StageResolver.resolve(spell.chantStages(), completedLines, masteryT(player, spell));
        int affordable = SpellCastHandler.affordableStage(player, spell, stage);
        if (affordable < 0) {
            WizardReal.LOGGER.info("{} completed chant for {} but has no mana (fizzle)",
                    player.getName().getString(), spell.id());
            fail(player, engine);
            return;
        }
        // wr#43 评分带: a PERFECT chant resonates — +10% power for this cast
        // only. The band never changes success/failure, and resonance never
        // touches mastery (两轴分离).
        float power = powerFor(spell, completedLines);
        if (com.theo.wizardreal.match.ScoreBands.DEFAULT.bandOf(engine.averageScore())
                == com.theo.wizardreal.match.ScoreBands.Band.PERFECT) {
            power *= RESONANCE_POWER_MULTIPLIER;
            hint(player, "wizardreal.practice.resonance");
            WizardReal.LOGGER.info("{}'s chant for {} resonated (perfect, power x{})",
                    player.getName().getString(), spell.id(), RESONANCE_POWER_MULTIPLIER);
        }
        SpellCastHandler.handleCast(player, spell.id(), power, affordable);
        WizardReal.LOGGER.info("{} completed chant for {} ({} lines, stage {})",
                player.getName().getString(), spell.id(), completedLines, affordable);
    }

    /** D9 咒名跳章: the spell-name line was spoken mid-chant; cast at the
     * completed-lines power tier (skip_allowed governs, enforced here). */
    private void earlyRelease(ServerPlayer player, ChantEngine engine, int completedLines) {
        Spell spell = engine.spell();
        ChantPolicy policy = spell.chantPolicy();
        active.remove(player.getUUID());
        declareMode(player, CastMode.OPEN, null);
        if (engine.isPractice()) {
            // wr#43: practice early release = a shorter rehearsal — same
            // zero-stake settlement as the full completion.
            ChantNetwork.sendEnd(player, true);
            LearningService.onPractice(player, spell);
            float avg = engine.averageScore();
            WizardReal.LOGGER.info("{} released {} early in practice ({} lines, avg {})",
                    player.getName().getString(), spell.id(), completedLines,
                    String.format(java.util.Locale.ROOT, "%.2f", avg));
            return;
        }
        if (policy != null && !policy.skipAllowed()) {
            // 禁咒: the jump is forbidden — treat as a failed chant (no cast).
            WizardReal.LOGGER.info("{} tried to jump chapters on forbidden chant {}",
                    player.getName().getString(), spell.id());
            fail(player, engine);
            return;
        }
        lock(player, COMPLETION_LOCKOUT_MS);
        ChantNetwork.sendEnd(player, true);
        // 空转咏唱 (issue #41): same stage degrade as the full completion.
        int stage = StageResolver.resolve(spell.chantStages(), completedLines, masteryT(player, spell));
        int affordable = SpellCastHandler.affordableStage(player, spell, stage);
        if (affordable < 0) {
            WizardReal.LOGGER.info("{} released {} early but has no mana (fizzle)",
                    player.getName().getString(), spell.id());
            fail(player, engine);
            return;
        }
        SpellCastHandler.handleCast(player, spell.id(), powerFor(spell, completedLines), affordable);
        WizardReal.LOGGER.info("{} released {} early ({} lines complete, stage {})",
                player.getName().getString(), spell.id(), completedLines, affordable);
    }

    /** Caster's learning percent for this spell (D4) — the stage ladder's gate. */
    private static float masteryT(ServerPlayer player, Spell spell) {
        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        return state.learningPercent(player.getUUID(), spell.id(), spell.difficulty());
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
     * {@code chant_policy.skip_allowed} AND mastery t >= [learning].
     * skipChantThreshold (D9: 熟练=免咏唱). Performs the cast at the spell's
     * lowest power tier.
     * @return true when the skip cast was performed.
     */
    public boolean trySkipCast(ServerPlayer player, Spell spell) {
        ChantPolicy policy = spell.chantPolicy();
        if (policy != null && !policy.skipAllowed()) {
            hint(player, "wizardreal.chant.skip_locked");
            return false;
        }
        var settings = WizardRealConfig.loadCached(player.getServer().getServerDirectory().toPath());
        PlayerMagicState state = PlayerMagicState.get(player.getServer());
        float t = state.learningPercent(player.getUUID(), spell.id(), spell.difficulty());
        if (t < settings.learning().skipChantThreshold() * 100f) {
            hint(player, "wizardreal.chant.skip_locked");
            WizardReal.LOGGER.debug("Skip-cast for {} denied (t={} < threshold)", spell.id(), t);
            return false;
        }
        lock(player, COMPLETION_LOCKOUT_MS);
        hint(player, "wizardreal.chant.weak_skip");
        // 破弃 = lowest power tier (chant_policy.power_per_line[0]; default 1.0)
        // and the base stage (1 completed line never clears a stage's after_lines).
        SpellCastHandler.handleCast(player, spell.id(),
                policy == null ? 1.0f : policy.powerFor(1),
                StageResolver.resolve(spell.chantStages(), 1, t));
        WizardReal.LOGGER.info("{} skip-cast {} (t={})", player.getName().getString(), spell.id(), t);
        return true;
    }

    /** Failed chant: darkness with stacking duration (短时间连续失败叠加). */
    private void fail(ServerPlayer player, ChantEngine engine) {
        active.remove(player.getUUID());
        declareMode(player, CastMode.OPEN, null);
        lock(player, CANCEL_LOCKOUT_MS);
        ChantNetwork.sendEnd(player, false);
        if (!engine.isPractice()) {
            // wr#43: practice failures are zero-stake — no streaks, no darkness.
            applyFailBlindness(player);
        }
        WizardReal.LOGGER.info("{}'s chant for {} failed{}", player.getName().getString(),
                engine.spell().id(), engine.isPractice() ? " (practice)" : "");
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
            declareMode(player, CastMode.OPEN, null);
            lock(player, CANCEL_LOCKOUT_MS);
            ChantNetwork.sendEnd(player, success);
        }
    }

    public void onQuit(ServerPlayer player) {
        // No declareMode(OPEN) here: VoiceCast's own quit handler clears the
        // player's session AND cast-mode declaration. Re-declaring from this
        // side races that cleanup and can re-insert a dead-UUID entry into
        // voicecast's castModes map (refine M13 F3) — a quit needs no new
        // declaration at all.
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
            if (p != null) {
                declareMode(p, CastMode.OPEN, null);
                ChantNetwork.sendEnd(p, false);
            }
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
            declareMode(p, CastMode.OPEN, null);
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
