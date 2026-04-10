package devflow.agent.parsing;

import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;

public enum SourceLanguage {
    HTML,
    JAVASCRIPT,
    TYPESCRIPT,
    CSS,
    JAVA,
    PYTHON,
    GO,
    UNSUPPORTED;

    static SourceLanguage fromPath(Path relativePath) {
        if (relativePath == null || relativePath.getFileName() == null) {
            return UNSUPPORTED;
        }
        String fileName = relativePath.getFileName().toString();
        if (ProjectPathSupport.isHtml(fileName)) {
            return HTML;
        }
        if (ProjectPathSupport.isJavaScript(fileName)) {
            return JAVASCRIPT;
        }
        if (ProjectPathSupport.isTypeScript(fileName)) {
            return TYPESCRIPT;
        }
        if (ProjectPathSupport.isStyle(fileName)) {
            return CSS;
        }
        if (ProjectPathSupport.isJava(fileName)) {
            return JAVA;
        }
        if (ProjectPathSupport.isPython(fileName)) {
            return PYTHON;
        }
        if (ProjectPathSupport.isGo(fileName)) {
            return GO;
        }
        return UNSUPPORTED;
    }
}
