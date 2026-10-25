package pi.integrated.preevaluation.rag;

import org.springframework.stereotype.Component;
import pi.integrated.preevaluation.rag.model.IndexedChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class RagVectorIndex {

    private final AtomicReference<List<IndexedChunk>> chunks = new AtomicReference<>(List.of());

    public void replaceAll(List<IndexedChunk> indexed) {
        chunks.set(List.copyOf(indexed == null ? List.of() : indexed));
    }

    public List<IndexedChunk> allChunks() {
        return chunks.get();
    }

    public int size() {
        return chunks.get().size();
    }

    public List<IndexedChunk> snapshot() {
        return new ArrayList<>(chunks.get());
    }

    public boolean isReady() {
        return !chunks.get().isEmpty();
    }

    public void clear() {
        chunks.set(Collections.emptyList());
    }
}
