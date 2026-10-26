package pi.integrated.course.rag;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Stores float vectors as compact little-endian float32 BLOBs. */
public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    public static byte[] toBytes(float[] vector) {
        ByteBuffer buf = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        buf.asFloatBuffer().put(vector);
        return buf.array();
    }

    public static float[] fromBytes(byte[] bytes) {
        float[] vector = new float[bytes.length / Float.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vector);
        return vector;
    }
}
