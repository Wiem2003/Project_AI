package pi.integrated.course.rag;

/** Video timestamps as shown to students: mm:ss under one hour, hh:mm:ss above. */
public final class TimestampFormat {

    private TimestampFormat() {
    }

    public static String format(double seconds) {
        long total = (long) Math.floor(Math.max(0, seconds));
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);
    }

    /**
     * Parses "SS", "MM:SS", "H:MM:SS" (optionally with ".fraction") or a plain number of seconds.
     *
     * @throws IllegalArgumentException if the value is not a timestamp
     */
    public static double parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("empty timestamp");
        }
        String[] parts = value.trim().split(":");
        if (parts.length > 3) {
            throw new IllegalArgumentException("invalid timestamp: " + value);
        }
        double seconds = 0;
        for (String part : parts) {
            if (part.trim().startsWith("-")) {
                throw new IllegalArgumentException("negative timestamp: " + value);
            }
            double v;
            try {
                v = Double.parseDouble(part.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("invalid timestamp: " + value);
            }
            if (v < 0 || !Double.isFinite(v)) {
                throw new IllegalArgumentException("invalid timestamp: " + value);
            }
            seconds = seconds * 60 + v;
        }
        return seconds;
    }

    /** Whole seconds for the player's ?t= parameter. */
    public static long toSeconds(double seconds) {
        return (long) Math.floor(Math.max(0, seconds));
    }
}
