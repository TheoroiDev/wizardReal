import com.theo.voicecast.api.RecognitionDiagnostics;
import com.theo.voicecast.api.RecognitionResult;
import com.theo.voicecast.api.SessionVocabulary;
import com.theo.voicecast.api.SpeechOptions;
import com.theo.voicecast.api.engine.EngineSpec;
import com.theo.voicecast.audio.NoiseSuppression;
import com.theo.voicecast.engine.SherpaQwen3Recognizer;
import com.theo.voicecast.engine.ZipaPhonemeRecognizer;
import com.theo.voicecast.model.Json;
import com.theo.voicecast.model.ModelConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interactive REAL-TIME backtest (tools/benchmark): shows a random spell
 * alias / chant line, the user speaks it, and the REAL voicecast recognizers
 * grade the attempt with the same Mirror scorer as EngineBench.
 *
 * <p>Two input modes:
 * <ul>
 *   <li>{@code --replay} (default): the attempt is a random existing take
 *       from the persistent audio store (audio/clean/&lt;backend&gt;/) — the
 *       debug harness, no microphone needed;</li>
 *   <li>{@code --mic}: push-to-talk capture via Java Sound (Enter starts,
 *       Enter stops), 16 kHz mono PCM16 with 44.1/48 kHz fallback resample.</li>
 * </ul>
 *
 * <p>Console commands between rounds: {@code skip} (re-pick), {@code stats},
 * {@code engine <id-substring>} (switch active engine), {@code langs} (list),
 * {@code quit}. {@code --auto N} runs N replay rounds without input (the
 * non-interactive verification path).
 *
 * <p>Grading mirrors production SpellMatcher + the EngineBench Mirror
 * (threshold 0.65, phonetic layer) — keep the Mirror in sync with both.
 */
public class LiveBench {
    private static final float MATCH_THRESHOLD = 0.65f;
    private static final float IPA_THRESHOLD = 0.85f;
    private static final int SR = 16_000;

    private static NoiseSuppression SHARED_NS;

    // ---- item model -------------------------------------------------------

    record Item(String spell, String lang, String alias, String kind, String ipa) {}

    private static final class Stats {
        final Map<String, int[]> byEngine = new LinkedHashMap<>();
        final Map<String, int[]> byLang = new LinkedHashMap<>();
        void add(String engine, String lang, boolean pass) {
            byEngine.computeIfAbsent(engine, k -> new int[2])[1]++;
            if (pass) byEngine.get(engine)[0]++;
            byLang.computeIfAbsent(lang, k -> new int[2])[1]++;
            if (pass) byLang.get(lang)[0]++;
        }
        String line() {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, int[]> e : byEngine.entrySet()) {
                int[] c = e.getValue();
                sb.append(e.getKey()).append(": ").append(c[0]).append('/').append(c[1]).append("  ");
            }
            sb.append("| by lang: ");
            for (Map.Entry<String, int[]> e : byLang.entrySet()) {
                int[] c = e.getValue();
                sb.append(e.getKey()).append('=').append(c[0]).append('/').append(c[1]).append("  ");
            }
            return sb.toString();
        }
    }

    // ---- main -------------------------------------------------------------

    private static final List<Item> ITEMS = new ArrayList<>();
    private static final List<EngineSpec> ENGINES = new ArrayList<>();
    private static final Stats STATS = new Stats();
    private static EngineSpec active;
    private static SpeechRecognizerHolder holder;
    private static boolean replayMode = true;
    private static boolean denoise;
    private static Path audioDir;
    private static final Random RNG = new Random();
    private static final PrintStream OUT = out();
    private static final BufferedReader IN = in();

    public static void main(String[] args) throws Exception {
        String spellsDir = arg(args, "--spells-dir", "../../wizardreal/common/src/main/resources/data/wizardreal/voicecast/spells");
        String catalogPath = arg(args, "--catalog", "");
        String modelsRoot = arg(args, "--models-root", "");
        String langsArg = arg(args, "--langs", "en,zh,ja,ko");
        String enginesArg = arg(args, "--engines", "");
        audioDir = Path.of(arg(args, "--audio-dir", "audio"));
        replayMode = !has(args, "--mic");
        denoise = has(args, "--denoise");
        String g2pCache = arg(args, "--g2p-cache", "out/g2p_cache.json");
        long seed = Long.parseLong(arg(args, "--seed", "0"));
        int auto = Integer.parseInt(arg(args, "--auto", "0"));
        int n = Integer.parseInt(arg(args, "--n", "0"));
        RNG.setSeed(seed);
        if (!catalogPath.isBlank()) {
            loadCatalog(Path.of(catalogPath), Path.of(modelsRoot), enginesArg);
        }
        if (ENGINES.isEmpty()) {
            OUT.println("no engines resolved — pass --catalog/--models-root");
            return;
        }
        loadSpells(Path.of(spellsDir), langsArg, Path.of(g2pCache));
        if (ITEMS.isEmpty()) {
            OUT.println("no items — check --spells-dir/--langs");
            return;
        }
        if (denoise) {
            Path gameDir = Path.of(arg(args, "--game-dir",
                    Path.of("").toAbsolutePath().getParent().getParent() + "/wizardreal/fabric/run"));
            SHARED_NS = NoiseSuppression.create(gameDir, ModelConfig.load(gameDir));
            OUT.println("denoise-in-the-loop: " + (SHARED_NS != null ? "gtcrn active" : "unavailable"));
        }
        OUT.println("engines: " + ENGINES.stream().map(EngineSpec::engineId).toList());
        OUT.println("items: " + ITEMS.size() + "  mode: " + (replayMode ? "REPLAY (audio store)" : "LIVE MIC"));
        active = ENGINES.get(0);
        OUT.println("active engine: " + active.engineId() + "  (commands: skip | stats | engine <id> | quit)");

        int rounds = auto > 0 ? auto : n;
        int done = 0;
        while (rounds <= 0 || done < rounds) {
            Item it = pick();
            if (it == null) {
                OUT.println("no eligible items (lang/engine/take filters) — done");
                break;
            }
            String[] promptLines = {
                    "---- round " + (done + 1) + " ----",
                    "[" + active.engineId() + "][" + it.lang() + "] " + it.kind() + "  spell: " + it.spell(),
                    ">>> SPEAK: " + it.alias()
            };
            for (String l : promptLines) OUT.println(l);

            Attempt a;
            if (replayMode) {
                a = replay(it);
            } else {
                if (auto > 0) break; // auto mode is replay-only
                OUT.println("(press ENTER to start recording, ENTER again to stop)");
                IN.readLine();
                a = recordAndRecognize(it);
                OUT.println("(recording stopped)");
            }
            grade(it, a);
            done++;

            if (auto > 0) continue;
            OUT.println("[skip | stats | engine <id> | quit] ENTER = next round");
            String cmd = IN.readLine();
            if (cmd == null) break;
            cmd = cmd.trim().toLowerCase(Locale.ROOT);
            if (cmd.equals("quit") || cmd.equals("q")) break;
            if (cmd.equals("stats")) OUT.println(STATS.line());
            if (cmd.startsWith("engine ")) switchEngine(cmd.substring(7).trim());
        }
        OUT.println("---- session ----");
        OUT.println(STATS.line());
    }

    private static void switchEngine(String needle) {
        for (EngineSpec e : ENGINES) {
            if (e.engineId().contains(needle)) {
                active = e;
                holder = null; // rebuild lazily
                OUT.println("active engine: " + active.engineId());
                return;
            }
        }
        OUT.println("no engine matching '" + needle + "'");
    }

    // ---- item loading ------------------------------------------------------

    private static void loadSpells(Path dir, String langsArg, Path g2pCache) throws Exception {
        List<String> langs = List.of(langsArg.split(","));
        Map<String, String> g2p = Files.exists(g2pCache)
                ? parseFlatJson(Files.readString(g2pCache, StandardCharsets.UTF_8))
                : Map.of();
        try (var files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Map<String, Object> spell = Json.parseObject(Files.readString(f, StandardCharsets.UTF_8));
                String id = Json.getString(spell, "id", f.getFileName().toString());
                Map<String, Object> trig = Json.getMap(spell, "trigger");
                Map<String, Object> trigLangs = Json.getMap(trig, "languages");
                for (String lang : langs) {
                    for (Object o : Json.getList(trigLangs, lang)) {
                        add(id, lang, String.valueOf(o), "alias", g2p);
                    }
                }
                Map<String, Object> chants = Json.getMap(spell, "chants");
                Map<String, Object> chantLangs = Json.getMap(chants, "languages");
                for (String lang : langs) {
                    Map<String, Object> groups = Json.getMap(chantLangs, lang);
                    if (groups.isEmpty()) continue;
                    for (String role : List.of("trigger", "cast")) {
                        for (Object o : Json.getList(Json.getMap(groups, role), "aliases")) {
                            add(id, lang, String.valueOf(o), "chant_" + role, g2p);
                        }
                    }
                    List<Object> body = Json.getList(groups, "body");
                    for (int li = 0; li < body.size(); li++) {
                        for (Object lineObj : body.get(li) instanceof List<?> line
                                ? line : List.of()) {
                            Map<String, Object> var = Json.asMap(lineObj);
                            for (Object o : Json.getList(var, "aliases")) {
                                add(id, lang, String.valueOf(o), "chant_body" + (li + 1), g2p);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void add(String spell, String lang, String alias, String kind, Map<String, String> g2p) {
        if (alias == null || alias.isBlank()) return;
        for (Item it : ITEMS) {
            if (it.spell().equals(spell) && it.lang().equals(lang) && it.alias().equals(alias)) return;
        }
        ITEMS.add(new Item(spell, lang, alias, kind, g2p.get(lang + ":" + alias)));
    }

    /** Minimal flat-string-JSON parser via the voicecast Json helper. */
    private static Map<String, String> parseFlatJson(String text) {
        Map<String, String> out = new HashMap<>();
        Map<String, Object> root = Json.parseObject(text);
        for (Map.Entry<String, Object> e : root.entrySet()) {
            out.put(e.getKey(), e.getValue() == null ? null : String.valueOf(e.getValue()));
        }
        return out;
    }

    // ---- catalog -----------------------------------------------------------

    private static void loadCatalog(Path catalog, Path modelsRoot, String enginesArg) throws Exception {
        Map<String, Object> cat = Json.parseObject(Files.readString(catalog, StandardCharsets.UTF_8));
        for (Map.Entry<String, Object> e : Json.getMap(cat, "models").entrySet()) {
            Map<String, Object> m = Json.asMap(e.getValue());
            Map<String, Object> props = Json.getMap(m, "properties");
            String type = Json.getString(props, "type", "");
            if (!type.equals("offline") && !type.equals("ipa")) continue;
            if (!enginesArg.isBlank() && !e.getKey().contains(enginesArg)) continue;
            Path modelDir = modelsRoot.resolve(e.getKey());
            if (!Files.isDirectory(modelDir)) continue;
            Map<String, String> options = new LinkedHashMap<>();
            for (Map.Entry<String, Object> p : props.entrySet()) {
                if (p.getKey().equals("type") || p.getKey().equals("lang")) continue;
                options.put(p.getKey(), String.valueOf(p.getValue()));
            }
            List<String> langs = new ArrayList<>();
            for (Object o : Json.getList(props, "lang")) langs.add(String.valueOf(o));
            ENGINES.add(new EngineSpec(type, e.getKey(), modelDir, langs, options));
        }
    }

    // ---- round flow --------------------------------------------------------

    private record Attempt(String text, double score, String source) {}

    private static Item pick() {
        List<Item> pool = new ArrayList<>();
        for (Item it : ITEMS) {
            if (!active.languages().isEmpty() && !active.languages().contains(it.lang())) continue;
            if (replayMode && takesFor(it).isEmpty()) continue;
            pool.add(it);
        }
        return pool.isEmpty() ? null : pool.get(RNG.nextInt(pool.size()));
    }

    private static List<Path> takesFor(Item it) {
        List<Path> out = new ArrayList<>();
        for (String backend : List.of("sapi", "edge")) {
            Path p = audioDir.resolve("clean").resolve(backend).resolve(stem(it) + ".wav");
            if (Files.isRegularFile(p)) out.add(p);
        }
        return out;
    }

    /** Same stem rule as engbench.py (keep in sync): runs of non-[a-z0-9]
     *  become one '-', strip '-', truncate 32, blank → x; non-ASCII aliases
     *  append md5(alias)[0:8]. */
    private static String stem(Item it) {
        String base = (it.spell() + "::" + it.lang() + "::" + it.alias()).toLowerCase(Locale.ROOT);
        String slug = base.replaceAll("[^a-z0-9]+", "-");
        while (slug.startsWith("-")) slug = slug.substring(1);
        while (slug.endsWith("-")) slug = slug.substring(0, slug.length() - 1);
        if (slug.length() > 32) slug = slug.substring(0, 32);
        if (slug.isBlank()) slug = "x";
        boolean ascii = it.alias().chars().allMatch(c -> c < 128);
        if (!ascii) {
            try {
                byte[] d = MessageDigest.getInstance("MD5")
                        .digest(it.alias().getBytes(StandardCharsets.UTF_8));
                slug += "-" + HexFormat.of().formatHex(d).substring(0, 8);
            } catch (Exception ignored) {
            }
        }
        return slug;
    }

    private static Attempt replay(Item it) {
        List<Path> takes = takesFor(it);
        Path wav = takes.get(RNG.nextInt(takes.size()));
        short[] pcm = readWav16kMono(wav);
        if (pcm == null || pcm.length == 0) return new Attempt("", 0, "unreadable " + wav);
        OUT.println("  replay take: " + wav.getFileName());
        return recognize(it, pcm);
    }

    private static Attempt recognize(Item it, short[] pcm) {
        try {
            if (SHARED_NS != null) {
                SHARED_NS.process(pcm, 0, pcm.length);
            }
            SpeechRecognizerHolder h = ensureRecognizer();
            if (h.ipa != null) {
                if (it.ipa() == null || it.ipa().isBlank()) {
                    return new Attempt("", 0, "no g2p template for " + it.lang() + ":" + it.alias());
                }
                h.ipa.setVocabulary(new SessionVocabulary(List.of(
                        new SessionVocabulary.Entry(it.spell(), List.of(it.ipa()),
                                List.of(it.ipa()), null, null))));
                h.ipa.start(new SpeechOptions(true, 0.65f, active.modelDir().toString(), true, null));
                AtomicReference<RecognitionResult> res = new AtomicReference<>();
                CountDownLatch done = new CountDownLatch(1);
                h.ipa.setResultSink(r -> {
                    res.set(r);
                    done.countDown();
                });
                h.ipa.acceptPcm(pcm, 0, pcm.length);
                h.ipa.finishUtterance();
                done.await(30, TimeUnit.SECONDS);
                h.ipa.stop();
                RecognitionResult r = res.get();
                RecognitionDiagnostics diag = h.ipa.lastDiagnostics();
                Map<String, Float> scores = diag == null ? null : diag.templateScores();
                Float s = scores == null ? null : scores.get(it.spell());
                double score = s == null ? 0.0 : s;
                return new Attempt(r == null ? "" : r.utteranceText(), score, "ipa-template");
            }
            h.text.setVocabulary(vocabulary());
            h.text.start(new SpeechOptions(true, 0.65f, active.modelDir().toString(), true, null));
            AtomicReference<RecognitionResult> res = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            h.text.setResultSink(r -> {
                if (r != null && r.decision() != null) {
                    res.set(r);
                    done.countDown();
                }
            });
            // feed in 200 ms chunks like the production capture loop
            int chunk = SR / 5;
            for (int off = 0; off < pcm.length; off += chunk) {
                h.text.acceptPcm(pcm, off, Math.min(chunk, pcm.length - off));
            }
            h.text.acceptPcm(new short[9600], 0, 9600); // PTT-release right context
            h.text.finishUtterance();
            done.await(30, TimeUnit.SECONDS);
            h.text.stop();
            RecognitionResult r = res.get();
            String text = r == null ? "" : r.utteranceText();
            double score = text.isBlank() ? 0.0 : bestScore(text, it);
            return new Attempt(text, score, "mirror");
        } catch (Exception e) {
            return new Attempt("", 0, "error: " + e);
        }
    }

    /** Best alias score across the whole pool (production SpellMatcher
     *  semantics: one utterance, all spells compete). */
    private static double bestScore(String text, Item prompt) {
        double best = 0;
        for (Item it : ITEMS) {
            double s = Mirror.scoreAlias(it.alias(), text);
            if (s > best) best = s;
        }
        return best;
    }

    private static SessionVocabulary vocabulary() {
        Map<String, Map<String, List<String>>> bySpellLang = new LinkedHashMap<>();
        for (Item it : ITEMS) {
            bySpellLang.computeIfAbsent(it.spell(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(it.lang(), k -> new ArrayList<>()).add(it.alias());
        }
        List<SessionVocabulary.Entry> vocab = new ArrayList<>();
        for (Map.Entry<String, Map<String, List<String>>> e : bySpellLang.entrySet()) {
            vocab.add(new SessionVocabulary.Entry(e.getKey(), List.of(), List.of(),
                    new LinkedHashMap<>(e.getValue()), null));
        }
        return new SessionVocabulary(vocab);
    }

    private static void grade(Item it, Attempt a) {
        boolean pass = a.score() >= (active.type().equals("ipa") ? IPA_THRESHOLD : MATCH_THRESHOLD);
        String heard = a.text().isBlank() ? "(nothing)" : a.text();
        OUT.println("  heard: " + heard + "   score=" + String.format(Locale.ROOT, "%.2f", a.score())
                + "  -> " + (pass ? "PASS" : "FAIL") + "   [" + a.source() + "]");
        STATS.add(active.engineId(), it.lang(), pass);
    }

    // ---- recognizer holder -------------------------------------------------

    private static final class SpeechRecognizerHolder {
        SherpaQwen3Recognizer text;
        ZipaPhonemeRecognizer ipa;
    }

    private static SpeechRecognizerHolder ensureRecognizer() throws Exception {
        if (holder != null) return holder;
        holder = new SpeechRecognizerHolder();
        switch (active.type()) {
            case "offline" -> holder.text = new SherpaQwen3Recognizer(active);
            case "ipa" -> holder.ipa = new ZipaPhonemeRecognizer();
            default -> throw new IllegalStateException("unsupported type " + active.type());
        }
        return holder;
    }

    // ---- mic capture -------------------------------------------------------

    private static Attempt recordAndRecognize(Item it) throws Exception {
        AudioFormat target = new AudioFormat(SR, 16, 1, true, false);
        AudioFormat nativeFormat = target;
        if (!AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, target))) {
            for (float rate : new float[]{48_000f, 44_100f}) {
                AudioFormat f = new AudioFormat(rate, 16, 1, true, false);
                if (AudioSystem.isLineSupported(new DataLine.Info(TargetDataLine.class, f))) {
                    nativeFormat = f;
                    break;
                }
            }
        }
        TargetDataLine line = (TargetDataLine) AudioSystem.getLine(
                new DataLine.Info(TargetDataLine.class, nativeFormat));
        line.open(nativeFormat);
        line.start();
        var bytes = new java.io.ByteArrayOutputStream();
        Thread t = new Thread(() -> {
            byte[] buf = new byte[3200];
            while (line.isOpen()) {
                int r = line.read(buf, 0, buf.length);
                if (r <= 0) break;
                synchronized (bytes) {
                    bytes.write(buf, 0, r);
                }
            }
        }, "LiveBench-Mic");
        t.setDaemon(true);
        t.start();
        IN.readLine(); // blocking stop
        line.stop();
        line.close();
        byte[] data;
        synchronized (bytes) {
            data = bytes.toByteArray();
        }
        short[] pcm;
        if (nativeFormat.getSampleRate() == SR) {
            pcm = new short[data.length / 2];
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short) ((data[2 * i] & 0xFF) | (data[2 * i + 1] << 8));
            }
        } else {
            int nIn = data.length / 2;
            short[] in = new short[nIn];
            for (int i = 0; i < nIn; i++) {
                in[i] = (short) ((data[2 * i] & 0xFF) | (data[2 * i + 1] << 8));
            }
            double ratio = nativeFormat.getSampleRate() / (double) SR;
            pcm = new short[(int) (nIn / ratio)];
            for (int i = 0; i < pcm.length; i++) {
                double src = i * ratio;
                int i0 = (int) src;
                double frac = src - i0;
                short a = in[Math.min(i0, nIn - 1)];
                short b = in[Math.min(i0 + 1, nIn - 1)];
                pcm[i] = (short) Math.round(a + (b - a) * frac);
            }
        }
        if (pcm.length < SR / 4) return new Attempt("", 0, "recording too short");
        return recognize(it, pcm);
    }

    // ---- misc --------------------------------------------------------------

    private static short[] readWav16kMono(Path path) {
        try (var in = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat f = in.getFormat();
            if (f.getSampleRate() != SR || f.getChannels() != 1 || f.getSampleSizeInBits() != 16) {
                OUT.println("  wav must be 16 kHz mono PCM16: " + path);
                return null;
            }
            byte[] bytes = in.readAllBytes();
            short[] out = new short[bytes.length / 2];
            for (int i = 0; i < out.length; i++) {
                out[i] = (short) ((bytes[2 * i] & 0xFF) | (bytes[2 * i + 1] << 8));
            }
            return out;
        } catch (Exception e) {
            OUT.println("  wav read failed: " + path + " (" + e + ")");
            return null;
        }
    }

    private static boolean has(String[] args, String flag) {
        for (String a : args) if (a.equals(flag)) return true;
        return false;
    }

    private static String arg(String[] args, String name, String dflt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) return args[i + 1];
        }
        return dflt;
    }

    private static PrintStream out() {
        try {
            return new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        } catch (Exception e) {
            return System.out;
        }
    }

    private static BufferedReader in() {
        return new BufferedReader(new InputStreamReader(new FileInputStream(FileDescriptor.in),
                StandardCharsets.UTF_8));
    }

    // ---- Mirror (keep in sync with wizardreal SpellMatcher/Phonetics and
    // EngineBench.Mirror) ----------------------------------------------------

    static final class Mirror {
        private Mirror() {}

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
            return Math.max(similarity(a, t), phoneticScore(a, t));
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

        static float phoneticScore(String a, String b) {
            String la = latinize(a);
            String lb = latinize(b);
            float full = similarity(la, lb);
            List<String> ta = tokenize(la);
            List<String> tb = tokenize(lb);
            if (ta.isEmpty() || tb.isEmpty()) return full;
            List<String> concat = new ArrayList<>(tb);
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
                // ICU is not bundled in the standalone voicecast jar (MC ships
                // it); resolve reflectively so the bench compiles from the jar
                // alone and upgrades when icu is on the classpath.
                Class<?> tz = Class.forName("com.ibm.icu.text.Transliterator");
                Object t = tz.getMethod("getInstance", String.class)
                        .invoke(null, "Any-Latin; Latin-ASCII");
                return ((String) tz.getMethod("transform", String.class).invoke(t, s))
                        .toLowerCase(Locale.ROOT);
            } catch (Throwable e) {
                return s.toLowerCase(Locale.ROOT); // production Phonetics fallback path
            }
        }

        static List<String> tokenize(String s) {
            List<String> out = new ArrayList<>();
            for (String t : s.split("[^\\p{L}\\p{N}']+")) {
                if (!t.isBlank()) out.add(t);
            }
            return out;
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
            String skel = stripVowels(token);
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
    }
}
