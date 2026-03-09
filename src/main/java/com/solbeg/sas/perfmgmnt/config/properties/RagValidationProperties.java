package com.solbeg.sas.perfmgmnt.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration properties for RAG (Retrieval-Augmented Generation) input validation pipeline.
 * Contains validation thresholds and filter configurations.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag.validation")
@Validated
public class RagValidationProperties {

    /**
     * Configuration for quick input filter (first stage of validation).
     */
    private final QuickFilter quickFilter = new QuickFilter();

    /**
     * Configuration for language detector filter (second stage of validation).
     */
    private final LanguageDetector languageDetector = new LanguageDetector();

    /**
     * Configuration for domain relevance checker (third stage of validation).
     */
    private final DomainChecker domainChecker = new DomainChecker();

    @Getter
    @Setter
    public static class QuickFilter {

        /**
         * Minimum number of characters (trimmed) required to pass quick validation.
         * Must be between 1 and 100 characters.
         */
        @Min(value = 1, message = "Minimum length must be at least 1 character")
        @Max(value = 100, message = "Maximum length must not exceed 100 characters")
        private int minLength = 2;
    }

    @Getter
    @Setter
    public static class LanguageDetector {

        /**
         * Comma-separated list of accepted language names. Each entry must be a valid
         * {@link com.github.pemistahl.lingua.api.Language} enum constant (e.g., {@code ENGLISH},
         * {@code GERMAN}). Invalid names will cause an {@link IllegalStateException} at startup.
         */
        @NotBlank(message = "Accepted languages cannot be blank")
        @Pattern(
                regexp = "^[A-Z_]+(,\\s*[A-Z_]+)*$",
                message = "Accepted languages must be a comma-separated list of uppercase language names (e.g. ENGLISH,GERMAN)")
        private String acceptedLanguages = "ENGLISH";

        /**
         * Minimum confidence score to trust a language detection result.
         * Must be between 0.0 and 1.0.
         */
        @DecimalMin(value = "0.0", message = "Minimum confidence must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Minimum confidence must not exceed 1.0")
        private double minConfidence = 0.5;

        /**
         * Minimum input length to trigger language detection.
         * Must be between 1 and 1000 characters.
         */
        @Min(value = 1, message = "Short input threshold must be at least 1 character")
        @Max(value = 1000, message = "Short input threshold must not exceed 1000 characters")
        private int shortInputThreshold = 10;
    }

    @Getter
    @Setter
    public static class DomainChecker {

        /**
         * Maximum number of search results to retrieve from vector store.
         * Must be between 1 and 10.
         */
        @Min(value = 1, message = "Maximum search results must be at least 1")
        @Max(value = 10, message = "Maximum search results must not exceed 10")
        private int maxSearchResults = 1;

        /**
         * Minimum similarity score required for domain relevance validation.
         * Must be between 0.0 and 1.0.
         */
        @DecimalMin(value = "0.0", message = "Minimum score must be at least 0.0")
        @DecimalMax(value = "1.0", message = "Minimum score must not exceed 1.0")
        private double minScore = 0.5;
    }
}
