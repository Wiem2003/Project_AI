package pi.integrated.course.rag;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the YouTube links stored in Lesson.videoUrl. */
public final class YoutubeUrls {

    private static final Pattern VIDEO_ID = Pattern.compile(
            "(?:youtube(?:-nocookie)?\\.com/(?:watch\\?(?:.*&)?v=|embed/|shorts/|live/|v/)|youtu\\.be/)([A-Za-z0-9_-]{11})");

    private YoutubeUrls() {
    }

    public static Optional<String> extractVideoId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher m = VIDEO_ID.matcher(url.trim());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /** Canonical watch URL, the form Gemini accepts as fileUri. */
    public static String watchUrl(String videoId) {
        return "https://www.youtube.com/watch?v=" + videoId;
    }
}
