package me.kkfish.misc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageManagerTest {

    @Test
    void playerWithoutLanguageUsesCurrentLanguage() {
        assertEquals("en_us", MessageManager.chooseMessageLang("en_us", null));
        assertEquals("en_us", MessageManager.chooseMessageLang("en_us", ""));
    }

    @Test
    void oldChineseDefaultDoesNotOverrideEnglishConfig() {
        assertEquals("en_us", MessageManager.chooseMessageLang("en_us", "zh_cn"));
    }

    @Test
    void explicitNonDefaultPlayerLanguageStillWorks() {
        assertEquals("en_us", MessageManager.chooseMessageLang("zh_cn", "en_us"));
    }
}
