package devflow.agent.parsing;

import java.nio.file.Path;

public enum SourceLanguage {
    HTML,
    JAVASCRIPT,
    TYPESCRIPT,
    JAVA,
    PYTHON,
    GO,
    UNSUPPORTED;

    static SourceLanguage fromPath(Path relativePath) {
        if (relativePath == null || relativePath.getFileName() == null) {
            return UNSUPPORTED;
        }
        String fileName = relativePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return HTML;
        }
        if (fileName.endsWith(".js") || fileName.endsWith(".mjs") || fileName.endsWith(".cjs")) {
            return JAVASCRIPT;
        }
        if (fileName.endsWith(".ts") || fileName.endsWith(".tsx")
                || fileName.endsWith(".mts") || fileName.endsWith(".cts")) {
            return TYPESCRIPT;
        }
        if (fileName.endsWith(".java")) {
            return JAVA;
        }
        if (fileName.endsWith(".py")) {
            return PYTHON;
        }
        if (fileName.endsWith(".go")) {
            return GO;
        }
        return UNSUPPORTED;
    }
}
