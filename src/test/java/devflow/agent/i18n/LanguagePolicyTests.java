package devflow.agent.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LanguagePolicyTests {

    private final LanguagePolicy languagePolicy = new LanguagePolicy();

    @Test
    void ignoresMachineMetadataWhenResolvingLanguage() {
        String input = """
                ## Contract Metadata
                - runtime.entryRequired: true
                - runtime.entryKind: html-entry

                ## 1. 文档目标
                需要交付一个可以直接打开运行的俄罗斯方块网页。
                """;

        assertEquals(DocumentLanguage.ZH, languagePolicy.resolve(input));
    }

    @Test
    void ignoresSourceMetadataWhenResolvingLanguage() {
        String input = """
                ## Source Metadata
                - hard.userRequirements: direct-open
                - hard.upstreamFacts: browser-runtime

                ## Product Goal
                Deliver a playable browser game with visible score and next-piece preview.
                """;

        assertEquals(DocumentLanguage.EN, languagePolicy.resolve(input));
    }
}
