package devflow.agent.executor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * implementation tool loop 的 read file state 单一状态源。
 *
 * <p>它既是运行时状态，也是可序列化快照：
 * 1. Read/Edit/Write/Delete 统一更新这里；
 * 2. continuation / retry 恢复时直接从快照回放；
 * 3. cache eviction 同时受 entry 数和内容字节数限制。
 */
final class ToolLoopReadFileStateLedger {

    private static final long DEFAULT_MAX_ENTRIES = 100L;
    private static final long DEFAULT_MAX_SIZE_BYTES = 25L * 1024L * 1024L;

    private final long maxEntries;
    private final long maxSizeBytes;
    private final LinkedHashMap<String, CoderReadFileState> entries;
    private long currentSizeBytes;

    ToolLoopReadFileStateLedger() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_SIZE_BYTES, List.of());
    }

    ToolLoopReadFileStateLedger(long maxEntries, long maxSizeBytes) {
        this(maxEntries, maxSizeBytes, List.of());
    }

    ToolLoopReadFileStateLedger(
            long maxEntries,
            long maxSizeBytes,
            List<ImplementationStateSnapshot.ReadFileStateEntry> entries
    ) {
        this.maxEntries = Math.max(1L, maxEntries);
        this.maxSizeBytes = Math.max(1L, maxSizeBytes);
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
        load(entries);
    }

    CoderReadFileState get(Path absolutePath) {
        return entries.get(key(absolutePath));
    }

    void put(Path absolutePath, CoderReadFileState state) {
        String key = key(absolutePath);
        CoderReadFileState normalizedState = Objects.requireNonNull(state);
        CoderReadFileState previous = entries.put(key, normalizedState);
        currentSizeBytes += sizeOf(normalizedState) - sizeOf(previous);
        evictIfNeeded();
    }

    void invalidate(Path absolutePath) {
        CoderReadFileState removed = entries.remove(key(absolutePath));
        currentSizeBytes -= sizeOf(removed);
    }

    long maxEntries() {
        return maxEntries;
    }

    long maxSizeBytes() {
        return maxSizeBytes;
    }

    List<ImplementationStateSnapshot.ReadFileStateEntry> snapshotEntries() {
        List<ImplementationStateSnapshot.ReadFileStateEntry> snapshot = new ArrayList<>();
        for (Map.Entry<String, CoderReadFileState> entry : entries.entrySet()) {
            CoderReadFileState value = entry.getValue();
            if (value == null) {
                continue;
            }
            snapshot.add(new ImplementationStateSnapshot.ReadFileStateEntry(
                    entry.getKey(),
                    value.content(),
                    value.timestamp(),
                    value.offset(),
                    value.limit(),
                    value.partialView()
            ));
        }
        return List.copyOf(snapshot);
    }

    private void load(List<ImplementationStateSnapshot.ReadFileStateEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        for (ImplementationStateSnapshot.ReadFileStateEntry entry : entries) {
            if (entry == null || entry.absolutePath() == null || entry.absolutePath().isBlank()) {
                continue;
            }
            put(Path.of(entry.absolutePath()), new CoderReadFileState(
                    entry.content(),
                    entry.timestamp(),
                    entry.offset(),
                    entry.limit(),
                    entry.partialView()
            ));
        }
    }

    private String key(Path absolutePath) {
        return absolutePath.toAbsolutePath().normalize().toString();
    }

    private void evictIfNeeded() {
        while ((long) entries.size() > maxEntries || currentSizeBytes > maxSizeBytes) {
            String eldestKey = entries.keySet().stream().findFirst().orElse(null);
            if (eldestKey == null) {
                return;
            }
            CoderReadFileState removed = entries.remove(eldestKey);
            currentSizeBytes -= sizeOf(removed);
        }
    }

    private long sizeOf(CoderReadFileState state) {
        if (state == null || state.content() == null) {
            return 0L;
        }
        return Math.max(1L, state.content().getBytes(StandardCharsets.UTF_8).length);
    }
}
