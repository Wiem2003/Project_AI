package pi.integrated.course.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits free text (descriptions, PDF pages) into chunks of about {@code targetChars} characters,
 * cutting on paragraph then sentence boundaries; only a single over-long sentence is cut on words.
 */
public final class TextChunker {

    private static final Pattern PARAGRAPH = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?…])\\s+");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\r]+");

    private TextChunker() {
    }

    public static List<String> split(String text, int targetChars) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        int target = Math.max(1, targetChars);

        List<String> units = new ArrayList<>();
        for (String paragraph : PARAGRAPH.split(text.replace("\r\n", "\n"))) {
            String p = SPACES.matcher(paragraph.replace('\n', ' ')).replaceAll(" ").trim();
            if (p.isEmpty()) {
                continue;
            }
            for (String sentence : SENTENCE_END.split(p)) {
                if (sentence.length() <= target) {
                    units.add(sentence);
                } else {
                    units.addAll(splitOnWords(sentence, target));
                }
            }
        }

        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.length() > 0 && current.length() + 1 + unit.length() > target) {
                out.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(unit);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private static List<String> splitOnWords(String sentence, int target) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : sentence.split(" ")) {
            if (current.length() > 0 && current.length() + 1 + word.length() > target) {
                pieces.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }
}
