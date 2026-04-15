package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import devflow.agent.protocol.ExecutionDirectiveNarrativeRenderer;
import devflow.agent.protocol.ExecutionDirectivePayload;
import devflow.agent.protocol.ExecutionDirectiveProtocol;
import devflow.agent.quality.QualityChecklist;
import devflow.agent.quality.QualityPlan;
import java.util.List;
import java.util.StringJoiner;

import devflow.agent.executor.gate.ImplementationCompletenessResult;
import devflow.agent.executor.SelfCheckResult;
/**
 * 负责子任务验证阶段的 prompt 组装。
 * 这层只拼装 reviewer 所需的结构化上下文，不执行模型调用。
 */
public final class SubtaskReviewPromptAssembler {

    String systemPrompt() {
        return """
                你是实现阶段的子任务验证器。请只根据子任务目标、验收标准、自检结果和当前文件内容判断该子任务是否已经完成。
                约束：
                1. 只能基于输入里的明确证据下结论，不要猜测运行时结果。
                2. 必须先区分“当前负责能力”和“后续负责能力”。单纯尚未实现的后续能力只能记为 deferred，不能单独作为当前子任务失败理由。
                3. 如果当前实现已经提前落入后续负责能力，或明显越过当前 capability boundary，这本身就是当前子任务失败理由，必须拒绝。
                4. 只有“未实现的后续能力”不能阻塞当前子任务；“当前子任务提前实现了后续/外部能力”必须阻塞。
                5. 只有在 DESIGN 明确定义了性能测量要求时，才可以要求补充基础性能测量。
                6. 如果要判定“性能不达标”“耗时超标”“未满足 xx ms”，必须在自检结果或输入文本里存在明确的测量数据。
                7. 如果没有测量数据，只能写“存在性能风险”或“缺少性能验证”，不能直接判定未达标。
                8. 若 DESIGN 未要求性能测量，不要因为缺少性能数据而拒绝当前子任务。
                9. changeRequest 必须可执行，优先指出具体文件、函数、变量或缺失验证。
                10. 如果代码基本正确，只是缺少验证，优先给 PATCH，不要轻易给 REWORK。
                11. 如果输入包含 repair brief，必须优先判断 Must Fix First 和 Acceptance Checks 是否已被覆盖。
                12. 如果实现仍沿着 Forbidden Directions 继续修改，必须拒绝。
                13. SKELETON 只表示可运行壳层完成，不表示行为实现完成。
                14. 如果当前文件仍保留显式占位、TODO/FIXME、空函数、空方法或 no-op 处理，而这些占位落在当前负责能力范围内，则不能通过。
                15. 如果占位或空实现只对应后续负责能力，且当前子任务本身已满足 ownedCapabilities 与 acceptanceCriteria，则当前子任务可以通过。
                16. 如果当前负责能力是行为、状态、交互、数据处理或运行时接线，而代码只完成了静态结构、表面文案切换、按钮可见性变化、示意渲染或其他表层 UI 变化，则不能视为能力已实现。
                17. 验证时优先判断“当前负责能力是否真的产生了对应行为或状态变化”，不要把仅有入口、控件、静态画面或无副作用事件处理误判为实现完成。
                18. 如果当前实现提前落入后续负责能力，或明显越过当前 capability boundary，必须在结构化 subtaskBoundary 字段里显式标记，并作为拒绝当前子任务的直接依据。
                18.1 一旦标记 boundary violation，必须同时给出 offendingPaths，且路径只能来自当前子任务的结构化 change-set。
                18.2 offendingPaths 只能填写有明确代码证据的相对路径，不允许根据 prose 猜路径。
                """;
    }

    String candidatePrompt(
            Subtask subtask,
            SelfCheckResult selfCheck,
            ImplementationCompletenessResult completenessResult,
            QualityPlan qualityPlan,
            String targetedContext,
            String performanceValidationGuidance,
            String repairVerificationContext
    ) {
        QualityChecklist checklist = qualityPlan == null ? QualityChecklist.empty() : qualityPlan.qualityChecklist();
        return """
                子任务标题：%s

                子任务目标：
                %s

                可运行里程碑：
                %s

                覆盖引用：
                %s

                当前负责能力：
                %s

                后续负责能力：
                %s

                验收标准：
                %s

                自检结果：
                - passed: %s
                - summary: %s
                - details:
                %s

                实现完整性检查：
                - passed: %s
                - summary: %s
                - evidence:
                %s

                质量清单：
                %s

                当前相关文件：
                %s

                %s

                %s
                """.formatted(
                subtask.title(),
                subtask.goal(),
                subtask.runnableMilestone(),
                joinList(safeList(subtask.coverageRefs()), "；"),
                joinList(safeList(subtask.ownedCapabilities()), "；"),
                joinList(safeList(subtask.deferredCapabilities()), "；"),
                joinList(safeList(subtask.acceptanceCriteria()), "；"),
                selfCheck.passed(),
                selfCheck.summary(),
                selfCheck.details(),
                completenessResult.passed(),
                completenessResult.summary(),
                completenessResult.evidenceMarkdown().isBlank() ? PlaceholderValues.machineNone() : completenessResult.evidenceMarkdown(),
                checklist.toMarkdown(DocumentLanguage.ZH),
                targetedContext,
                performanceValidationGuidance,
                repairVerificationContext
        );
    }

    String renderRepairVerificationContext(String feedback) {
        ExecutionDirectivePayload directives = ExecutionDirectiveProtocol.parseMerged(feedback);
        if (!Boolean.TRUE.equals(directives.repairBriefPresent())
                && !Boolean.TRUE.equals(directives.repairBriefEnforced())) {
            return "";
        }
        return ExecutionDirectiveNarrativeRenderer.renderRepairBriefVerification(feedback);
    }

    public String renderBulletList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletMachineNone();
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            joiner.add("- " + value.trim());
        }
        String rendered = joiner.toString();
        return rendered.isBlank() ? PlaceholderValues.bulletMachineNone() : rendered;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String joinList(List<String> values, String separator) {
        return String.join(separator, values);
    }
}
