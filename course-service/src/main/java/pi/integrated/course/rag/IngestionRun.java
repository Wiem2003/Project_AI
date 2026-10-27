package pi.integrated.course.rag;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Progress of one course re-index job (kept in memory, last run per course). */
@Getter
public class IngestionRun {

    public enum State {
        QUEUED, RUNNING, DONE, DONE_WITH_ERRORS, FAILED
    }

    private final Long courseId;
    private volatile State state = State.QUEUED;
    private final LocalDateTime queuedAt = LocalDateTime.now();
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile String message;
    private volatile int sourcesUnchanged;
    private volatile int sourcesIndexed;
    private volatile int chunksWritten;
    private volatile int chunksDeleted;
    private final List<String> errors = new ArrayList<>();

    IngestionRun(Long courseId) {
        this.courseId = courseId;
    }

    public boolean isActive() {
        return state == State.QUEUED || state == State.RUNNING;
    }

    void start() {
        state = State.RUNNING;
        startedAt = LocalDateTime.now();
    }

    synchronized void sourceUnchanged() {
        sourcesUnchanged++;
    }

    synchronized void sourceIndexed(int chunks) {
        sourcesIndexed++;
        chunksWritten += chunks;
    }

    synchronized void chunksDeleted(int n) {
        chunksDeleted += n;
    }

    synchronized void error(String error) {
        errors.add(error);
    }

    public synchronized List<String> getErrors() {
        return List.copyOf(errors);
    }

    void finish() {
        state = errors.isEmpty() ? State.DONE : State.DONE_WITH_ERRORS;
        finishedAt = LocalDateTime.now();
    }

    void fail(String reason) {
        state = State.FAILED;
        message = reason;
        finishedAt = LocalDateTime.now();
    }
}
