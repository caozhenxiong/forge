package devflow.agent.i18n;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devflow.document")
public record DocumentLanguageProperties(DocumentLanguage defaultLanguage) {

    public DocumentLanguageProperties {
        defaultLanguage = defaultLanguage == null ? DocumentLanguage.ZH : defaultLanguage;
    }
}
