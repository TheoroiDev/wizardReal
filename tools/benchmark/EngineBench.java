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
    private static final float MATCH_THRESHOLD = 0.65f;
    private static com.theo.voicecast.audio.NoiseSuppression SHARED_NS;

    /** Cross-spell alias collision audit (per language, best-of-pairs). */
    private static void auditCollisions(List<Object> items) {
        record Ent(String spell, String lang, String alias) { }
        List<Ent> ents = new ArrayList<>();
        for (Object io : items) {
            Map<String, Object> it = Json.asMap(io);
            ents.add(new Ent(Json.getString(it, "spell", ""), Json.getString(it, "lang", ""),
                    Json.getString(it, "alias", "")));
        }
        for (String lang : ents.stream().map(e -> e.lang).distinct().sorted().toList()) {
            List<Ent> pool = ents.stream().filter(e -> e.lang.equals(lang)).toList();
            float max = 0f;
            String pair = "";
            for (int i = 0; i < pool.size(); i++) {
                for (int j = i + 1; j < pool.size(); j++) {
                    Ent a = pool.get(i), b = pool.get(j);
                    if (a.spell.equals(b.spell)) continue;
                    float s = Mirror.scoreAlias(a.alias, b.alias);
                    if (s > max) {
                        max = s;
                        pair = a.alias + " <-> " + b.alias;
                    }
                }
            }
            System.out.printf("[collision audit] lang=%s maxCrossSpellSimilarity=%.2f (%s)%n",
                    lang, max, pair);
        }
    }

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
        String gameDir = Json.getString(input, "gameDir", "");
        boolean denoise = Json.getBool(input, "denoise", false); // getString returns dflt for booleans!
        if (denoise) {
            // production noiseSuppression=true config: GTCRN cleans every mic
            // frame before recognition — mirror that in the bench
            com.theo.voicecast.audio.NoiseSuppression ns = com.theo.voicecast.audio.NoiseSuppression.create(
                    Path.of(gameDir), com.theo.voicecast.model.ModelConfig.load(Path.of(gameDir)));
            SHARED_NS = ns;
            System.out.println("[EngineBench] denoise-in-the-loop: " + (ns != null ? "gtcrn active" : "unavailable (passthrough)"));
        }

        // Collision audit: max Mirror similarity between aliases of DIFFERENT
        // spells, per language. Informs the match threshold — anything above
        // it can confuse the matcher into casting the wrong spell.
        auditCollisions(items);

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
            if (SHARED_NS != null) {
                SHARED_NS.process(pcm, 0, pcm.length);
            }
            recognizer.acceptPcm(pcm, 0, pcm.length);
            // trailing silence mirrors a real PTT release (the mic keeps
            // running briefly); without it the streaming transducer drops
            // the trailing frames of the final token
            recognizer.acceptPcm(new short[9600], 0, 9600); // 600 ms right-context for the trailing token
            recognizer.finishUtterance();
            if (SHARED_NS != null) {
                SHARED_NS.reset();
            }
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
        // Hybrid language strategy (measured 20260906): auto mode is best for
        // en/zh, but ja collapses (auto cross-lingual errors). ja items run a
        // separate language=ja-pinned recognizer; everything else stays auto.
        List<Map<String, Object>> auto = new ArrayList<>();
        List<Map<String, Object>> ja = new ArrayList<>();
        for (Map<String, Object> it : items) {
            if ("ja".equals(Json.getString(it, "lang", ""))) ja.add(it);
            else auto.add(it);
        }
        if (!auto.isEmpty()) runOfflineLang(spec, auto);
        if (!ja.isEmpty()) {
            Map<String, String> options = new LinkedHashMap<>(spec.options());
            options.put("language", "ja");
            EngineSpec pinned = new EngineSpec(spec.type(), spec.engineId(),
                    spec.modelDir(), spec.languages(), options);
            runOfflineLang(pinned, ja);
        }
    }

    private static void runOfflineLang(EngineSpec spec, List<Map<String, Object>> items) throws Exception {
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
        // Language-bucketed like production (wizardreal builds per-language
        // Pronunciations; the session router only feeds the engine its own
        // buckets). The flat constructor would dump every language's aliases
        // into every engine's grammar and bias bilingual models toward CJK.
        Map<String, Map<String, Set<String>>> bySpellLang = new LinkedHashMap<>();
        for (Map<String, Object> it : items) {
            String spell = Json.getString(it, "spell", "spell");
            String lang = Json.getString(it, "lang", "en");
            String alias = Json.getString(it, "alias", "");
            if (!alias.isBlank()) {
                bySpellLang.computeIfAbsent(spell, k -> new LinkedHashMap<>())
                        .computeIfAbsent(lang, k -> new LinkedHashSet<>()).add(alias);
            }
        }
        List<Pronunciation> vocab = new ArrayList<>();
        for (Map.Entry<String, Map<String, Set<String>>> en : bySpellLang.entrySet()) {
            Map<String, List<String>> buckets = new LinkedHashMap<>();
            en.getValue().forEach((lang, aliases) -> buckets.put(lang, List.copyOf(aliases)));
            vocab.add(new Pronunciation(en.getKey(), List.of(), List.of(), buckets));
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
        out.put("kind", Json.getString(it, "kind", "alias"));
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
            // phonetic layer, mirrors production Phonetics (keep in sync)
            return Math.max(similarity(a, t), phoneticScore(a, t));
        }

        /** Phonetic similarity — mirrors production Phonetics (keep in sync). */
        static float phoneticScore(String a, String b) {
            String la = latinize(a);
            String lb = latinize(b);
            float full = similarity(la, lb);
            java.util.List<String> ta = tokenize(la);
            java.util.List<String> tb = tokenize(lb);
            if (ta.isEmpty() || tb.isEmpty()) return full;
            java.util.List<String> concat = new java.util.ArrayList<>(tb);
            for (int i = 0; i + 1 < tb.size(); i++) concat.add(tb.get(i) + tb.get(i + 1));
            float total = 0f;
            for (String x : ta) {
                float best = 0f;
                for (String y : concat) {
                    float lev = similarity(x, y);
                    float vowelless = 0f;
                    if (Math.min(x.length(), y.length()) >= 4) {
                        vowelless = similarity(stripVowels(x), stripVowels(y));
                    }
                    float meta = similarity(metaphone(x), metaphone(y));
                    best = Math.max(best, Math.max(lev, Math.max(vowelless,
                            Math.max(meta, jaccard(skeleton(x), skeleton(y))))));
                }
                total += best;
            }
            float token = total / ta.size();
            return Math.max(full, token);
        }

        static String latinize(String s) {
            if (s == null || s.isEmpty()) return "";
            try {
                com.ibm.icu.text.Transliterator t =
                        com.ibm.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII");
                return t.transform(s).toLowerCase(Locale.ROOT);
            } catch (Throwable e) {
                return s.toLowerCase(Locale.ROOT);
            }
        }

        static java.util.List<String> tokenize(String s) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String t : s.split("[^\\p{L}\\p{N}']+")) {
                if (!t.isBlank()) out.add(t);
            }
            return out;
        }

        /** Classic Metaphone key - mirrors production Phonetics.metaphone
         *  (keep in sync). */
        static String metaphone(String word) {
            String w = word.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
            int n = w.length();
            if (n == 0) return "";
            StringBuilder sb = new StringBuilder();
            int i = 0;
            if (w.startsWith("CH")) { sb.append('K'); i = 2; }
            else if (w.startsWith("PH")) { sb.append('F'); i = 2; }
            else if (w.startsWith("KN") || w.startsWith("GN") || w.startsWith("PN")
                    || w.startsWith("WR") || w.startsWith("AE")) { i = 1; }
            else if (w.startsWith("WH")) { sb.append('W'); i = 2; }
            else if (w.startsWith("X")) { sb.append('S'); i = 1; }
            else { sb.append(w.charAt(0)); i = 1; }
            while (i < n) {
                char cur = w.charAt(i);
                char prev = i > 0 ? w.charAt(i - 1) : ' ';
                char next = i + 1 < n ? w.charAt(i + 1) : ' ';
                char after = i + 2 < n ? w.charAt(i + 2) : ' ';
                if (cur == prev && cur != 'C') { i++; continue; }
                switch (cur) {
                    case 'A', 'E', 'I', 'O', 'U' -> { }
                    case 'B' -> { if (!(prev == 'M' && i == n - 1)) sb.append('B'); }
                    case 'C' -> {
                        if (next == 'H') { sb.append('X'); i++; }
                        else if (prev == 'S' && "EIY".indexOf(next) >= 0) { }
                        else sb.append('K');
                    }
                    case 'D' -> {
                        if (next == 'G' && "EIY".indexOf(after) >= 0) { sb.append('J'); i++; }
                        else sb.append('T');
                    }
                    case 'G' -> {
                        if (next == 'H') i++;
                        else if (next == 'N') { }
                        else if (prev == 'G') { }
                        else if ("EIY".indexOf(next) >= 0) sb.append('J');
                        else sb.append('K');
                    }
                    case 'H' -> { if (i == 0 || "CSPTG".indexOf(prev) < 0) sb.append('H'); }
                    case 'K' -> { if (prev != 'C') sb.append('K'); }
                    case 'P' -> { if (next == 'H') { sb.append('F'); i++; } else sb.append('P'); }
                    case 'Q' -> sb.append('K');
                    case 'S' -> { if (next == 'H') { sb.append('X'); i++; } else sb.append('S'); }
                    case 'T' -> {
                        if (next == 'I' && (after == 'O' || after == 'A')) sb.append('X');
                        else if (next == 'H') { sb.append('T'); i++; }
                        else sb.append('T');
                    }
                    case 'V' -> sb.append('F');
                    case 'W', 'Y' -> { if (isVowel(next)) sb.append(cur); }
                    case 'X' -> { sb.append('K'); sb.append('S'); }
                    case 'Z' -> sb.append('S');
                    case 'F', 'J', 'L', 'M', 'N', 'R' -> sb.append(cur);
                    default -> { }
                }
                i++;
            }
            return sb.toString();
        }

        static boolean isVowel(char c) {
            return "AEIOU".indexOf(c) >= 0;
        }

        static String stripVowels(String token) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (i == 0 || "aeiou".indexOf(c) < 0) sb.append(c);
            }
            return sb.toString();
        }

        static java.util.Set<String> skeleton(String token) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < token.length(); i++) {
                char c = token.charAt(i);
                if (i == 0 || "aeiou".indexOf(c) < 0) sb.append(c);
            }
            String skel = sb.toString();
            java.util.Set<String> grams = new java.util.HashSet<>();
            for (int i = 0; i + 1 < skel.length(); i++) grams.add(skel.substring(i, i + 2));
            if (grams.isEmpty() && !skel.isEmpty()) grams.add(skel);
            return grams;
        }

        static float jaccard(java.util.Set<String> a, java.util.Set<String> b) {
            if (a.isEmpty() || b.isEmpty()) return 0f;
            java.util.Set<String> inter = new java.util.HashSet<>(a);
            inter.retainAll(b);
            java.util.Set<String> union = new java.util.HashSet<>(a);
            union.addAll(b);
            return (float) inter.size() / union.size();
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
