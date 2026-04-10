package devflow.agent.context;

/**
 * 结构化验证元数据。
 *
 * <p>这层只承载机器可消费的稳定验证要求，不再从自然语言正文里猜“是否需要性能验证”
 * 或“某个阈值是多少”。如果对应键不存在，就表示没有显式声明，而不是让下游自己推断。
 */
public record ValidationMetadata(
        boolean performanceMeasurementRequired,
        Integer pageLoadMaxMs,
        Integer interactionMaxMs
) {

    public static ValidationMetadata empty() {
        return new ValidationMetadata(false, null, null);
    }

    public ValidationMetadata merge(ValidationMetadata other) {
        if (other == null) {
            return this;
        }
        return new ValidationMetadata(
                performanceMeasurementRequired || other.performanceMeasurementRequired,
                pageLoadMaxMs != null ? pageLoadMaxMs : other.pageLoadMaxMs,
                interactionMaxMs != null ? interactionMaxMs : other.interactionMaxMs
        );
    }
}
