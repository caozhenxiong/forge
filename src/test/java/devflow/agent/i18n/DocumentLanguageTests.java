package devflow.agent.i18n;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentLanguageTests {

    @Test
    void detectsChineseWhenHanCharactersDominate() {
        assertEquals(DocumentLanguage.ZH, DocumentLanguage.detect("俄罗斯方块页面，支持开始、暂停、重开"));
    }

    @Test
    void detectsEnglishWhenLatinCharactersDominate() {
        assertEquals(DocumentLanguage.EN, DocumentLanguage.detect("Build a playable Tetris game with keyboard controls."));
    }

    @Test
    void reportsWhetherHumanLanguageExists() {
        assertTrue(DocumentLanguage.containsHumanLanguage("### 标题"));
        assertTrue(DocumentLanguage.containsHumanLanguage("Heading"));
        assertFalse(DocumentLanguage.containsHumanLanguage("- 1234._-: 5678"));
    }
}
