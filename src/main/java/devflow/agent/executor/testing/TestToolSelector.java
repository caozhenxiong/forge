package devflow.agent.executor.testing;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.context.ExecutionContract;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectType;

/**
 * 统一维护“测试阶段该用什么工具执行”的确定性策略。
 *
 * <p>它只做工具选择和不可执行原因归类，不负责：
 * 1. 真正运行 testcase；
 * 2. 渲染测试报告；
 * 3. 推进流程到下一阶段。
 */
class TestToolSelector {

    TestToolSelection select(
            ProjectFingerprint fingerprint,
            ExecutionContract executionContract,
            ArchitectIntegrationCheckResult architectCheck
    ) {
        if (architectCheck != null && !architectCheck.passed()) {
            return unavailable(
                    mapArchitectFailureReason(architectCheck.failureReason(), executionContract),
                    "当前交付物未通过整体可运行检查，测试用例无法执行。",
                    architectCheck.details() == null || architectCheck.details().isBlank()
                            ? "架构师整体检查未通过。"
                            : architectCheck.details()
            );
        }
        if (supportsPlaywright(fingerprint)) {
            return new TestToolSelection(
                    TestExecutionTool.PLAYWRIGHT,
                    null,
                    null,
                    null,
                    fingerprint.resolvedHtmlEntryPath()
            );
        }
        TestToolFailureReason failureReason = resolveExecutionContractFailureReason(fingerprint, executionContract);
        String details = failureReason == TestToolFailureReason.UNSUPPORTED_EXECUTOR
                ? "当前技术栈尚未实现专用 testcase 执行器，测试用例未执行。"
                : "当前交付物未满足 execution contract，测试用例无法执行。";
        String evidence = evidenceForFailureReason(failureReason);
        return unavailable(failureReason, details, evidence);
    }

    private boolean supportsPlaywright(ProjectFingerprint fingerprint) {
        return fingerprint != null
                && (fingerprint.projectTypeEnum() == ProjectType.WEB_STATIC
                || fingerprint.projectTypeEnum() == ProjectType.WEB_APP)
                && fingerprint.hasResolvedHtmlEntry();
    }

    private TestToolSelection unavailable(TestToolFailureReason failureReason, String details, String evidence) {
        return new TestToolSelection(
                TestExecutionTool.UNAVAILABLE,
                failureReason,
                details,
                evidence,
                null
        );
    }

    private TestToolFailureReason mapArchitectFailureReason(
            ArchitectIntegrationFailureReason failureReason,
            ExecutionContract executionContract
    ) {
        if (failureReason == null) {
            return resolveExecutionContractFailureReason(null, executionContract);
        }
        if (failureReason == ArchitectIntegrationFailureReason.ENTRY_MISSING) {
            return TestToolFailureReason.ENTRY_MISSING;
        }
        if (failureReason == ArchitectIntegrationFailureReason.SURFACE_MISSING) {
            return TestToolFailureReason.SURFACE_MISSING;
        }
        if (failureReason == ArchitectIntegrationFailureReason.IMPLEMENTATION_INCOMPLETE) {
            return TestToolFailureReason.IMPLEMENTATION_INCOMPLETE;
        }
        return TestToolFailureReason.EXECUTION_CONTRACT_UNMET;
    }

    private TestToolFailureReason resolveExecutionContractFailureReason(ProjectFingerprint fingerprint, ExecutionContract executionContract) {
        if (executionContract == null || !executionContract.entryRequired()) {
            return TestToolFailureReason.UNSUPPORTED_EXECUTOR;
        }
        if (fingerprint == null) {
            return TestToolFailureReason.EXECUTION_CONTRACT_UNMET;
        }
        if (executionContract.requiresHtmlEntry() && !fingerprint.hasResolvedHtmlEntry()) {
            return TestToolFailureReason.ENTRY_MISSING;
        }
        if (executionContract.launchRequired() && !fingerprint.hasResolvedHtmlEntry() && executionContract.requiresHtmlEntry()) {
            return TestToolFailureReason.LAUNCH_UNAVAILABLE;
        }
        if (executionContract.surfaceRequired() && executionContract.requiresHtmlEntry() && !fingerprint.hasResolvedHtmlEntry()) {
            return TestToolFailureReason.SURFACE_MISSING;
        }
        return TestToolFailureReason.EXECUTION_CONTRACT_UNMET;
    }

    private String evidenceForFailureReason(TestToolFailureReason failureReason) {
        if (failureReason == TestToolFailureReason.ENTRY_MISSING) {
            return "缺少可解析的入口，无法启动交付物。";
        }
        if (failureReason == TestToolFailureReason.LAUNCH_UNAVAILABLE) {
            return "存在入口要求，但当前交付物无法满足启动契约。";
        }
        if (failureReason == TestToolFailureReason.SURFACE_MISSING) {
            return "存在表面要求，但当前交付物缺少可观察运行表面。";
        }
        if (failureReason == TestToolFailureReason.EXECUTION_CONTRACT_UNMET) {
            return "执行契约未满足，无法进入 testcase 执行。";
        }
        return "当前技术栈没有匹配的 testcase 执行器。";
    }
}
