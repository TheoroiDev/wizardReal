import com.theo.voicecast.api.Pronunciation;
import com.theo.voicecast.api.RecognitionResult;
import com.theo.voicecast.api.SpeechOptions;
import com.theo.voicecast.api.engine.EngineSpec;
import com.theo.voicecast.engine.IpaPhonemeRecognizer;
import com.theo.voicecast.engine.SherpaSenseVoiceRecognizer;
import com.theo.voicecast.engine.SherpaStreamingRecognizer;
import com.theo.voicecast.model.Json;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-chain multi-engine bench for the wizardreal backtest harness
 * (SS2, tools/benchmark): runs the REAL voicecast recognizers from the fat jar
 * over TTS wavs — no lab-side replicas, so results match what the server would
 * compute.
 *
 * <p>Input (JSON file, argv[0]):
 * <pre>
 * {
 *   "ipaThreshold": 0.85,
 *   "engines": [ {"id": "...", "type": "stream|offline|ipa", "modelDir": "...",
 *                 "languages": ["zh","en"], "options": {"num_threads": "2", ...}} ],
 *   "items":   [ {"id": "...", "spell": "...", "lang": "en", "alias": "ignis",
 *                 "ipa": "i g n i s", "wav": "...", "condition": "clean"} ]
 * }
 * </pre>
 * Per engine, only items whose {@code lang} is in the engine's languages run
 * (type=ipa takes every item). Text engines load ALL of their items' aliases
 * as one vocabulary — mirroring production, where a session's recognizer is
 * fed every spell alias of the chosen engine. The IPA engine grades each item
 * as a one-entry vocabulary (its own CTC posterior), like IpaBench.
 *
 * <p>Verdict for text engines mirrors wizardreal's production
 * {@code com.theo.wizardreal.match.SpellMatcher} (normalize + whole-word
 * containment + Levenshtein similarity, threshold 0.8) — deliberately copied,
 * not referenced, so the bench stays runnable from the voicecast fat jar
 * alone. Drift risk: if SpellMatcher changes, re-port. Output: one JSON line
 * per (engine, item) on stdout.
 */
public final class EngineBench {
    private static final float MATCH_THRESHOLD = 0.8f;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: EngineBench <input.json>");
            System.exit(2);
        }
        Map<String, Object> input = Json.parseObject(
                new String(java.nio.file.Files.readAllBytes(Path.of(args[0]))));
        double ipaThreshold = Double.parseDouble(Json.getString(input, "ipaThreshold", "0.85"));

        List<Object> engines = Json.getList(input, "engines");
        List<Object> items = Json.getList(input, "items");

        for (Object eo : engines) {
            Map<String, Object> e = Json.asMap(eo);
            String id = Json.getString(e, "id", "engine");
            String type = Json.getString(e, "type", "");
            Path modelDir = Path.of(Json.getString(e, "modelDir", ""));
            List<String> languages = Json.getStringList(e, "languages");
            Map<String, String> options = new LinkedHashMap<>();
            for (Object o : Json.getList(e, "options")) {
                Map<String, Object> kv = Json.asMap(o);
                options.put(Json.getString(kv, "k", ""), Json.getString(kv, "v", ""));
            }
            EngineSpec spec = new EngineSpec(type, id, modelDir, languages, options);

            List<Map<String, Object>> mine = new ArrayList<>();
            for (Object io : items) {
                Map<String, Object> it = Json.asMap(io);
                String lang = Json.getString(it, "lang", "");
                if (type.equals("ipa") || languages.isEmpty() || languages.contains(lang)) {
                    mine.add(it);
                }
            }
            if (mine.isEmpty()) {
                System.err.println("[EngineBench] no items for engine " + id + ", skipping");
                continue;
            }
            System.err.println("[EngineBench] engine=" + id + " type=" + type
                    + " items=" + mine.size());
            switch (type) {
                case "ipa" -> benchIpa(spec, mine, ipaThreshold);
                case "stream" -> benchStream(spec, mine);
                case "offline" -> benchOffline(spec, mine);
                default -> System.err.println("[EngineBench] unknown type '" + type + "' for " + id);
            }
        }
        System.err.println("[EngineBench] done");
    }

    // ---------------------------------------------------------------- engines

    private static void benchIpa(EngineSpec spec, List<Map<String, Object>> items,
                                 double threshold) throws Exception {
        IpaPhonemeRecognizer recognizer = new IpaPhonemeRecognizer();
        recognizer.start(new SpeechOptions(true, 0.65f, spec.modelDir().toString(), true));
        for (Map<String, Object> it : items) {
            String id = Json.getString(it, "id", "item");
            String ipa = Json.getString(it, "ipa", "");
            Map<String, Object> out = base(spec, it);
            short[] pcm = readWavMono16k(Path.of(Json.getString(it, "wav", "")));
            if (ipa.isBlank() || pcm == null || pcm.length == 0) {
                out.put("verdict", "error");
                out.put("error", ipa.isBlank() ? "no ipa template" : "unreadable wav");
                System.out.println(Json.write(out));
                continue;
            }
            // One-entry vocabulary: the template's own CTC posterior is the grade.
            recognizer.setVocabulary(List.of(new Pronunciation(id, List.of(ipa), List.of(ipa))));
            AtomicReference<RecognitionResult> latest = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            recognizer.setResultSink(res -> {
                latest.set(res);
                done.countDown();
            });
            long t0 = System.nanoTime();
            recognizer.acceptPcm(pcm, 0, pcm.length);
            recognizer.finishUtterance();
            boolean finished = done.await(30, TimeUnit.SECONDS);
            long latency = (System.nanoTime() - t0) / 1_000_000L;
            RecognitionResult res = latest.get();
            Map<String, Float> scores = res == null ? null : res.templateScores();
            Float score = scores == null ? null : scores.get(id);
            double s = score == null ? 0.0 : score.doubleValue();
            out.put("score", s);
            out.put("latencyMs", latency);
            out.put("verdict", !finished ? "error" : (s >= threshold ? "pass" : "fail"));
            if (!finished) out.put("error", "decode timeout");
            System.out.println(Json.write(out));
        }
        recognizer.stop();
    }

    private static void benchStream(EngineSpec spec, List<Map<String, Object>> items) throws Exception {
        SherpaStreamingRecognizer recognizer = new SherpaStreamingRecognizer(spec);
        recognizer.setResultSink(res -> { }); // finals arrive synchronously; set per item below
        recognizer.setVocabulary(productionVocabulary(items)); // hotwords, like a live session
        recognizer.start(new SpeechOptions(true, 0.65f, spec.modelDir().toString(), true));
        runTextItems(recognizer, spec, items);
        recognizer.stop();
    }

    private static void benchOffline(EngineSpec spec, List<Map<String, Object>> items) throws Exception {
        SherpaSenseVoiceRecognizer recognizer = new SherpaSenseVoiceRecognizer(spec);
        recognizer.setVocabulary(List.of()); // no-op by contract (open vocabulary)
        recognizer.start(new SpeechOptions(true, 0.65f, spec.modelDir().toString(), true));
        runTextItems(recognizer, spec, items);
        recognizer.stop();
    }

    /** Text engines share the flow: per item feed + finish, capture the FINAL
     * result (partials ignored), grade the item's own alias with the matcher. */
    private static void runTextItems(com.theo.voicecast.api.SpeechRecognizer recognizer,
                                     EngineSpec spec, List<Map<String, Object>> items) {
        for (Map<String, Object> it : items) {
            Map<String, Object> out = base(spec, it);
            short[] pcm = readWavMono16k(Path.of(Json.getString(it, "wav", "")));
            if (pcm == null || pcm.length == 0) {
                out.put("verdict", "error");
                out.put("error", "unreadable wav");
                System.out.println(Json.write(out));
                continue;
            }
            AtomicReference<RecognitionResult> fin = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            recognizer.setResultSink(res -> {
                if (res != null && !res.partial()) {
                    fin.set(res);
                    done.countDown();
                }
            });
            String alias = Json.getString(it, "alias", "");
            long t0 = System.nanoTime();
            try {
                recognizer.acceptPcm(pcm, 0, pcm.length);
                recognizer.finishUtterance();
            } catch (Throwable t) {
                out.put("verdict", "error");
                out.put("error", String.valueOf(t));
                System.out.println(Json.write(out));
                continue;
            }
            long latency = (System.nanoTime() - t0) / 1_000_000L;
            RecognitionResult res = fin.get();
            String text = res == null ? "" : res.text();
            float match = Mirror.scoreAlias(alias, text);
            out.put("text", text);
            out.put("matchScore", (double) match);
            out.put("latencyMs", latency);
            // A null final means the recognizer emitted no text (finishUtterance
            // swallows blank transcriptions) — that is a MISS, not a harness
            // error. `error` is reserved for exceptions / unreadable wavs.
            out.put("verdict", match >= MATCH_THRESHOLD ? "pass" : "fail");
            System.out.println(Json.write(out));
        }
    }

    /** Production session shape: one Pronunciation per spell, aliases = every
     * distinct alias seen for this engine's items. */
    private static List<Pronunciation> productionVocabulary(List<Map<String, Object>> items) {
        Map<String, Set<String>> bySpell = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            String spell = Json.getString(it, "spell", "spell");
            String alias = Json.getString(it, "alias", "");
            if (!alias.isBlank()) bySpell.computeIfAbsent(spell, k -> new LinkedHashSet<>()).add(alias);
        }
        List<Pronunciation> vocab = new ArrayList<>();
        for (Map.Entry<String, Set<String>> en : bySpell.entrySet()) {
            vocab.add(new Pronunciation(en.getKey(), List.of(), List.copyOf(en.getValue())));
        }
        return vocab;
    }

    private static Map<String, Object> base(EngineSpec spec, Map<String, Object> it) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engine", spec.engineId());
        out.put("type", spec.type());
        out.put("id", Json.getString(it, "id", "item"));
        out.put("spell", Json.getString(it, "spell", ""));
        out.put("lang", Json.getString(it, "lang", ""));
        out.put("alias", Json.getString(it, "alias", ""));
        out.put("condition", Json.getString(it, "condition", "clean"));
        out.put("backend", Json.getString(it, "backend", ""));
        return out;
    }

    // ---------------------------------------------------------------- matcher
    // Mirror of wizardreal com.theo.wizardreal.match.SpellMatcher (normalize /
    // scoreAlias / containsWord / isBoundary / similarity). Keep in sync.

    private static final class Mirror {

        static float scoreAlias(String alias, String text) {
            String a = normalize(alias);
            String t = normalize(text);
            if (a.isEmpty() || t.isEmpty()) return 0f;
            if (a.equals(t)) return 1.0f;
            boolean multiWord = a.indexOf(' ') >= 0;
            if (multiWord) {
                if (t.contains(a)) return 0.95f;
            } else if (containsWord(t, a)) {
                return 0.9f;
            }
            return similarity(a, t);
        }

        static String normalize(String s) {
            if (s == null) return "";
            return s.toLowerCase(Locale.ROOT)
                    .replaceAll("[^\\p{L}\\p{N}'\\s]", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        private static boolean containsWord(String haystack, String word) {
            int idx = haystack.indexOf(word);
            while (idx >= 0) {
                boolean beforeOk = idx == 0 || isBoundary(haystack.charAt(idx - 1));
                int end = idx + word.length();
                boolean afterOk = end == haystack.length() || isBoundary(haystack.charAt(end));
                if (beforeOk && afterOk) return true;
                idx = haystack.indexOf(word, idx + 1);
            }
            return false;
        }

        private static boolean isBoundary(char c) {
            return c <= ' ' || c >= 0x2E80;
        }

        static float similarity(String a, String b) {
            int maxLen = Math.max(a.length(), b.length());
            if (maxLen == 0) return 1f;
            return 1.0f - (float) levenshtein(a, b) / maxLen;
        }

        private static int levenshtein(String a, String b) {
            int[] prev = new int[b.length() + 1];
            int[] cur = new int[b.length() + 1];
            for (int j = 0; j <= b.length(); j++) prev[j] = j;
            for (int i = 1; i <= a.length(); i++) {
                cur[0] = i;
                for (int j = 1; j <= b.length(); j++) {
                    int sub = prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                    cur[j] = Math.min(sub, Math.min(prev[j] + 1, cur[j - 1] + 1));
                }
                int[] tmp = prev;
                prev = cur;
                cur = tmp;
            }
            return prev[b.length()];
        }

        private Mirror() {}
    }

    // ---------------------------------------------------------------- wav

    /** Decode a 16 kHz mono 16-bit PCM wav into short samples (ffmpeg already
     * normalized the file; this reader accepts the bench canonical format). */
    private static short[] readWavMono16k(Path path) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(path.toFile())) {
            javax.sound.sampled.AudioFormat format = in.getFormat();
            if (format.getSampleRate() != 16000f || format.getChannels() != 1
                    || format.getSampleSizeInBits() != 16) {
                System.err.println("wav must be 16 kHz mono PCM16: " + path);
                return null;
            }
            byte[] bytes = in.readAllBytes();
            short[] out = new short[bytes.length / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (short) ((bytes[2 * i] & 0xFF) | (bytes[2 * i + 1] << 8));
            }
            return out;
        } catch (Exception e) {
            System.err.println("wav read failed: " + path + " (" + e + ")");
            return null;
        }
    }

    private EngineBench() {}
}
