package devflow.agent.executor;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.parsing.HtmlStructureSnapshot;
import devflow.agent.parsing.TreeSitterSupport;
import devflow.agent.project.FileProjectWorkspace;
import devflow.agent.review.FixMode;
import devflow.agent.review.ImplementationPatchTarget;
import devflow.agent.review.ReviewDecision;
import devflow.agent.review.ReviewResult;
import devflow.agent.validation.ProjectFingerprint;
import devflow.agent.validation.ProjectInspector;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

/**
 * 在子任务级提前拦截 runtime ownership / wiring 问题。
 *
 * <p>这层只做确定性验证，不参与 reviewer 语义判断。
 * 目标是让“companion 文件已生成但入口没接线”“入口已 externalize 但仍保留完整内联主脚本”
 * 这类问题在子任务完成后立刻被打回，而不是等到最终 architect gate。
 */
final class SubtaskRuntimeWiringGuard {

    private final FileProjectWorkspace workspace;
    private final ProjectInspector projectInspector;
    private final TreeSitterSupport treeSitterSupport;
    private final WebRuntimeWiringCheck webRuntimeWiringCheck;

    SubtaskRuntimeWiringGuard(FileProjectWorkspace workspace) {
        this.workspace = workspace;
        this.projectInspector = new ProjectInspector(workspace);
        this.treeSitterSupport = new TreeSitterSupport();
        this.webRuntimeWiringCheck = new WebRuntimeWiringCheck(workspace);
    }

    SubtaskVerificationOutcome check(Path projectPath, Subtask subtask, DocumentLanguage language) {
        if (!touchesHtmlEntryRuntimeOwnership(subtask, projectPath)) {
            return null;
        }
        ProjectFingerprint fingerprint = projectInspector.inspect(projectPath);
        if (fingerprint == null || !fingerprint.hasResolvedHtmlEntry()) {
            return null;
        }
        Path htmlEntryPath = Path.of(fingerprint.resolvedHtmlEntryPath()).normalize();
        String htmlSource = workspace.readFile(projectPath, htmlEntryPath);
        HtmlStructureSnapshot snapshot = treeSitterSupport.inspectHtml(htmlSource);
        WebRuntimeWiringResult result = webRuntimeWiringCheck.inspect(projectPath, htmlEntryPath, snapshot, htmlSource);
        if (result.passed()) {
            return null;
        }
        String changeRequest = result.summary();
        String evidence = result.evidenceMarkdown();
        if (!evidence.isBlank()) {
            changeRequest = changeRequest + "\n" + evidence;
        }
        RuntimeWiringPatchDecision patchDecision = result.patchDecision();
        ReviewResult review = new ReviewResult(
                ReviewDecision.REVISION_REQUIRED,
                FixMode.PATCH,
                language.choose("当前子任务破坏了 HTML 入口与 runtime 所有权/接线契约。", "The current subtask broke the HTML runtime ownership/wiring contract."),
                changeRequest.trim(),
                evidence,
                language.choose("只修复当前入口与 companion runtime 的接线/所有权问题，不要重开整轮实现。", "Only repair the current entry/companion runtime wiring and ownership issue; do not reopen the whole implementation."),
                patchDecision == null ? ImplementationPatchTarget.PATCH_RUNTIME_WIRING : patchDecision.patchTarget()
        );
        SubtaskRevisionDirective revisionDirective = patchDecision == null || patchDecision.htmlEntryOverride() == null
                ? SubtaskRevisionDirective.patch(java.util.List.of())
                : SubtaskRevisionDirective.patch(java.util.List.of(patchDecision.htmlEntryOverride()));
        return SubtaskVerificationOutcome.of(review, revisionDirective);
    }

    private boolean touchesHtmlEntryRuntimeOwnership(Subtask subtask, Path projectPath) {
        if (subtask == null || subtask.changes() == null || subtask.changes().isEmpty()) {
            return false;
        }
        ProjectFingerprint fingerprint = projectInspector.inspect(projectPath);
        Path htmlEntryPath = fingerprint != null && fingerprint.hasResolvedHtmlEntry()
                ? Path.of(fingerprint.resolvedHtmlEntryPath()).normalize()
                : null;
        Path htmlParent = htmlEntryPath == null || htmlEntryPath.getParent() == null
                ? Path.of("")
                : htmlEntryPath.getParent().normalize();
        for (FileChange change : subtask.changes()) {
            if (change == null || change.path() == null || change.path().isBlank()) {
                continue;
            }
            Path relativePath = Path.of(change.path()).normalize();
            if (change.runtimeOwnership() != null) {
                return true;
            }
            if (htmlEntryPath != null && htmlEntryPath.equals(relativePath)) {
                return true;
            }
            if (htmlEntryPath != null
                    && ProjectPathSupport.isRuntimeScript(relativePath)
                    && isUnderHtmlEntryTree(relativePath, htmlParent)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnderHtmlEntryTree(Path candidate, Path htmlParent) {
        if (candidate == null) {
            return false;
        }
        Path candidateParent = candidate.getParent() == null ? Path.of("") : candidate.getParent().normalize();
        return htmlParent.toString().isBlank() || candidateParent.equals(htmlParent) || candidateParent.startsWith(htmlParent);
    }
}
