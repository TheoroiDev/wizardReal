package com.theo.wizardreal.server;

import com.theo.voicecast.api.SessionVocabulary;
import com.theo.voicecast.api.ThresholdHint;
import com.theo.voicecast.api.VoiceCastEvents;
import com.theo.voicecast.api.event.ServerRecognitionFinalEvent;
import com.theo.voicecast.server.CastMode;
import com.theo.voicecast.server.VoiceCastServer;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Pronunciation;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.config.WizardRealConfig;
import com.theo.wizardreal.g2p.G2p;
import com.theo.wizardreal.item.StaffItem;
import com.theo.wizardreal.net.ChantNetwork;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side voice -> spell wiring (semantic contract v2, C1b). Builds the
 * {@link SessionVocabulary} from registered spells (trigger words + every
 * chant line) — the ONE push entry point, carrying the game-side threshold
 * hints (per-spell {@code threshold} overrides + the reject-level overlay) —
 * and routes each ADJUDICATED utterance via the {@link ChantGate} semantic
 * gate (D9): a ritual spell's first line (L1) enters the chant pre-locked
 * (首行即门), a ritual trigger / spell-name becomes a skip-cast candidate
 * (破弃, learning-gated), an instant spell casts immediately. Damage
 * mid-chant interrupts (07 M1).
 *
 * <p>Decision authority (C1b §0.2): voicecast fuses the evidence lines and
 * emits {@code Decision + spellId + pronId + score + alternatives};
 * WizardReal only maps that verdict onto gameplay. The pre-v2
 * {@code FORWARD_MATCH_THRESHOLD} constant and the consumer-side matcher
 * chain (templateScores/phoneme/text + ctcPresent gating) are gone — the
 * threshold brain lives in voicecast config ({@code [match]}) plus the
 * per-entry hints pushed here.
 */
public final class ServerVoiceCast {

    private ServerVoiceCast() {}

    public static void init() {
        VoiceCastEvents.subscribe(ServerRecognitionFinalEvent.class, e -> {
            MinecraftServer server = e.player().getServer();
            if (server == null) return;
            server.execute(() -> handle(e.player(), e.result()));
        });

        // Casting-time mode default (issue #30/D-15, amended by the P30
        // supervisor ruling): free casting = OPEN (full word list ∩ engine
        // language buckets — identical candidates to the pre-#30 default).
        // Declared on join so it reaches the session before the first
        // utterance; re-declared on chant end (see ChantManager).
        PlayerEvent.PLAYER_JOIN.register(player ->
                setCastMode(player, CastMode.OPEN, List.of()));

        // Clean up chants when a player leaves.
        PlayerEvent.PLAYER_QUIT.register(player -> ChantManager.get().onQuit(player));
        // Periodic timeout sweep.
        TickEvent.SERVER_POST.register(server -> ChantManager.get().tick(server));
        // 07 M1 咏唱打断: damage taken mid-chant rolls the chant back (or fails it).
        EntityEvent.LIVING_HURT.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer sp) {
                ChantManager.get().onPlayerDamaged(sp, amount);
            }
            return EventResult.pass();
        });
    }

    /** Language-trimmed push ([voice] languages): only the enabled buckets of
     * every entry reach the recognizer grammar — fewer competing entries
     * means fewer cross-language false triggers and sharper CTC posteriors.
     * Legacy (bucket-less) entries always pass. With {@code [voice]
     * g2pDrafts}, entries without curated templates get G2P drafts at push
     * time (runtime-only: the JSON stays untouched; the drafts are
     * unverified — see the 2026-09 backtest notes in docs/ipa/).
     *
     * <p>Threshold hints (C1b §0.3): per-spell {@code threshold()} overrides
     * (≥ 0) apply to every tier of the spell's entries; the reject level
     * overlays per-surface suppression as disabled-tier hints (level ≥ 1
     * kills the trigger text/phoneme surfaces, level ≥ 2 additionally the
     * first-line surfaces). Data over the boundary — the application logic
     * is voicecast's. */
    public static void pushVocabulary(MinecraftServer server) {
        WizardRealConfig.VoiceSettings voice = WizardRealConfig.loadCached(
                server.getServerDirectory().toPath()).voice();
        Set<String> enabled = voice.enabledLanguages();
        boolean drafts = voice.g2pDrafts();
        int level = ChantGate.rejectLevel();
        Map<String, SessionVocabulary.Entry> merged = new LinkedHashMap<>();
        for (Spell spell : SpellRegistry.all()) {
            ThresholdHint spellHint = spell.threshold() >= 0 ? ThresholdHint.all(spell.threshold()) : null;
            SessionVocabulary.Entry t = entry(spell.pronunciation(), enabled, drafts, level, spellHint);
            if (t != null) merged.put(t.id(), t);
            for (Chant chant : spell.chants()) {
                for (ChantLine line : chant.lines()) {
                    SessionVocabulary.Entry p = entry(line.pronunciation(), enabled, drafts, level,
                            spellHint, true);
                    if (p != null) merged.putIfAbsent(p.id(), p);
                }
            }
        }
        VoiceCastServer.INSTANCE.setVocabulary(new SessionVocabulary(new ArrayList<>(merged.values())));
        WizardReal.LOGGER.info("Pushed {} recognizer vocabulary entries (spells + chant lines, "
                        + "languages={}, g2pDrafts={}, rejectLevel={})",
                merged.size(), enabled.isEmpty() ? "all" : enabled, drafts, level);
    }

    /** Casting-time mode declaration (issue #30/D-1 passthrough): forwards the
     *  player's D-15 routing mode to the voicecast session — free casting =
     *  {@link CastMode#OPEN} (full word list, P30 supervisor ruling), ladder
     *  chant = {@link CastMode#CHANT_CONFIRM} + the current spell, practice
     *  surfaces (M4) = {@link CastMode#PRACTICE_CONFIRM}. {@code null} mode
     *  clears the declaration (full vocabulary, the pre-#30 behavior). Safe
     *  from any thread; declarations survive session rebuilds until the player
     *  quits. */
    public static void setCastMode(ServerPlayer player, CastMode mode, Collection<String> spellIds) {
        VoiceCastServer.INSTANCE.setCastMode(player, mode, spellIds);
    }

    /** Convenience overload for modes that need no declared spells (OPEN). */
    public static void setCastMode(ServerPlayer player, CastMode mode) {
        setCastMode(player, mode, List.of());
    }

    /** Build one push entry: language trim, optional G2P drafts, reject-level
     *  hint overlay and the per-spell threshold override. */
    private static SessionVocabulary.Entry entry(Pronunciation p, Set<String> enabled, boolean drafts,
                                                 int level, ThresholdHint spellHint) {
        return entry(p, enabled, drafts, level, spellHint, false);
    }

    private static SessionVocabulary.Entry entry(Pronunciation p, Set<String> enabled, boolean drafts,
                                                 int level, ThresholdHint spellHint, boolean chantLine) {
        if (p == null) return null;
        Pronunciation trimmed = languageTrim(p, enabled);
        if (trimmed == null) return null;
        Pronunciation effective = drafts ? withDrafts(trimmed, enabled) : trimmed;
        // Entry re-flattens: with buckets the flat aliases become the legacy
        // extras (deduped); legacy entries keep their flat list.
        return new SessionVocabulary.Entry(effective.id(), effective.ipa(), effective.aliases(),
                effective.languages(), hintOf(effective, level, spellHint, chantLine));
    }

    /**
     * Push-time threshold hint (the reject-level overlay as DATA):
     * level &ge; 1 disables the text/phoneme tiers of TRIGGER entries (the
     * pre-v2 "suppress the matcher fallbacks" branch); level &ge; 2
     * additionally disables both tiers of FIRST-LINE entries (the pre-v2
     * "suppress the lenient L1 gate" branch). Forward is never level-gated —
     * a true utterance should clear the CTC/posterior evidence. Per-spell
     * {@code threshold()} overrides apply beneath the overlay.
     */
    static ThresholdHint hintOf(Pronunciation p, int level, ThresholdHint spellHint, boolean chantLine) {
        ThresholdHint base = spellHint;
        boolean trigger = !chantLine;
        boolean firstLine = chantLine && p.id() != null && p.id().endsWith(":0");
        boolean overlay = (level >= 1 && trigger) || (level >= 2 && firstLine);
        if (!overlay) return base;
        Float forward = base == null ? null : base.forward();
        float phoneme = base == null ? ThresholdHint.DISABLED : orDisabled(base.phoneme());
        float text = base == null ? ThresholdHint.DISABLED : orDisabled(base.text());
        return new ThresholdHint(forward, phoneme, text);
    }

    private static float orDisabled(Float value) {
        return value == null ? ThresholdHint.DISABLED : Math.max(value, ThresholdHint.DISABLED);
    }

    /** G2P draft fill for template-less entries ([voice] g2pDrafts): every
     * alias of every enabled bucket is converted (globally deduped, mirroring
     * gen_ipa). Strict — unknown scripts produce no draft and an entry with
     * zero successful aliases stays template-less. */
    static Pronunciation withDrafts(Pronunciation p, Set<String> enabled) {
        if (!p.ipa().isEmpty()) return p;
        List<String> drafts = new ArrayList<>();
        if (p.languages().isEmpty()) { // legacy bucket: routed everywhere
            for (String alias : p.aliases()) {
                String draft = G2p.toIpa(alias, "");
                if (!draft.isBlank() && !drafts.contains(draft)) drafts.add(draft);
            }
        } else {
            // Language trim applies to drafts too: with buckets but no enabled
            // match, the entry stays template-less. ALL enabled buckets
            // aggregate (first-bucket-only dropped zh drafts when en failed to
            // convert - R1 audit B#6).
            for (Map.Entry<String, List<String>> bucket : p.languages().entrySet()) {
                if (!enabled.isEmpty()
                        && !enabled.contains(bucket.getKey().toLowerCase(Locale.ROOT))) {
                    continue;
                }
                String lang = bucket.getKey().toLowerCase(Locale.ROOT);
                for (String alias : bucket.getValue()) {
                    String draft = G2p.toIpa(alias, lang);
                    if (!draft.isBlank() && !drafts.contains(draft)) drafts.add(draft);
                }
            }
        }
        if (drafts.isEmpty()) return p;
        return new Pronunciation(p.id(), drafts, p.aliases(), p.languages());
    }

    /** Keep only the enabled language buckets; {@code null} drops the entry.
     * Ids with {@code .chant.<lang>.} are filtered by the embedded language. */
    private static Pronunciation languageTrim(Pronunciation p, Set<String> enabled) {
        if (enabled.isEmpty()) return p;
        String id = p.id();
        int marker = id.indexOf(".chant.");
        if (marker >= 0) {
            String rest = id.substring(marker + 7);
            int dot = rest.indexOf('.');
            String lang = (dot < 0 ? rest : rest.substring(0, dot)).toLowerCase(Locale.ROOT);
            return enabled.contains(lang) ? p : null;
        }
        if (p.languages().isEmpty()) return p; // legacy bucket: always routed
        Map<String, List<String>> kept = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : p.languages().entrySet()) {
            if (enabled.contains(e.getKey().toLowerCase(Locale.ROOT))) kept.put(e.getKey(), e.getValue());
        }
        return kept.isEmpty() ? null : new Pronunciation(p.id(), p.ipa(), List.of(), kept);
    }

    private static void handle(ServerPlayer player, com.theo.voicecast.api.RecognitionResult result) {
        // Voice casting requires a staff in the main hand.
        if (!(player.getMainHandItem().getItem() instanceof StaffItem)) {
            return;
        }
        String heard = result == null ? "" : result.utteranceText();
        String ipa = result == null ? "" : result.ipa();

        // 1) In a chant? feed the line (never instant-cast mid-chant).
        if (ChantManager.get().isChanting(player)) {
            // Drop utterances with neither text nor tokens (noise that produced
            // no greedy decode) so they don't count as failed chant lines.
            if (heard.isBlank() && ipa.isBlank()) {
                return;
            }
            ChantManager.get().feed(player, heard, ipa.isBlank()
                    ? List.of() : List.of(ipa.split(" ")), result.score(),
                    new com.theo.voicecast.match.ChantLineMatcher.ChantVerdict(
                            result.pronId(), result.decision() == null ? "" : result.decision().name(),
                            result.score()));
            return;
        }

        // 1b) Chant lockout: during a chant nothing else can trigger, and for
        //     a short window after a chant ended every further utterance is
        //     swallowed — the final word usually matches the ritual trigger
        //     again, and recognition emits several finals per utterance (one
        //     per flush), which would restart the chant.
        if (ChantManager.get().isLocked(player)) {
            WizardReal.LOGGER.debug("Chant lockout for {}: ignoring '{}'", player.getName().getString(), heard);
            return;
        }

        // 2) Idle routing (D9) on the ADJUDICATED result (C1b): the semantic
        //    gate maps Decision+pronId -> gameplay kind; the matcher chain,
        //    the rejection levels' surface logic and the language trim live
        //    upstream (voicecast adjudicator + push-time hints).
        ChantGate.Decision gate = ChantGate.route(result, ChantGate.rejectLevel());
        switch (gate.kind()) {
            case ENTER -> {
                // Issue #41 preflight: the learning gate / staff / origin /
                // cooldown checks fire BEFORE the chant starts, with the
                // specific reason on the action bar — reciting a full ritual
                // into a locked gate is wasted breath. Mana is deliberately
                // not preflighted (空转咏唱: the chant completes and the cast
                // degrades by stage, ChantManager completion path).
                String blocked = SpellCastHandler.preflight(player, gate.spell().id());
                if (blocked != null) {
                    WizardReal.LOGGER.info("Server heard '{}' / [{}] -> chant entry {} blocked preflight ({})",
                            heard, ipa, gate.spell().id(), blocked);
                    return;
                }
                ChantManager.get().startAtLine(player, gate.spell(), gate.variant());
                WizardReal.LOGGER.info("Server heard '{}' / [{}] -> chant entry {} variant {}",
                        heard, ipa, gate.spell().id(), gate.variant());
            }
            case INSTANT -> {
                // Instant voice cast: base power 1.0 (recognition confidence is
                // not a power factor — the performance layer owns that in P2).
                SpellCastHandler.handleCast(player, gate.spell().id(), 1.0f);
                WizardReal.LOGGER.info("Server matched '{}' / [{}] -> {} score={} (instant)",
                        heard, ipa, gate.spell().id(),
                        String.format(java.util.Locale.ROOT, "%.2f", gate.score()));
            }
            case SKIP -> {
                WizardReal.LOGGER.info("Server heard '{}' -> skip-cast candidate {}",
                        heard, gate.spell().id());
                ChantManager.get().trySkipCast(player, gate.spell());
            }
            case NONE -> {
                // Issue #41 拒识有反馈: surface the rejection on the HUD
                // instead of a debug line. REJECTED = the adjudicator heard
                // speech but refused it ("咒文消散在风中"); otherwise the
                // top runner-up candidate reads as a near-whisper of that
                // spell ("似是而非的低语……最接近：…"). Pure noise — no
                // decision, no candidates — stays silent.
                sendIdleFeedback(player, result);
                WizardReal.LOGGER.debug("Server heard '{}' / [{}] — no spell match "
                        + "(decision={})", heard, ipa, result == null ? null : result.decision());
            }
        }
    }

    /** Idle rejection feedback (issue #41): maps the adjudication outcome to
     *  the S2C {@link ChantNetwork} notice. */
    private static void sendIdleFeedback(ServerPlayer player,
                                         com.theo.voicecast.api.RecognitionResult result) {
        if (result == null) return;
        String kind = noticeKind(result.decision(), result.alternatives());
        if (kind == null) return;
        String nameKey = "";
        if (ChantNetwork.NOTICE_WHISPER.equals(kind)) {
            for (com.theo.voicecast.api.Alternative alt : result.alternatives()) {
                Spell spell = SpellRegistry.get(alt.spellId()).orElse(null);
                if (spell != null) {
                    nameKey = spell.nameKey();
                    break;
                }
            }
            if (nameKey.isEmpty()) return; // candidates exist but none registered
        }
        ChantNetwork.sendNotice(player, kind, nameKey);
    }

    /** Pure kind selection (unit-tested): REJECTED = dissipate outright;
     *  anything else with runner-up candidates = whisper; no candidates =
     *  silent. {@code null} means "no notice". */
    static String noticeKind(com.theo.voicecast.api.Decision decision,
                             List<com.theo.voicecast.api.Alternative> alternatives) {
        if (decision == com.theo.voicecast.api.Decision.REJECTED) {
            return ChantNetwork.NOTICE_DISSIPATE;
        }
        return alternatives != null && !alternatives.isEmpty() ? ChantNetwork.NOTICE_WHISPER : null;
    }
}
