package com.theo.wizardreal.server;

import com.theo.voicecast.api.Pronunciation;
import com.theo.voicecast.api.VoiceCastEvents;
import com.theo.voicecast.api.event.ServerRecognitionFinalEvent;
import com.theo.voicecast.server.VoiceCastServer;
import com.theo.wizardreal.WizardReal;
import com.theo.wizardreal.api.Chant;
import com.theo.wizardreal.api.ChantLine;
import com.theo.wizardreal.api.Spell;
import com.theo.wizardreal.api.SpellRegistry;
import com.theo.wizardreal.config.WizardRealConfig;
import com.theo.wizardreal.item.StaffItem;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.common.EntityEvent;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Server-side voice -> spell wiring. Builds the recognizer vocabulary from
 * registered spells (trigger words + every chant line) and routes each
 * recognized utterance via the {@link ChantGate} idle router (D9): a ritual
 * spell's first line (L1) enters the chant pre-locked (首行即门), a ritual
 * trigger / spell-name becomes a skip-cast candidate (破弃, learning-gated),
 * an instant spell casts immediately. Damage mid-chant interrupts (07 M1).
 */
public final class ServerVoiceCast {
    /**
     * Default posterior threshold for CTC forward scoring: the softmax includes
     * every pushed vocabulary template plus a "nothing said" null competitor,
     * so >= 0.6 means the acoustic evidence clearly favors this spell over all
     * others and silence. Per-spell override via {@link Spell#threshold()}.
     */
    public static final float FORWARD_MATCH_THRESHOLD = 0.6f;

    private ServerVoiceCast() {}

    public static void init() {
        VoiceCastEvents.subscribe(ServerRecognitionFinalEvent.class, e -> {
            MinecraftServer server = e.player().getServer();
            if (server == null) return;
            String text = e.result() == null ? "" : e.result().text();
            List<String> ipa = e.result() == null ? List.of() : e.result().ipaTokens();
            float conf = e.result() == null ? 0f : e.result().confidence();
            Map<String, Float> scores = e.result() == null ? Map.of() : e.result().templateScores();
            server.execute(() -> handle(e.player(), text, ipa, conf, scores));
        });

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

    /** Build vocabulary (trigger words + all chant lines) and push to the server. */
    public static void pushVocabulary() {
        Map<String, Pronunciation> merged = new LinkedHashMap<>();
        for (Spell spell : SpellRegistry.all()) {
            merged.put(spell.pronunciation().id(), spell.pronunciation());
            for (Chant chant : spell.chants()) {
                for (ChantLine line : chant.lines()) {
                    Pronunciation p = line.pronunciation();
                    merged.putIfAbsent(p.id(), p);
                }
            }
        }
        VoiceCastServer.INSTANCE.setVocabulary(new ArrayList<>(merged.values()));
        WizardReal.LOGGER.info("Pushed {} recognizer pronunciations (spells + chant lines)", merged.size());
    }

    /** Language-trimmed push ([voice] languages): only the enabled buckets of
     * every pronunciation reach the recognizer grammar — fewer competing
     * templates means fewer cross-language false triggers and sharper CTC
     * posteriors. Legacy (bucket-less) pronunciations always pass. */
    public static void pushVocabulary(MinecraftServer server) {
        Set<String> enabled = WizardRealConfig.loadCached(
                server.getServerDirectory().toPath()).voice().enabledLanguages();
        Map<String, Pronunciation> merged = new LinkedHashMap<>();
        for (Spell spell : SpellRegistry.all()) {
            Pronunciation t = languageTrim(spell.pronunciation(), enabled);
            if (t != null) merged.put(t.id(), t);
            for (Chant chant : spell.chants()) {
                for (ChantLine line : chant.lines()) {
                    Pronunciation p = languageTrim(line.pronunciation(), enabled);
                    if (p != null) merged.putIfAbsent(p.id(), p);
                }
            }
        }
        VoiceCastServer.INSTANCE.setVocabulary(new ArrayList<>(merged.values()));
        WizardReal.LOGGER.info("Pushed {} recognizer pronunciations (spells + chant lines, languages={})",
                merged.size(), enabled.isEmpty() ? "all" : enabled);
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

    private static void handle(ServerPlayer player, String heard, List<String> heardIpa, float confidence,
                               Map<String, Float> templateScores) {
        // Voice casting requires a staff in the main hand.
        if (!(player.getMainHandItem().getItem() instanceof StaffItem)) {
            return;
        }
        // Language trim for the matcher chain ([voice] languages, empty = all).
        MinecraftServer server0 = player.getServer();
        Set<String> allowedLanguages = server0 == null ? Set.of()
                : WizardRealConfig.loadCached(server0.getServerDirectory().toPath())
                        .voice().enabledLanguages();

        // 1) In a chant? feed the line (never instant-cast mid-chant).
        if (ChantManager.get().isChanting(player)) {
            // Drop utterances with neither text nor tokens (noise that produced
            // no greedy decode) so they don't count as failed chant lines.
            if ((heard == null || heard.isBlank()) && (heardIpa == null || heardIpa.isEmpty())) {
                return;
            }
            ChantManager.get().feed(player, heard, heardIpa, confidence);
            return;
        }

        // 1b) Chant lockout: during a chant nothing else can trigger, and for
        //     a short window after a chant ended every further utterance is
        //     swallowed — the final word usually matches the ritual trigger
        //     again, and recognition emits several finals per utterance (one
        //     per engine + endpoint flush), which would restart the chant.
        if (ChantManager.get().isLocked(player)) {
            WizardReal.LOGGER.debug("Chant lockout for {}: ignoring '{}'", player.getName().getString(), heard);
            return;
        }

        // 2) Idle routing (D9): L1 entry gate (首行即门) / instant cast /
        //    skip-cast candidate (破弃). ChantGate owns the matcher chain
        //    (templateScores -> phoneme -> text), the rejection levels and the
        //    language trim.
        ChantGate.Decision gate = ChantGate.route(heard, heardIpa, templateScores, allowedLanguages);
        switch (gate.kind()) {
            case ENTER -> {
                ChantManager.get().startAtLine(player, gate.spell(), gate.variant());
                WizardReal.LOGGER.info("Server heard '{}' / [{}] -> chant entry {} variant {}",
                        heard, heardIpa == null || heardIpa.isEmpty() ? "" : String.join(" ", heardIpa),
                        gate.spell().id(), gate.variant());
            }
            case INSTANT -> {
                // Instant voice cast: base power 1.0 (recognition confidence is
                // not a power factor — the performance layer owns that in P2).
                SpellCastHandler.handleCast(player, gate.spell().id(), 1.0f);
                WizardReal.LOGGER.info("Server matched '{}' / [{}] -> {} score={} (instant)",
                        heard, heardIpa == null || heardIpa.isEmpty() ? "" : String.join(" ", heardIpa),
                        gate.spell().id(), String.format(java.util.Locale.ROOT, "%.2f", gate.score()));
            }
            case SKIP -> {
                WizardReal.LOGGER.info("Server heard '{}' -> skip-cast candidate {}",
                        heard, gate.spell().id());
                ChantManager.get().trySkipCast(player, gate.spell());
            }
            case NONE -> WizardReal.LOGGER.debug("Server heard '{}' / [{}] — no spell match", heard,
                    heardIpa == null || heardIpa.isEmpty() ? "" : String.join(" ", heardIpa));
        }
    }
}
