package pi.integrated.course.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ChunkingHelpersTest {

    @Test
    void textChunkerCutsOnSentencesAndParagraphs() {
        List<String> chunks = TextChunker.split(
                "First sentence here. Second one!\n\nNew paragraph that is quite long indeed.", 40);

        assertEquals(List.of("First sentence here. Second one!", "New paragraph that is quite long indeed."), chunks);
    }

    @Test
    void textChunkerCutsAnOverlongSentenceOnWords() {
        List<String> chunks = TextChunker.split("one two three four five six seven eight", 10);

        assertTrue(chunks.stream().allMatch(c -> c.length() <= 10), chunks.toString());
        assertEquals("one two three four five six seven eight", String.join(" ", chunks));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/watch?list=PL1&v=dQw4w9WgXcQ&t=42",
            "https://youtu.be/dQw4w9WgXcQ?t=42",
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ"
    })
    void extractsYoutubeVideoId(String url) {
        assertEquals(Optional.of("dQw4w9WgXcQ"), YoutubeUrls.extractVideoId(url));
    }

    @Test
    void rejectsNonYoutubeUrls() {
        assertTrue(YoutubeUrls.extractVideoId("https://vimeo.com/12345678").isEmpty());
        assertTrue(YoutubeUrls.extractVideoId(null).isEmpty());
    }

    @Test
    void embeddingCodecRoundTrip() {
        float[] v = {0.5f, -1.25f, 3f, Float.MIN_VALUE};
        assertArrayEquals(v, EmbeddingCodec.fromBytes(EmbeddingCodec.toBytes(v)));
    }
}
