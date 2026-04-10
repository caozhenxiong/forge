package devflow.agent.executor;

/**
 * 宿主内嵌语言的编辑计划标记接口。
 *
 * <p>核心编辑内核只关心“宿主里抽出的局部片段如何被 patch 化”，
 * 不关心它来自 HTML、模板还是别的宿主文件。
 */
interface EmbeddingEditPlan {

    PatchPlan patchPlan();

    String embeddedContent();

    boolean isUsable();
}
