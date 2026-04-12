package devflow.agent.executor;

record ImplementationToolResultMessage(
        String toolUseId,
        String toolName,
        String content,
        int maxResultSizeChars
) {
}
