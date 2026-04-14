package devflow.agent.executor.llm;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devflow.ollama")
public record OllamaProperties(
        String host,
        String model,
        int timeoutSeconds,
        int connectTimeoutSeconds,
        int maxEmptyResponseRetries,
        ModelOverrides models
) {

    private static final String DEFAULT_HOST = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "qwen3-coder:30b";
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_MAX_EMPTY_RESPONSE_RETRIES = 3;

    public OllamaProperties {
        host = host == null || host.isBlank() ? DEFAULT_HOST : host;
        model = model == null || model.isBlank() ? DEFAULT_MODEL : model;
        timeoutSeconds = timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
        connectTimeoutSeconds = connectTimeoutSeconds <= 0 ? DEFAULT_CONNECT_TIMEOUT_SECONDS : connectTimeoutSeconds;
        maxEmptyResponseRetries = maxEmptyResponseRetries <= 0
                ? DEFAULT_MAX_EMPTY_RESPONSE_RETRIES
                : maxEmptyResponseRetries;
        models = models == null ? new ModelOverrides(null, null, null, null, null, null, null, null, null, null, null) : models;
    }

    public Duration connectTimeout() {
        return Duration.ofSeconds(connectTimeoutSeconds);
    }

    public String resolveModel(ModelRole role) {
        if (role == null) {
            return model;
        }
        if (role == ModelRole.ANALYSIS) {
            return choose(models.analysis());
        }
        if (role == ModelRole.PRD) {
            return choose(models.prd());
        }
        if (role == ModelRole.DESIGN) {
            return choose(models.design());
        }
        if (role == ModelRole.IMPLEMENTATION) {
            return choose(models.implementation());
        }
        if (role == ModelRole.CODE_REVIEW) {
            return choose(models.codeReview());
        }
        if (role == ModelRole.TEST) {
            return choose(models.test());
        }
        if (role == ModelRole.TEST_CASE_DESIGN) {
            return choose(models.testCaseDesign());
        }
        if (role == ModelRole.VALIDATION_STRATEGY) {
            return choose(models.validationStrategy());
        }
        if (role == ModelRole.DIAGNOSIS) {
            return choose(models.diagnosis());
        }
        if (role == ModelRole.REPAIR) {
            return choose(models.repair());
        }
        return choose(models.supervisor());
    }

    private String choose(String override) {
        return override == null || override.isBlank() ? model : override;
    }

    public record ModelOverrides(
            String analysis,
            String prd,
            String design,
            String implementation,
            String codeReview,
            String test,
            String testCaseDesign,
            String validationStrategy,
            String diagnosis,
            String repair,
            String supervisor
    ) {
    }
}
