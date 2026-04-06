package devflow.agent.executor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devflow.ollama")
public record OllamaProperties(
        String host,
        String model,
        int timeoutSeconds,
        ModelOverrides models
) {

    public OllamaProperties {
        host = host == null || host.isBlank() ? "http://127.0.0.1:11434" : host;
        model = model == null || model.isBlank() ? "gemma4:26b" : model;
        timeoutSeconds = timeoutSeconds <= 0 ? 300 : timeoutSeconds;
        models = models == null ? new ModelOverrides(null, null, null, null, null, null, null, null, null, null, null) : models;
    }

    public String resolveModel(ModelRole role) {
        if (role == null) {
            return model;
        }
        return switch (role) {
            case ANALYSIS -> choose(models.analysis());
            case PRD -> choose(models.prd());
            case DESIGN -> choose(models.design());
            case IMPLEMENTATION -> choose(models.implementation());
            case CODE_REVIEW -> choose(models.codeReview());
            case TEST -> choose(models.test());
            case TEST_CASE_DESIGN -> choose(models.testCaseDesign());
            case VALIDATION_STRATEGY -> choose(models.validationStrategy());
            case DIAGNOSIS -> choose(models.diagnosis());
            case REPAIR -> choose(models.repair());
            case SUPERVISOR -> choose(models.supervisor());
        };
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
