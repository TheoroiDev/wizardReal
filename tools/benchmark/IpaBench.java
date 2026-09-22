import com.theo.voicecast.api.RecognitionDiagnostics;
import com.theo.voicecast.api.RecognitionResult;
import com.theo.voicecast.api.SessionVocabulary;
import com.theo.voicecast.api.SpeechOptions;
import com.theo.voicecast.engine.ZipaPhonemeRecognizer;
import com.theo.voicecast.model.Json;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-chain IPA bench for the wizardreal backtest tools (SS2 tool A/B
 * backend): runs the REAL {@link ZipaPhonemeRecognizer} from the voicecast
 * jar over candidate WAVs — no lab-side LocalIpa replica, so scores always
 * match what the server would compute. 0.5.0 contract v2 (C1b): vocabulary
 * is pushed as a {@link SessionVocabulary} (IPA templates ride in the
 * entry's {@code ipa} list) and the CTC posterior map is read back through
 * {@link ZipaPhonemeRecognizer#lastDiagnostics()} — the post-margin
 * {@code templateScores} map, the same quantity the pre-v2 result contract
 * carried.
 *
 * <p>Input (JSON file, argv[0]):
 * <pre>
 * {
 *   "modelDir": "path/to/zipa-ipa",
 *   "vocabulary": [ {"id": "spell:a", "aliases": [...], "ipa": [...] } ],
 *   "items": [ {"id": "cand-1", "ipa": "ˈkændɪdət", "wav": "cand-1.wav"} ]
 * }
 * </pre>
 * Each item is scored as a one-entry vocabulary so the recognizer's CTC
 * posterior ({@code lastDiagnostics().templateScores()}) directly grades the
 * candidate template against its own TTS rendering. Output: one JSON line
 * per item {@code {"id":..., "score":..., ...}} on stdout.
 *
 * <p>Run: {@code java -cp voicecast-common-1.20.1.jar;slf4j-api.jar;tools/benchmark IpaBench input.json}
 * (no MC classes touched; the voicecast jar + slf4j alone suffice).
 */
public final class IpaBench {

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: IpaBench <input.json>");
            System.exit(2);
        }
        Map<String, Object> input = Json.parseObject(
                new String(java.nio.file.Files.readAllBytes(Path.of(args[0]))));
        Path modelDir = Path.of(Json.getString(input, "modelDir", ""));

        List<SessionVocabulary.Entry> initial = new ArrayList<>();
        for (Object o : Json.getList(input, "vocabulary")) {
            Map<String, Object> m = Json.asMap(o);
            initial.add(new SessionVocabulary.Entry(Json.getString(m, "id", "v"),
                    Json.getStringList(m, "ipa"), Json.getStringList(m, "aliases"), null, null));
        }

        ZipaPhonemeRecognizer recognizer = new ZipaPhonemeRecognizer();
        recognizer.setVocabulary(new SessionVocabulary(initial));
        recognizer.start(new SpeechOptions(true, 0.65f, modelDir.toString(), true, null));

        for (Object o : Json.getList(input, "items")) {
            Map<String, Object> item = Json.asMap(o);
            String id = Json.getString(item, "id", "item");
            String ipa = Json.getString(item, "ipa", "");
            String wav = Json.getString(item, "wav", "");
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", id);
            out.put("ipa", ipa);
            out.put("wav", wav);

            short[] pcm = readWavMono16k(Path.of(wav));
            if (pcm == null || pcm.length == 0) {
                out.put("score", 0.0);
                out.put("error", "unreadable wav");
                System.out.println(Json.write(out));
                continue;
            }

            // One-entry vocabulary: the template's own CTC posterior is the grade.
            recognizer.setVocabulary(new SessionVocabulary(List.of(
                    new SessionVocabulary.Entry(id, List.of(ipa), List.of(ipa), null, null))));
            AtomicReference<RecognitionResult> latest = new AtomicReference<>();
            CountDownLatch done = new CountDownLatch(1);
            recognizer.setResultSink(res -> {
                latest.set(res);
                done.countDown();
            });
            recognizer.acceptPcm(pcm, 0, pcm.length);
            recognizer.finishUtterance();
            boolean finished = done.await(30, TimeUnit.SECONDS);
            RecognitionResult res = latest.get();
            RecognitionDiagnostics diag = recognizer.lastDiagnostics();
            Map<String, Float> scores = diag == null ? null : diag.templateScores();
            Float score = scores == null ? null : scores.get(id);
            out.put("score", score == null ? 0.0 : score.doubleValue());
            if (!finished) out.put("error", "decode timeout");
            System.out.println(Json.write(out));
        }
        recognizer.stop();
    }

    /** Decode a 16 kHz mono 16-bit PCM wav into short samples (ffmpeg already
     * normalized the file; this reader accepts the lab's canonical format). */
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

    private IpaBench() {}
}
