package devflow.agent.parsing;

import java.util.List;
import java.util.Locale;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * 负责 HTML 片段级结构检查与单标签包裹提取。
 */
final class HtmlFragmentInspectorSupport {

    boolean hasExplicitDocumentSkeleton(String htmlSource) {
        return containsTagStart(htmlSource, "html")
                && containsClosingTag(htmlSource, "html")
                && containsTagStart(htmlSource, "body")
                && containsClosingTag(htmlSource, "body");
    }

    boolean hasBalancedExplicitTagPairs(String htmlSource, String tagName) {
        if (tagName == null || tagName.isBlank()) {
            return true;
        }
        String normalizedTag = tagName.trim().toLowerCase(Locale.ROOT);
        return countOccurrences(htmlSource, "<" + normalizedTag) == countOccurrences(htmlSource, "</" + normalizedTag + ">");
    }

    String unwrapSingleWrappedElementBody(String htmlFragment, String tagName) {
        if (htmlFragment == null || htmlFragment.isBlank() || tagName == null || tagName.isBlank()) {
            return null;
        }
        Document document = Jsoup.parseBodyFragment(htmlFragment);
        Element body = document.body();
        if (body == null) {
            return null;
        }
        List<Element> children = body.children();
        if (children.size() != 1 || !hasOnlyIgnorableOuterNodes(body, children.getFirst())) {
            return null;
        }
        Element element = children.getFirst();
        if (!element.normalName().equals(tagName.trim().toLowerCase(Locale.ROOT))) {
            return null;
        }
        return switch (element.normalName()) {
            case "script", "style" -> {
                String bodyContent = element.data();
                if (bodyContent == null || bodyContent.isBlank()) {
                    bodyContent = element.html();
                }
                yield bodyContent == null ? "" : bodyContent.strip();
            }
            default -> element.html().strip();
        };
    }

    boolean containsElementTag(String htmlFragment, String tagName) {
        if (htmlFragment == null || htmlFragment.isBlank() || tagName == null || tagName.isBlank()) {
            return false;
        }
        Document document = Jsoup.parseBodyFragment(htmlFragment);
        return !document.select(tagName.trim().toLowerCase(Locale.ROOT)).isEmpty();
    }

    private boolean containsTagStart(String htmlSource, String tagName) {
        return countOccurrences(htmlSource, "<" + tagName.toLowerCase(Locale.ROOT)) > 0;
    }

    private boolean containsClosingTag(String htmlSource, String tagName) {
        return countOccurrences(htmlSource, "</" + tagName.toLowerCase(Locale.ROOT) + ">") > 0;
    }

    private int countOccurrences(String value, String token) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        int count = 0;
        int index = 0;
        while ((index = normalized.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private boolean hasOnlyIgnorableOuterNodes(Element body, Element child) {
        for (Node node : body.childNodes()) {
            if (node == child) {
                continue;
            }
            if (node instanceof TextNode textNode && textNode.isBlank()) {
                continue;
            }
            if (node instanceof Comment) {
                continue;
            }
            return false;
        }
        return true;
    }
}
