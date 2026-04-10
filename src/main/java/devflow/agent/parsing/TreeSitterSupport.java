package devflow.agent.parsing;

import java.nio.file.Path;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TreeSitterSupport {

    public static final String APP_ROOT_ID = "app-root";
    public static final String APP_STYLE_ID = "app-style";
    public static final String APP_SCRIPT_ID = "app-script";

    private final TreeSitterParserSupport parserSupport = new TreeSitterParserSupport();
    private final TreeSitterHtmlInspector htmlInspector = new TreeSitterHtmlInspector(parserSupport);
    private final TreeSitterCodeInspector codeInspector = new TreeSitterCodeInspector(parserSupport);

    public TreeSitterParseSummary analyze(Path relativePath, String source) {
        return analyze(SourceLanguage.fromPath(relativePath), source);
    }

    public TreeSitterParseSummary analyze(SourceLanguage language, String source) {
        return parserSupport.analyze(language, source);
    }

    public HtmlStructureSnapshot inspectHtml(String source) {
        return htmlInspector.inspectHtml(source);
    }

    public HtmlEditableStructure inspectEditableHtml(String source) {
        return htmlInspector.inspectEditableHtml(source);
    }

    public CodeStructureSnapshot inspectCodeStructure(Path relativePath, String source) {
        return inspectCodeStructure(SourceLanguage.fromPath(relativePath), source);
    }

    public CodeStructureSnapshot inspectCodeStructure(SourceLanguage language, String source) {
        return codeInspector.inspectCodeStructure(language, source);
    }
}
