package devflow.agent.executor;

import devflow.agent.editing.FileStateLedger;
import devflow.agent.editing.FileStateSnapshot;
import devflow.agent.i18n.PlaceholderValues;

/**
 * exact-replace prompt 的共享支撑。
 *
 * <p>这层集中维护：
 * 1. 当前内容指纹；
 * 2. 当前内容原样展示；
 * 3. 协议字段与公共规则。
 */
final class ExactReplacePromptSupport {

    private static final FileStateLedger FILE_STATE_LEDGER = new FileStateLedger();

    private ExactReplacePromptSupport() {
    }

    static FileStateSnapshot capture(String targetPath, String currentContent) {
        return FILE_STATE_LEDGER.capture(java.nio.file.Path.of(targetPath), currentContent);
    }

    static String renderCurrentContent(String currentContent) {
        if (currentContent == null || currentContent.isBlank()) {
            return PlaceholderValues.machineNewFile();
        }
        return """
                <<<CURRENT_CONTENT
                %s
                CURRENT_CONTENT
                """.formatted(currentContent);
    }

    static String exactReplaceProtocol(String contentName) {
        return """
                你必须只返回一个 JSON 对象，格式如下：
                {
                  "targetPath": "必须原样拷贝输入中的 targetPath",
                  "baseContentHash": "必须原样拷贝输入中的 contentHash",
                  "oldText": "当前%s中原样存在的文本；只有当前内容为空时才允许为空字符串",
                  "newText": "替换后的文本",
                  "replaceAll": false
                }

                通用规则：
                1. 只返回 JSON，不要解释，不要 markdown
                2. `targetPath` 必须与输入给出的 targetPath 完全一致
                3. `baseContentHash` 必须与输入给出的 contentHash 完全一致
                4. `oldText` 必须从当前%s里原样拷贝，不能改写、不能总结
                5. 除非明确需要替换所有相同片段，否则 `replaceAll` 必须为 false
                6. 不要重写整份%s；只返回完成当前目标所需的最小替换
                """.formatted(contentName, contentName, contentName);
    }
}
