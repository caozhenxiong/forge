package devflow.agent.review;

/**
 * implementation subtask review 专用的边界语义载荷。
 *
 * <p>它只描述当前子任务是否越过 capability boundary，不进入全局 ReviewResult 主协议。
 */
public record SubtaskBoundaryReviewPayload(
        boolean provided,
        boolean implementsDeferredCapabilities,
        boolean implementsForeignCapabilities,
        String summary,
        String evidence,
        String actionItems,
        java.util.List<String> offendingPaths
) {

    public SubtaskBoundaryReviewPayload {
        offendingPaths = normalizePaths(offendingPaths);
    }

    public static SubtaskBoundaryReviewPayload empty() {
        return new SubtaskBoundaryReviewPayload(false, false, false, "", "", "", java.util.List.of());
    }

    public boolean boundaryViolation() {
        return implementsDeferredCapabilities || implementsForeignCapabilities;
    }

    private static java.util.List<String> normalizePaths(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return java.util.List.of();
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(java.nio.file.Path.of(value.trim()).normalize().toString().replace('\\', '/'));
        }
        return java.util.List.copyOf(normalized);
    }
}
