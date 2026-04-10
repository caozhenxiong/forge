package devflow.agent.executor;

import java.nio.file.Path;

/**
 * 宿主嵌入语言的轻量适配接口。
 *
 * <p>这层只负责：
 * 1. 判断宿主文件是否支持某类嵌入编辑；
 * 2. 构造该嵌入区域的编辑计划；
 * 3. 把更新后的片段重新回填到宿主文件。
 *
 * <p>它不负责模型调用、预算路由和流程裁决。
 */
interface EmbeddingAdapter<T extends EmbeddingEditPlan> {

    boolean supports(Path hostPath, String hostSource);

    T buildEditPlan(Path hostPath, String hostSource);

    String mergeIntoHost(String hostSource, String embeddedContent);
}
