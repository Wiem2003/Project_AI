package pi.integrated.event.rag;

import org.springframework.stereotype.Component;
import pi.integrated.event.rag.model.EventIndexedChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Index vectoriel en mémoire qui stocke tous les chunks indexés.
 * Permet la recherche par similarité cosinus.
 */
@Component
public class EventVectorIndex {

    private final AtomicReference<List<EventIndexedChunk>> chunks = new AtomicReference<>(List.of());

    /**
     * Remplace tous les chunks de l'index.
     */
    public void replaceAll(List<EventIndexedChunk> indexed) {
        chunks.set(List.copyOf(indexed == null ? List.of() : indexed));
    }

    /**
     * Retourne tous les chunks de l'index.
     */
    public List<EventIndexedChunk> allChunks() {
        return chunks.get();
    }

    /**
     * Retourne le nombre de chunks indexés.
     */
    public int size() {
        return chunks.get().size();
    }

    /**
     * Retourne une copie modifiable de tous les chunks.
     */
    public List<EventIndexedChunk> snapshot() {
        return new ArrayList<>(chunks.get());
    }

    /**
     * Vérifie si l'index est prêt (contient des chunks).
     */
    public boolean isReady() {
        return !chunks.get().isEmpty();
    }

    public void clear() {
        chunks.set(Collections.emptyList());
    }
}
