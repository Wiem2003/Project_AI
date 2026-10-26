package pi.integrated.course.rag;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Checks, without any LLM call, that the quote ({@code evidence}) given by the model for a quiz answer really
 * comes from the cited passage. Both texts are normalised (NFKC, lower case, typographic quotes unified,
 * punctuation removed, spaces collapsed); the quote is accepted when at least 80 % of its words appear, in order,
 * in one contiguous stretch of the passage.
 */
public final class EvidenceMatcher {

    public static final int MIN_WORDS = 5;
    public static final int MAX_WORDS = 40;
    static final double MIN_MATCH_RATIO = 0.8;

    private EvidenceMatcher() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[‘’‚‛′´`]", "'")
                .replaceAll("[“”„‟″«»]", "\"")
                .replaceAll("\\p{P}", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static List<String> words(String text) {
        String n = normalize(text);
        return n.isEmpty() ? List.of() : Arrays.asList(n.split(" "));
    }

    public static boolean hasValidLength(String evidence) {
        int n = words(evidence).size();
        return n >= MIN_WORDS && n <= MAX_WORDS;
    }

    /** True when the quote is found in the passage, exactly or with at least 80 % of its words in sequence. */
    public static boolean isSupported(String evidence, String passage) {
        List<String> quote = words(evidence);
        List<String> text = words(passage);
        if (quote.isEmpty() || text.isEmpty()) {
            return false;
        }
        if ((" " + String.join(" ", text) + " ").contains(" " + String.join(" ", quote) + " ")) {
            return true;
        }
        int needed = (int) Math.ceil(quote.size() * MIN_MATCH_RATIO);
        // A contiguous stretch slightly longer than the quote, so a few changed or inserted words are tolerated.
        int window = quote.size() + Math.max(2, quote.size() / 4);
        for (int start = 0; start < text.size(); start++) {
            List<String> stretch = text.subList(start, Math.min(text.size(), start + window));
            if (longestCommonSubsequence(quote, stretch) >= needed) {
                return true;
            }
            if (start + window >= text.size()) {
                break;
            }
        }
        return false;
    }

    private static int longestCommonSubsequence(List<String> a, List<String> b) {
        int[] prev = new int[b.size() + 1];
        int[] cur = new int[b.size() + 1];
        for (String x : a) {
            for (int j = 1; j <= b.size(); j++) {
                cur[j] = x.equals(b.get(j - 1)) ? prev[j - 1] + 1 : Math.max(prev[j], cur[j - 1]);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.size()];
    }
}
