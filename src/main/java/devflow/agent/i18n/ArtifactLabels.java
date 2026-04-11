package devflow.agent.i18n;

/**
 * 集中管理跨模块复用的人类可读标题和栏目名。
 * 这些值本身不是流程协议，但如果长期散落在各个渲染器和模板里，会很快演化成难以维护的魔法字符串。
 */
public final class ArtifactLabels {

    private ArtifactLabels() {
    }

    public static String currentNotes(DocumentLanguage language) {
        // Current Notes 既是展示标题，也是当前模板链上的稳定结构标签。
        // 这里保持固定英文，避免不同语言下继续分裂模板和解析约定。
        return "Current Notes";
    }

    public static String sourceMetadata(DocumentLanguage language) {
        return "Source Metadata";
    }

    public static String contractMetadata(DocumentLanguage language) {
        return "Contract Metadata";
    }

    public static String requiredEvidence(DocumentLanguage language) {
        return language.choose("必需证据", "Required Evidence");
    }

    public static String mustFixFirst(DocumentLanguage language) {
        return language.choose("必须优先修复", "Must Fix First");
    }

    public static String forbiddenDirections(DocumentLanguage language) {
        return language.choose("禁止方向", "Forbidden Directions");
    }

    public static String mustFixFirstCoverageRequirement(DocumentLanguage language) {
        return language.choose("必须优先覆盖 Must Fix First", "Must Fix First items must be covered first");
    }

    public static String forbiddenDirectionsAvoidRequirement(DocumentLanguage language) {
        return language.choose("必须避免 Forbidden Directions", "Forbidden Directions must be avoided");
    }

    public static String generatedAt(DocumentLanguage language) {
        return language.choose("生成时间", "Generated At");
    }

    public static String heading(String title) {
        return "## " + title;
    }

    public static String numberedHeading(int sectionNumber, String title) {
        return "## " + sectionNumber + ". " + title;
    }

    public static String sourceMetadataHeading(DocumentLanguage language) {
        return heading(sourceMetadata(language));
    }

    public static String contractMetadataHeading(DocumentLanguage language) {
        return heading(contractMetadata(language));
    }

}
