package devflow.agent.parsing;

import java.util.LinkedHashSet;
import java.util.Set;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * 负责 HTML 里稳定选择器集合的提取。
 */
final class HtmlSelectorScanner {

    Set<String> idSelectors(Document document) {
        Set<String> ids = new LinkedHashSet<>();
        for (Element element : document.select("[id]")) {
            String value = element.id();
            if (value != null && !value.isBlank()) {
                ids.add(value.trim());
            }
        }
        return Set.copyOf(ids);
    }

    Set<String> classSelectors(Document document) {
        Set<String> classes = new LinkedHashSet<>();
        for (Element element : document.select("[class]")) {
            element.classNames().stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .forEach(classes::add);
        }
        return Set.copyOf(classes);
    }
}
