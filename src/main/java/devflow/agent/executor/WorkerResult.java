package devflow.agent.executor;
import devflow.agent.executor.editing.*;
import devflow.agent.executor.patch.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.i18n.DocumentLanguage;
import devflow.agent.i18n.PlaceholderValues;
import java.util.List;

public record WorkerResult(
        String title,
        String status,
        boolean runnableMilestone,
        List<String> ownedFiles,
        List<String> coverageRefs,
        List<String> ownedCapabilities,
        List<String> deferredCapabilities,
        List<String> fulfilledAcceptance,
        List<String> residualRisks,
        String selfCheckSummary,
        String verifierSummary,
        String verifierChangeRequest
) {

    public String toMarkdown() {
        return toMarkdown(DocumentLanguage.EN);
    }

    public String toMarkdown(DocumentLanguage language) {
        return """
                ## %s: %s

                - %s: %s
                - %s: %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s
                %s

                ### %s

                %s

                ### %s

                %s

                ### %s

                %s
                """.formatted(
                language.choose("执行结果", "Worker Result"),
                blank(title, language),
                language.choose("状态", "status"),
                blank(status, language),
                language.choose("可运行里程碑", "Runnable Milestone"),
                runnableMilestone,
                language.choose("负责文件", "Owned Files"),
                bullets(ownedFiles, language),
                language.choose("覆盖引用", "Coverage Refs"),
                bullets(coverageRefs, language),
                language.choose("当前负责能力", "Owned Capabilities"),
                bullets(ownedCapabilities, language),
                language.choose("后续负责能力", "Deferred Capabilities"),
                bullets(deferredCapabilities, language),
                language.choose("已满足验收", "Fulfilled Acceptance"),
                bullets(fulfilledAcceptance, language),
                language.choose("残余风险", "Residual Risks"),
                bullets(residualRisks, language),
                language.choose("自检摘要", "Self Check Summary"),
                blank(selfCheckSummary, language),
                language.choose("验证摘要", "Verifier Summary"),
                blank(verifierSummary, language),
                language.choose("验证修改要求", "Verifier Change Request"),
                blank(verifierChangeRequest, language)
        ).trim();
    }

    private String bullets(List<String> values, DocumentLanguage language) {
        if (values == null || values.isEmpty()) {
            return PlaceholderValues.bulletNone(language);
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(PlaceholderValues.bulletNone(language));
    }

    private String blank(String value, DocumentLanguage language) {
        return PlaceholderValues.orNone(value, language);
    }
}
