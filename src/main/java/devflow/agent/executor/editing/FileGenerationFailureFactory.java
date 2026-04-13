package devflow.agent.executor.editing;
import devflow.agent.executor.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureExceptions;
import devflow.agent.executor.generation.GenerationFailureType;

import java.nio.file.Path;

/**
 * 统一装配文件生成链的失败异常。
 */
public final class FileGenerationFailureFactory {

    public GenerationFailureException create(
            Path relativePath,
            DeliveryMode deliveryMode,
            String strategy,
            int attempts,
            GenerationFailureType failureType,
            String evidence,
            String retryHint
    ) {
        String mode = deliveryMode == null ? DeliveryMode.INCREMENTAL.name() : deliveryMode.name();
        String summary = failureSummary(relativePath, failureType);
        return GenerationFailureExceptions.create(
                relativePath.toString(),
                mode,
                strategy,
                failureType,
                attempts,
                true,
                summary,
                evidence == null ? "" : evidence,
                retryHint == null ? "" : retryHint
        );
    }

    /**
     * 显式分支避免编译器生成额外的 switch 合成类。
     *
     * <p>这类工厂在测试与 CLI 中都会频繁命中，继续依赖 `$1` 合成类会让增量编译和热替换更脆弱。
     */
    private String failureSummary(Path relativePath, GenerationFailureType failureType) {
        if (failureType == GenerationFailureType.ATTEMPT_TIMEOUT) {
            return relativePath + " 的生成尝试超时，当前结果不可用。";
        }
        if (failureType == GenerationFailureType.OUTPUT_TRUNCATED) {
            return relativePath + " 的模型输出被截断或未完整返回。";
        }
        if (failureType == GenerationFailureType.MODEL_OUTPUT_INVALID) {
            return relativePath + " 的模型返回不符合编辑协议。";
        }
        if (failureType == GenerationFailureType.SNAPSHOT_STALE) {
            return relativePath + " 的编辑请求使用了过期快照。";
        }
        if (failureType == GenerationFailureType.TARGET_SCOPE_VIOLATION) {
            return relativePath + " 的精确改写越过了当前 edit unit 的边界。";
        }
        if (failureType == GenerationFailureType.TARGET_NOT_FOUND) {
            return relativePath + " 的精确改写目标符号未匹配到现有代码。";
        }
        if (failureType == GenerationFailureType.TARGET_NOT_UNIQUE) {
            return relativePath + " 的编辑目标在当前内容中不是唯一命中。";
        }
        if (failureType == GenerationFailureType.SYNTAX_INVALID) {
            return relativePath + " 的改写结果未通过 tree-sitter 解析。";
        }
        if (failureType == GenerationFailureType.NO_MATERIAL_CHANGE) {
            return relativePath + " 的编辑结果没有产生实际内容变化。";
        }
        if (failureType == GenerationFailureType.MODEL_INVOCATION_FAILED) {
            return relativePath + " 的模型调用失败，当前结果不可用。";
        }
        return relativePath + " 的生成结果未通过本地结构校验。";
    }
}
