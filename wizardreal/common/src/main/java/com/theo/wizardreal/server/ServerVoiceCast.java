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
import com.theo.wizardreal.item.StaffItem;
import com.theo.wizardreal.match.PhonemeMatcher;
import com.theo.wizardreal.match.SpellMatcher;
import dev.architectury.event.events.common.PlayerEvent;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side voice -> spell wiring. Builds the recognizer vocabulary from
 * registered spells (trigger words + every chant line) and routes each
 * recognized utterance: an in-progress chant gets fed the line, a ritual
 * spell's trigger word enters chanting, an instant spell casts immediately.
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

    private static void handle(ServerPlayer player, String heard, List<String> heardIpa, float confidence,
                               Map<String, Float> templateScores) {
        // Voice casting requires a staff in the main hand.
        if (!(player.getMainHandItem().getItem() instanceof StaffItem)) {
            return;
        }

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

        // 2) Match the utterance to a spell. Preferred: exact CTC forward
        //    scores from the IPA engine (robust to the systematic greedy-decode
        //    errors in workspace-root docs/IPA识别问题.md). Fallbacks: token-based phoneme
        //    matching, then text-alias matching.
        SpellMatcher.Match match = matchByScores(templateScores);
        if (match == null && heardIpa != null && !heardIpa.isEmpty()) {
            PhonemeMatcher.Match pm = PhonemeMatcher.match(heardIpa);
            if (pm != null) match = new SpellMatcher.Match(pm.spell(), pm.score());
        }
        if (match == null && heard != null && !heard.isBlank()) {
            match = SpellMatcher.match(heard);
        }
        if (match == null) {
            WizardReal.LOGGER.debug("Server heard '{}' / [{}] — no spell match", heard,
                    heardIpa == null || heardIpa.isEmpty() ? "" : String.join(" ", heardIpa));
            return;
        }

        Spell spell = match.spell();
        // 3) Ritual spell (has chants) -> start chanting; instant spell -> cast now.
        if (!spell.chants().isEmpty()) {
            ChantManager.get().start(player, spell);
        } else {
            SpellCastHandler.handleCast(player, spell.id(), Math.min(confidence, match.score()));
        }
        WizardReal.LOGGER.info("Server matched '{}' / [{}] -> {} score={} (ritual={})",
                heard, heardIpa == null || heardIpa.isEmpty() ? "" : String.join(" ", heardIpa),
                spell.id(), String.format(java.util.Locale.ROOT, "%.2f", match.score()),
                !spell.chants().isEmpty());
    }

    /**
     * Best spell by CTC forward posterior (pronunciation id -> probability in
     * [0,1]), or null when no template clears its threshold.
     */
    private static SpellMatcher.Match matchByScores(Map<String, Float> scores) {
        if (scores == null || scores.isEmpty()) return null;
        Spell bestSpell = null;
        float bestScore = 0f;
        for (Spell spell : SpellRegistry.all()) {
            Float s = scores.get(spell.pronunciation().id());
            if (s != null && s > bestScore) {
                bestScore = s;
                bestSpell = spell;
            }
        }
        if (bestSpell == null) return null;
        float threshold = bestSpell.threshold() >= 0 ? bestSpell.threshold() : FORWARD_MATCH_THRESHOLD;
        if (bestScore < threshold) return null;
        return new SpellMatcher.Match(bestSpell, bestScore);
    }
}
