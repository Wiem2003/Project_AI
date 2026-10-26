package pi.integrated.course.rag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RagHashing {

    private RagHashing() {
    }

    public static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Hash of several fields joined with a separator that cannot appear by accident. */
    public static String sha256(Object... parts) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parts) {
            sb.append(p == null ? "" : p.toString()).append('\u0001');
        }
        return sha256(sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
