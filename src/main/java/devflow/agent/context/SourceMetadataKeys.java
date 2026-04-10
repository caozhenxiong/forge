package devflow.agent.context;

import java.util.List;

/**
 * 集中定义 Source Metadata 中机器可消费的稳定 key。
 *
 * <p>这些 key 同时用于：
 * 1. markdown 渲染；
 * 2. contract 解析；
 * 3. 文档净化与固定协议保护；
 * 4. prompt 模板中的稳定字段说明。
 */
public final class SourceMetadataKeys {

    public static final String HARD_USER_REQUIREMENTS = "hard.userRequirements";
    public static final String HARD_UPSTREAM_FACTS = "hard.upstreamFacts";
    public static final String SOFT_INFERENCES = "soft.inferences";
    public static final String SOFT_DESIGN_DECISIONS = "soft.designDecisions";
    public static final String SOFT_RECOMMENDATIONS = "soft.recommendations";
    public static final String OPEN_QUESTIONS = "open.questions";

    private static final List<String> ALL_KEYS = List.of(
            HARD_USER_REQUIREMENTS,
            HARD_UPSTREAM_FACTS,
            SOFT_INFERENCES,
            SOFT_DESIGN_DECISIONS,
            SOFT_RECOMMENDATIONS,
            OPEN_QUESTIONS
    );

    private SourceMetadataKeys() {
    }

    public static List<String> allKeys() {
        return ALL_KEYS;
    }

    public static String markdownLinePrefix(String key) {
        return "- " + key + ":";
    }
}
