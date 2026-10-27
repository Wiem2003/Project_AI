package pi.integrated.course.rag;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimestampFormatTest {

    @ParameterizedTest
    @CsvSource({
            "0, 00:00",
            "5.9, 00:05",
            "59.99, 00:59",
            "60, 01:00",
            "252.4, 04:12",
            "3599, 59:59",
            "3600, 01:00:00",
            "3725, 01:02:05",
            "36000, 10:00:00",
            "-3, 00:00"
    })
    void formatsMmSsUnderOneHourAndHhMmSsAbove(double seconds, String expected) {
        assertEquals(expected, TimestampFormat.format(seconds));
    }

    @ParameterizedTest
    @CsvSource({
            "'00:18', 18",
            "'04:12', 252",
            "'1:02:05', 3725",
            "'01:05.4', 65.4",
            "'42', 42",
            "'75.5', 75.5"
    })
    void parsesGeminiTimestamps(String value, double expected) {
        assertEquals(expected, TimestampFormat.parse(value), 1e-9);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "ab:cd", "1:2:3:4", "-00:05"})
    void rejectsInvalidTimestamps(String value) {
        assertThrows(IllegalArgumentException.class, () -> TimestampFormat.parse(value));
    }

    @ParameterizedTest
    @CsvSource({"252.9, 252", "0.4, 0", "-1, 0"})
    void toSecondsFloorsForThePlayer(double seconds, long expected) {
        assertEquals(expected, TimestampFormat.toSeconds(seconds));
    }
}
