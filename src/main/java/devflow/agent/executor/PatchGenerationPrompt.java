package devflow.agent.executor;

import devflow.agent.executor.gate.*;
import devflow.agent.executor.runtime.*;

/**
 * patch 生成阶段真正发给模型的 prompt 对。
 *
 * <p>把 system/user prompt 收成显式值对象，避免协调器继续在局部变量里传递长模板字符串。
 */
record PatchGenerationPrompt(
        String systemPrompt,
        String userPrompt
) {
}
