package devflow.agent.executor;

import java.nio.file.Path;
import java.util.List;

/**
 * HTML 入口目录树 runtime 资产接线的结构化检查结果。
 */
record WebRuntimeAssetWiringInspection(
        List<Path> runtimeScripts,
        List<Path> directHtmlRuntimeScripts,
        List<Path> inlineImportedRuntimeScripts,
        List<Path> orphanRuntimeScripts,
        List<String> issues,
        List<String> evidence
) {

    HtmlRuntimeOwnershipContract runtimeContract(Path htmlEntryPath) {
        if (htmlEntryPath == null) {
            return null;
        }
        java.util.LinkedHashSet<Path> runtimeRoots = new java.util.LinkedHashSet<>();
        if (directHtmlRuntimeScripts != null) {
            runtimeRoots.addAll(directHtmlRuntimeScripts);
        }
        if (inlineImportedRuntimeScripts != null) {
            runtimeRoots.addAll(inlineImportedRuntimeScripts);
        }
        if (orphanRuntimeScripts != null) {
            runtimeRoots.addAll(orphanRuntimeScripts);
        }
        if (!runtimeRoots.isEmpty()) {
            return HtmlRuntimeOwnershipContract.externalCompanion(
                    htmlEntryPath,
                    runtimeRoots.stream().sorted().toList()
            );
        }
        return HtmlRuntimeOwnershipContract.inlineHost(htmlEntryPath);
    }
}
