package devflow.agent.quality;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;

/**
 * 质量规则加载器。
 *
 * <p>加载顺序从低到高：
 * 1. 资源级默认规则；
 * 2. 项目级 repo rule 文件。
 */
public final class QualityRulesLoader {

    private static final String DEFAULT_RULES_RESOURCE = "/devflow-default-quality-rules.properties";
    private static final String PROJECT_RULES_PATH = ".devflow/quality-rules.properties";
    private final String defaultRulesResource;

    public QualityRulesLoader() {
        this(DEFAULT_RULES_RESOURCE);
    }

    QualityRulesLoader(String defaultRulesResource) {
        this.defaultRulesResource = defaultRulesResource;
    }

    public QualityRules loadDefaults() {
        return toQualityRules(loadResourceDefaults());
    }

    public QualityRules load(Path projectPath) {
        Properties merged = loadResourceDefaults();
        mergeProjectOverrides(merged, projectPath);
        return toQualityRules(merged);
    }

    private Properties loadResourceDefaults() {
        Properties properties = new Properties();
        try (InputStream inputStream = QualityRulesLoader.class.getResourceAsStream(defaultRulesResource)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing quality rules resource: " + defaultRulesResource);
            }
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load quality rules resource: " + defaultRulesResource, exception);
        }
        return properties;
    }

    private void mergeProjectOverrides(Properties merged, Path projectPath) {
        if (projectPath == null) {
            return;
        }
        Path rulesFile = projectPath.resolve(PROJECT_RULES_PATH).normalize();
        if (!Files.isRegularFile(rulesFile)) {
            return;
        }
        Properties projectProperties = new Properties();
        try (InputStream inputStream = Files.newInputStream(rulesFile)) {
            projectProperties.load(inputStream);
            for (String key : projectProperties.stringPropertyNames()) {
                merged.setProperty(key, projectProperties.getProperty(key));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to load project quality rules: " + rulesFile, exception);
        }
    }

    private QualityRules toQualityRules(Properties properties) {
        return new QualityRules(
                new StructureRules(
                        readBoolean(properties, "structure.prefer-logic-externalization"),
                        readBoolean(properties, "structure.block-on-unjustified-embedded-dominance"),
                        readRiskLevel(properties, "structure.max-host-document-risk")
                ),
                new VerificationRules(
                        readPositiveInt(properties, "verification.minimum-required-cases"),
                        readBoolean(properties, "verification.require-page-load-coverage"),
                        readBoolean(properties, "verification.require-runtime-stability-coverage"),
                        readBoolean(properties, "verification.require-visual-surface-coverage"),
                        readBoolean(properties, "verification.require-capability-backfill"),
                        readBoolean(properties, "verification.require-observable-state-change-for-interactive-cases"),
                        readBoolean(properties, "verification.require-performance-coverage-from-metadata"),
                        readCapabilityIdSet(properties, "verification.required-capability-surfaces")
                ),
                new ExperienceRules(
                        readBoolean(properties, "experience.promote-timed-progression-coverage-from-feature-profile"),
                        readBoolean(properties, "experience.gate-on-missing-required-experience-coverage")
                )
        );
    }

    private boolean readBoolean(Properties properties, String key) {
        String raw = requireProperty(properties, key);
        if (!"true".equalsIgnoreCase(raw) && !"false".equalsIgnoreCase(raw)) {
            throw new IllegalStateException("Invalid boolean quality rule: " + key + "=" + raw);
        }
        return Boolean.parseBoolean(raw);
    }

    private int readPositiveInt(Properties properties, String key) {
        String raw = requireProperty(properties, key);
        try {
            int parsed = Integer.parseInt(raw);
            if (parsed <= 0) {
                throw new IllegalStateException("Invalid positive integer quality rule: " + key + "=" + raw);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid positive integer quality rule: " + key + "=" + raw, exception);
        }
    }

    private StructureRiskLevel readRiskLevel(Properties properties, String key) {
        String raw = requireProperty(properties, key);
        try {
            return StructureRiskLevel.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid risk level quality rule: " + key + "=" + raw, exception);
        }
    }

    private Set<String> readCapabilityIdSet(Properties properties, String key) {
        String raw = requireProperty(properties, key);
        if (raw.isBlank()) {
            return Set.of();
        }
        return CapabilityIds.normalizeSet(Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList());
    }

    private String requireProperty(Properties properties, String key) {
        String raw = properties.getProperty(key);
        if (raw == null) {
            throw new IllegalStateException("Missing quality rule: " + key);
        }
        return raw.trim();
    }
}
