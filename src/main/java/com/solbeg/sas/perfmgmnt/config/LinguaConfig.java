package com.solbeg.sas.perfmgmnt.config;

import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import com.solbeg.sas.perfmgmnt.config.properties.RagValidationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Lingua language detection components.
 * Provides the {@link LanguageDetector} bean used by the RAG input validation pipeline.
 */
@Configuration
public class LinguaConfig {

    /**
     * Creates a {@link LanguageDetector} that loads all available language models.
     *
     * <p>All language models are loaded so the detector can identify <em>non-accepted</em> languages
     * and reject them. Loading only the accepted languages (e.g., {@code ENGLISH} alone) would make
     * the detector unable to return any other language code, silently disabling the rejection logic.
     *
     * <p>Building the detector is expensive (loads language models into memory) and is done once
     * at application startup as a Spring singleton bean.
     *
     * @param properties RAG validation properties containing detector configuration
     * @return configured {@link LanguageDetector} instance
     */
    @Bean
    public LanguageDetector languageDetector(RagValidationProperties properties) {
        return LanguageDetectorBuilder.fromAllLanguages()
                .withMinimumRelativeDistance(properties.getLanguageDetector().getMinConfidence())
                .build();
    }
}
