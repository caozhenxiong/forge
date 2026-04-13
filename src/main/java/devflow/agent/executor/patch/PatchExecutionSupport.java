package devflow.agent.executor.patch;
import devflow.agent.executor.*;
import devflow.agent.executor.editing.*;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import devflow.agent.executor.generation.GenerationFailureException;
import devflow.agent.executor.generation.GenerationFailureType;
import devflow.agent.executor.generation.GenerationObserver;

import devflow.agent.i18n.PlaceholderValues;
import java.nio.file.Path;

import devflow.agent.executor.implementation.ImplementationEventJournal;
/**
 * patch 执行链共享的运行时支撑。
 *
 * <p>宿主 HTML、宿主嵌入、代码 patch 和 whole-file 例外路径都需要：
 * 1. 统一装配 generation failure；
 * 2. 统一创建 generation observer；
 * 3. 统一生成本地验证预览摘要。
 *
 * <p>把这些公共支撑从各个执行器里拿出来后，执行器只保留各自的 patch 语义。
 */
public final class PatchExecutionSupport {

    private final FileGenerationFailureFactory fileGenerationFailureFactory;
    private final ImplementationGenerationObserverFactory implementationGenerationObserverFactory;

    public PatchExecutionSupport(
            FileGenerationFailureFactory fileGenerationFailureFactory,
            ImplementationGenerationObserverFactory implementationGenerationObserverFactory
    ) {
        this.fileGenerationFailureFactory = fileGenerationFailureFactory;
        this.implementationGenerationObserverFactory = implementationGenerationObserverFactory;
    }

    GenerationFailureException generationFailure(
            Path relativePath,
            DeliveryMode deliveryMode,
            String strategy,
            int attempts,
            GenerationFailureType failureType,
            String evidence,
            String retryHint
    ) {
        return fileGenerationFailureFactory.create(
                relativePath,
                deliveryMode,
                strategy,
                attempts,
                failureType,
                nullToEmpty(evidence),
                nullToEmpty(retryHint)
        );
    }

    GenerationObserver generationObserver(
            Path relativePath,
            String strategy,
            DeliveryMode deliveryMode,
            ImplementationEventJournal eventJournal
    ) {
        return implementationGenerationObserverFactory.create(
                relativePath,
                strategy,
                deliveryMode,
                eventJournal,
                this::summarizeForVerification
        );
    }

    public String summarizeForVerification(String content, int maxChars) {
        return PlaceholderValues.truncateMiddle(content, maxChars);
    }

    public String summarizeForRepair(String content, int maxChars) {
        return summarizeForVerification(content, maxChars);
    }

    public void appendImplementationEvent(ImplementationEventJournal eventJournal, String message) {
        if (eventJournal != null) {
            eventJournal.append(message);
        }
    }

    public String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
