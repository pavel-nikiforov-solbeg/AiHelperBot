package com.solbeg.sas.perfmgmnt.config.properties;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Main configuration properties for RAG (Retrieval-Augmented Generation) functionality.
 * Mapped from application.yml with prefix "rag".
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "rag")
@Validated
public class RagProperties {

    /**
     * LLM service configuration
     */
    @Valid
    private LlmProperties llm;

    /**
     * PDF guide processing configuration
     */
    private GuideProperties guide;

    /**
     * Azure Blob Storage configuration
     */
    private StorageProperties storage = new StorageProperties();

    @Getter
    @Setter
    public static class StorageProperties {

        /** Azure Blob Storage container configuration */
        private BlobProperties blob = new BlobProperties();

        @Getter
        @Setter
        public static class BlobProperties {

            /**
             * Azure Blob Storage SAS URL or connection string.
             * Leave blank to run in degraded mode without Blob Storage.
             */
            private String url = "";
        }
    }
}
