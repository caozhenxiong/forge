package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.nio.file.Path;

/**
 * 统一渲染宿主内嵌 patch 的失败反馈。
 *
 * <p>这层只负责把结构化失败信息转成下一轮提示需要的文本，不承担路由判断。
 * 这样旧文件级编辑编排层就不需要再自己维护大量重试/终止文案。
 */
public final class EmbeddedPatchFeedbackRenderer {

    private EmbeddedPatchFeedbackRenderer() {
    }

    public static String validationRetryFeedback(
            int attempt,
            Path relativePath,
            EditUnit unit,
            EmbeddedPatchKind patchKind,
            PatchFailure patchFailure
    ) {
        return """
                - attempt: %d
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: %s工作集未通过本地校验
                要求：
                1. 继续返回 JSON exact replace %s改写
                2. 只改当前编辑单元允许的%s
                3. 不要输出 HTML，不要输出整页%s
                4. %s
                """.formatted(
                attempt,
                relativePath,
                unit.label(),
                patchKind.displayName(),
                patchKind.targetName(),
                patchKind.targetName(),
                patchKind.contentName(),
                patchFailure.recommendedNextActionOr("保持当前" + patchKind.displayName() + "工作集边界并确保结果可解析。")
        );
    }

    public static String abortFeedback(Path relativePath, EditUnit unit, EmbeddedPatchKind patchKind) {
        return """
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: 当前%s单元输出被截断
                要求：
                1. 不要继续重试同一个过大单元
                2. 由外层把当前单元拆成更小的%s批次后再继续
                """.formatted(relativePath, unit.label(), patchKind.contentName(), patchKind.targetName());
    }

    public static String truncationRetryFeedback(
            int attempt,
            Path relativePath,
            EditUnit unit,
            EmbeddedPatchKind patchKind
    ) {
        return """
                - attempt: %d
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: 当前单元输出被截断，且当前%s单元已无法继续按%s拆分
                要求：
                1. 继续只返回 JSON exact replace %s改写
                2. %s
                3. %s
                4. 不要回到整段%s或整页 HTML 重写
                """.formatted(
                attempt,
                relativePath,
                unit.label(),
                patchKind.contentName(),
                patchKind.targetName(),
                patchKind.targetName(),
                patchKind.truncationAppendGuidance(),
                patchKind.truncationRestrictedGuidance(),
                patchKind.contentName()
        );
    }

    public static String invalidJsonFeedback(
            int attempt,
            Path relativePath,
            EditUnit unit,
            EmbeddedPatchKind patchKind
    ) {
        return """
                - attempt: %d
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: 当前%s工作集返回了非法 JSON
                要求：
                1. 继续只返回 JSON 对象
                2. 必须包含 targetPath、baseContentHash、oldText、newText、replaceAll
                3. oldText 必须逐字摘自当前%s内容
                4. 不要输出 HTML、解释或 Markdown
                """.formatted(
                attempt,
                relativePath,
                unit.label(),
                patchKind.displayName(),
                patchKind.contentName()
        );
    }

    public static String patchSchemaFeedback(
            int attempt,
            Path relativePath,
            EditUnit unit,
            EmbeddedPatchKind patchKind
    ) {
        return """
                - attempt: %d
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: 当前%s patch 协议不符合单元约束
                要求：
                1. 继续只返回 JSON 对象
                2. 单符号单元只能返回覆盖当前 allowedSymbols 对应现有%s体的最小 exact replace
                3. 不要把整段%s工作集重写成大替换
                4. 只有 append-only 单元才允许通过最小尾部替换追加内容
                """.formatted(
                attempt,
                relativePath,
                unit.label(),
                patchKind.displayName(),
                patchKind.targetName(),
                patchKind.targetName()
        );
    }

    public static String scopeViolationFeedback(
            int attempt,
            Path relativePath,
            EditUnit unit,
            EmbeddedPatchKind patchKind
    ) {
        return """
                - attempt: %d
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: 当前 patch 越过了 edit unit 的边界
                要求：
                1. 只修改当前 allowedSymbols 列表中的%s
                2. 不要把 oldText 扩到当前单元以外
                3. 不要同时改写当前单元以外的%s或新增%s块
                """.formatted(
                attempt,
                relativePath,
                unit.label(),
                patchKind.targetName(),
                patchKind.targetName(),
                patchKind.contentName()
        );
    }

    public static String missingTargetFeedback(Path relativePath, EditUnit unit, EmbeddedPatchKind patchKind) {
        return """
                - 来源文件: %s
                - 编辑单元: %s
                - 问题: 当前%s工作集缺少可直接命中的%s
                要求：
                1. 保持当前 HTML 入口结构
                2. 下一步可退回 HTML %s区块级改写，但不要直接整文件重写
                """.formatted(relativePath, unit.label(), patchKind.displayName(), patchKind.targetName(), patchKind.contentName());
    }

    public static String generationFailureAdvice(EmbeddedPatchKind patchKind) {
        return "请继续只改当前编辑单元允许的" + patchKind.targetName()
                + "；返回 targetPath/baseContentHash/oldText/newText/replaceAll 结构化 exact replace；若单元仍过大，请继续拆小。";
    }
}
