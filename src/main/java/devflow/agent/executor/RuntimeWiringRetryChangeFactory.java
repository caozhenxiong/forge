package devflow.agent.executor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一生成 runtime wiring 续跑时的唯一 change-set。
 *
 * <p>runtime wiring 修复不是“只给一个 HTML 文件名，让 coder 自己猜 companion root 在哪”，
 * 而是必须把：
 * 1. 需要修接线的宿主 HTML；
 * 2. 当前已解析出的 companion runtime 根脚本；
 * 一起作为结构化 scope 传下去。
 *
 * <p>这样续跑 prompt、tool permission、mutation contract 才会共享同一份真相。
 */
final class RuntimeWiringRetryChangeFactory {

    List<FileChange> build(HtmlRuntimeOwnershipContract runtimeContract) {
        if (runtimeContract == null || !runtimeContract.active() || runtimeContract.htmlEntryPath() == null) {
            throw new IllegalStateException("Runtime wiring retry changes require an active runtime contract.");
        }
        ArrayList<FileChange> changes = new ArrayList<>();
        changes.add(new FileChange(
                runtimeContract.htmlEntryPath().toString().replace('\\', '/'),
                ChangeAction.WRITE,
                "修复宿主 HTML 与既有 runtime 根脚本的接线",
                FileEditScope.HOST_HTML_PATCH,
                runtimeContract.runtimeOwnership(),
                true
        ));
        for (Path runtimePath : runtimeContract.runtimePaths()) {
            if (runtimePath == null) {
                continue;
            }
            changes.add(new FileChange(
                    runtimePath.toString().replace('\\', '/'),
                    ChangeAction.WRITE,
                    "保留并对齐既有 companion runtime 根脚本，不要改写运行时所有权",
                    FileEditScope.AUTO,
                    null,
                    false
            ));
        }
        return List.copyOf(changes);
    }
}
