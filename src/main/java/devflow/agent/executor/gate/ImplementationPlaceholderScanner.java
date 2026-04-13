package devflow.agent.executor.gate;

import devflow.agent.executor.*;

import devflow.agent.executor.runtime.*;

import devflow.agent.parsing.HtmlDocumentInspector;
import devflow.agent.parsing.SourceLanguage;
import devflow.agent.util.ProjectPathSupport;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 负责文件级占位/空实现扫描。
 * 这层只把源码转成发现列表，不参与子任务责任域判断。
 */
final class ImplementationPlaceholderScanner {

    private final ImplementationBehaviorScanner behaviorScanner;

    ImplementationPlaceholderScanner(ImplementationBehaviorScanner behaviorScanner) {
        this.behaviorScanner = behaviorScanner;
    }

    ImplementationPlaceholderInspection inspectSingleFile(Path relativePath, String source) {
        List<ImplementationCompletenessFinding> findings = new ArrayList<>();

        collectPlaceholderMarkers(source, findings);
        collectExplicitNotImplementedMarkers(source, findings);

        if (looksLikeHtml(relativePath)) {
            int scriptIndex = 0;
            for (String scriptSource : HtmlDocumentInspector.inlineScriptBodies(source)) {
                scriptIndex++;
                final int currentScriptIndex = scriptIndex;
                ImplementationBehaviorInspection behaviorInspection =
                        behaviorScanner.inspectCodeLikeContent(SourceLanguage.JAVASCRIPT, scriptSource);
                behaviorInspection.findings().forEach(item -> findings.add(new ImplementationCompletenessFinding(
                        item.type(),
                        item.symbolName(),
                        "inline-script[" + currentScriptIndex + "]: " + item.evidence()
                )));
            }
            return summarizeInspection(findings);
        }

        SourceLanguage language = inferLanguage(relativePath);
        if (language == SourceLanguage.UNSUPPORTED) {
            return summarizeInspection(findings);
        }
        ImplementationBehaviorInspection behaviorInspection = behaviorScanner.inspectCodeLikeContent(language, source);
        behaviorInspection.findings().forEach(item -> findings.add(new ImplementationCompletenessFinding(
                item.type(),
                item.symbolName(),
                item.evidence()
        )));
        return summarizeInspection(findings);
    }

    private boolean looksLikeHtml(Path relativePath) {
        return ProjectPathSupport.isHtml(relativePath);
    }

    private SourceLanguage inferLanguage(Path relativePath) {
        String path = relativePath == null ? "" : relativePath.toString().toLowerCase();
        if (ProjectPathSupport.isHtml(path)) {
            return SourceLanguage.HTML;
        }
        if (ProjectPathSupport.isJavaScript(path)) {
            return SourceLanguage.JAVASCRIPT;
        }
        if (ProjectPathSupport.isTypeScript(path)) {
            return SourceLanguage.TYPESCRIPT;
        }
        if (ProjectPathSupport.isJava(path)) {
            return SourceLanguage.JAVA;
        }
        if (ProjectPathSupport.isPython(path)) {
            return SourceLanguage.PYTHON;
        }
        if (ProjectPathSupport.isGo(path)) {
            return SourceLanguage.GO;
        }
        return SourceLanguage.UNSUPPORTED;
    }

    private void collectPlaceholderMarkers(String source, List<ImplementationCompletenessFinding> findings) {
        String raw = source == null ? "" : source;
        Set<String> markers = new LinkedHashSet<>();
        for (String marker : ImplementationCompletenessPolicy.placeholderWordMarkers()) {
            if (containsWordIgnoreCase(raw, marker)) {
                markers.add(marker);
            }
        }
        for (String marker : ImplementationCompletenessPolicy.placeholderPhraseMarkers()) {
            if (containsPhraseIgnoreCase(raw, marker)) {
                markers.add(marker);
            }
        }
        for (String marker : ImplementationCompletenessPolicy.placeholderCjkMarkers()) {
            if (raw.contains(marker)) {
                markers.add(marker);
            }
        }
        markers.forEach(marker -> findings.add(new ImplementationCompletenessFinding(
                ImplementationCompletenessFindingType.PLACEHOLDER_MARKER,
                null,
                "contains placeholder marker: " + marker
        )));
    }

    private void collectExplicitNotImplementedMarkers(String source, List<ImplementationCompletenessFinding> findings) {
        String normalized = behaviorScanner.normalizeBody(source);
        Set<String> markers = new LinkedHashSet<>();
        for (String marker : ImplementationCompletenessPolicy.notImplementedMarkers()) {
            if (normalized.contains(marker)) {
                markers.add(marker);
            }
        }
        markers.forEach(marker -> findings.add(new ImplementationCompletenessFinding(
                ImplementationCompletenessFindingType.PLACEHOLDER_MARKER,
                null,
                "contains explicit not-implemented marker: " + marker
        )));
    }

    private ImplementationPlaceholderInspection summarizeInspection(List<ImplementationCompletenessFinding> findings) {
        int placeholderMarkers = 0;
        int emptyBehaviors = 0;
        List<String> evidence = new ArrayList<>();
        for (ImplementationCompletenessFinding finding : findings) {
            if (finding.type() == ImplementationCompletenessFindingType.PLACEHOLDER_MARKER) {
                placeholderMarkers++;
            } else if (finding.type() == ImplementationCompletenessFindingType.EMPTY_BEHAVIOR) {
                emptyBehaviors++;
            }
            addEvidence(evidence, finding.evidence());
        }
        return new ImplementationPlaceholderInspection(placeholderMarkers, emptyBehaviors, evidence, findings);
    }

    private void addEvidence(List<String> evidence, String item) {
        if (item == null || item.isBlank() || evidence.size() >= ImplementationCompletenessPolicy.maxEvidenceItems()) {
            return;
        }
        evidence.add(item.trim());
    }

    private boolean containsWordIgnoreCase(String source, String word) {
        if (source == null || source.isBlank() || word == null || word.isBlank()) {
            return false;
        }
        String normalizedSource = source.toLowerCase(Locale.ROOT);
        String normalizedWord = word.toLowerCase(Locale.ROOT);
        int cursor = 0;
        while (cursor >= 0 && cursor < normalizedSource.length()) {
            int matchIndex = normalizedSource.indexOf(normalizedWord, cursor);
            if (matchIndex < 0) {
                return false;
            }
            if (isWordBoundary(normalizedSource, matchIndex - 1)
                    && isWordBoundary(normalizedSource, matchIndex + normalizedWord.length())) {
                return true;
            }
            cursor = matchIndex + normalizedWord.length();
        }
        return false;
    }

    private boolean containsPhraseIgnoreCase(String source, String phrase) {
        if (source == null || source.isBlank() || phrase == null || phrase.isBlank()) {
            return false;
        }
        return devflow.agent.text.TextCanonicalizer.collapseWhitespace(source).toLowerCase(Locale.ROOT)
                .contains(devflow.agent.text.TextCanonicalizer.collapseWhitespace(phrase).toLowerCase(Locale.ROOT));
    }

    private boolean isWordBoundary(String source, int index) {
        if (index < 0 || index >= source.length()) {
            return true;
        }
        char current = source.charAt(index);
        return !Character.isLetterOrDigit(current) && current != '_' && current != '$';
    }
}
