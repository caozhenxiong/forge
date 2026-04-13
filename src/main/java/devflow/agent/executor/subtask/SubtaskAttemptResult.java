package devflow.agent.executor.subtask;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.tools.ToolResult;

import devflow.agent.executor.generation.GenerationFailureException;

import devflow.agent.review.ReviewResult;
import java.util.List;

import devflow.agent.executor.gate.ImplementationCompletenessGateOutcome;
import devflow.agent.executor.SelfCheckResult;
/**
 * 单次子任务尝试的结构化结果。
 * 外层执行器只消费这个对象来决定 approved/retry/recovery，不再自己拼一组原子引用。
 */
public record SubtaskAttemptResult(
        GenerationFailureException generationFailure,
        SelfCheckResult selfCheck,
        List<ToolResult> selfCheckToolResults,
        ImplementationCompletenessGateOutcome completenessOutcome,
        ReviewResult verification,
        SubtaskRevisionDirective revisionDirective
) {
}
