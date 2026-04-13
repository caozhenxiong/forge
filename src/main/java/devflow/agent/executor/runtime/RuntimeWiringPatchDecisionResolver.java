package devflow.agent.executor.runtime;

import devflow.agent.executor.*;
import devflow.agent.executor.editing.FileEditScope;

import devflow.agent.review.ImplementationPatchTarget;
import java.nio.file.Path;
import java.util.List;

/**
 * 统一把 runtime wiring 检查结果收敛成 PATCH decision。
 *
 * <p>规则只看结构化 inspection/issue，不从 prose 文本推断。
 * PATCH 只允许修“既有 contract 下的接线缺口”，不能在这一层迁移运行时所有权。
 */
final class RuntimeWiringPatchDecisionResolver {

    RuntimeWiringPatchDecision resolve(
            Path htmlEntryPath,
            HtmlEntryRuntimeOwnershipInspection inspection,
            List<String> issues
    ) {
        if (inspection == null) {
            return new RuntimeWiringPatchDecision(
                    ImplementationPatchTarget.PATCH_RUNTIME_WIRING,
                    null,
                    hostHtmlOverride(htmlEntryPath, ImplementationPatchTarget.PATCH_RUNTIME_WIRING, null, issues)
            );
        }
        ImplementationPatchTarget patchTarget = ImplementationPatchTarget.PATCH_RUNTIME_WIRING;
        return new RuntimeWiringPatchDecision(
                patchTarget,
                inspection.runtimeContract(),
                hostHtmlOverride(htmlEntryPath, patchTarget, inspection.runtimeContract(), issues)
        );
    }

    private FileChange hostHtmlOverride(
            Path htmlEntryPath,
            ImplementationPatchTarget patchTarget,
            HtmlRuntimeOwnershipContract runtimeContract,
            List<String> issues
    ) {
        if (htmlEntryPath == null || issues == null || issues.isEmpty()) {
            return null;
        }
        RuntimeOwnershipMode runtimeOwnership = runtimeContract == null ? null : runtimeContract.runtimeOwnership();
        String reason = "修复宿主 HTML 与当前 runtime 的接线和宿主表面";
        return new FileChange(
                htmlEntryPath.toString().replace('\\', '/'),
                ChangeAction.WRITE,
                reason,
                FileEditScope.HOST_HTML_PATCH,
                runtimeOwnership,
                true
        );
    }
}
