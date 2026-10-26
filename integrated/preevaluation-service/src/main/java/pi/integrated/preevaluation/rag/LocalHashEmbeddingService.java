package pi.integrated.preevaluation.rag;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deterministic local embeddings when Groq embeddings are unavailable on the account.
 * Uses hashed token counts — sufficient for small pedagogical corpora.
 */
@Component
public class LocalHashEmbeddingService {

    public static final int DIMENSION = 384;

    private static final Pattern TOKEN = Pattern.compile("[a-z0-9']+");

    public float[] embed(String text) {
        float[] vec = new float[DIMENSION];
        if (text == null || text.isBlank()) {
            return vec;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        var matcher = TOKEN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            int h = Math.floorMod(token.hashCode(), DIMENSION);
            vec[h] += 1.0f;
            if (token.length() > 3) {
                int h2 = Math.floorMod((token + "#bi").hashCode(), DIMENSION);
                vec[h2] += 0.5f;
            }
        }
        normalize(vec);
        return vec;
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double na = 0.0;
        double nb = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0.0 || nb == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static void normalize(float[] vec) {
        double sum = 0.0;
        for (float v : vec) {
            sum += v * v;
        }
        if (sum == 0.0) {
            return;
        }
        float inv = (float) (1.0 / Math.sqrt(sum));
        for (int i = 0; i < vec.length; i++) {
            vec[i] *= inv;
        }
    }

    public float[] copy(float[] source) {
        return source == null ? new float[DIMENSION] : Arrays.copyOf(source, source.length);
    }
}
