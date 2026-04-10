package devflow.agent.repair;

/**
 * 统一承载 diagnosis 可补充读取的最新测试证据。
 *
 * <p>Diagnosis 不应只看 review summary/changeRequest，
 * 还应直接看到最近一次测试运行、运行时快照和测试报告。
 */
record DiagnosisArtifactEvidence(
        String testRuntimeSnapshot,
        String testExecution,
        String testReport
) {

    static DiagnosisArtifactEvidence empty() {
        return new DiagnosisArtifactEvidence("", "", "");
    }
}
