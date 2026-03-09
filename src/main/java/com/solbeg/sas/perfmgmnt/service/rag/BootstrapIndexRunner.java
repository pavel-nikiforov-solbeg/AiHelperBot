package com.solbeg.sas.perfmgmnt.service.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Initializes the vector store once after the application is fully ready. */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapIndexRunner {

    private final EmbeddingIndexer indexer;
    private final PdfChunker chunker;
    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final AzureBlobStorageService blobStorage;

    private static final String METADATA_FILE_NAME = "index-metadata.json";
    private static final String VECTOR_STORE_FILE_NAME = "vectorstore.bin";
    private static final String PDF_FILE_NAME = "Performance-User-Guide.pdf";

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        try {
            initializeVectorStore();
        } catch (Exception e) {
            log.error(
                    "Critical failure during AI Helper Bot initialization - feature will be unavailable",
                    e);
        }
    }

    private void initializeVectorStore() {
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "sas-rag");
        try {
            Files.createDirectories(tempDir);
        } catch (Exception e) {
            log.error("Failed to create temp directory for RAG files", e);
            return;
        }

        Path localVectorPath = tempDir.resolve(VECTOR_STORE_FILE_NAME);

        if (!blobStorage.isHealthy()) {
            log.warn(
                    "Azure Blob Storage is not healthy. Attempting degraded mode with local cache.");
        }

        if (indexer.isLoaded()) {
            log.info("Vector store already loaded in memory - skipping further initialization");
            return;
        }

        if (Files.exists(localVectorPath)) {
            try {
                indexer.load(localVectorPath.toString());
                log.info("Vector store loaded from local cache: {}", localVectorPath);
                return;
            } catch (Exception e) {
                log.warn("Failed to load vector store from local cache - will try Blob", e);
            }
        }

        tryLoadStoreFromBlob(localVectorPath);

        String currentHash = calculateCurrentConfigHash(localVectorPath);
        if (currentHash == null) {
            log.warn("Could not calculate config hash - skipping reindex check");
            return;
        }

        if (needsReindex(tempDir.resolve(METADATA_FILE_NAME), currentHash)) {
            log.info("Detected need to rebuild vector index");
            rebuildIndex(
                    localVectorPath,
                    tempDir.resolve(PDF_FILE_NAME),
                    tempDir.resolve(METADATA_FILE_NAME),
                    currentHash);
        } else {
            log.info("Vector index appears up-to-date");
        }

        if (indexer.isLoaded()) {
            log.info("AI Helper Bot vector store successfully initialized");
        } else {
            log.warn("AI Helper Bot vector store failed to initialize - feature degraded");
        }
    }

    private void tryLoadStoreFromBlob(Path localVectorPath) {
        if (!blobStorage.vectorStoreExists()) {
            log.info("No vector store found in Blob Storage");
            return;
        }

        try {
            blobStorage.downloadVectorStoreToPath(localVectorPath);
            indexer.load(localVectorPath.toString());
            log.info("Vector store loaded from Blob Storage");
        } catch (Exception e) {
            log.error("Failed to download or load vector store from Blob", e);
        }
    }

    private String calculateCurrentConfigHash(Path localVectorPath) {
        Path localPdfPath = localVectorPath.getParent().resolve(PDF_FILE_NAME);

        try {
            if (!Files.exists(localPdfPath)) {
                if (!blobStorage.pdfExists()) {
                    log.warn("PDF not found in Blob Storage");
                    return null;
                }
                blobStorage.downloadPdfToPath(localPdfPath);
            }

            try (InputStream is = Files.newInputStream(localPdfPath)) {
                String pdfHash = DigestUtils.sha256Hex(is);
                String configPart =
                        String.format(
                                "%d|%d|%s",
                                properties.getGuide().getChunkSize(),
                                properties.getGuide().getOverlap(),
                                properties.getGuide().getGuideUrl());
                return DigestUtils.sha256Hex(pdfHash + configPart);
            }
        } catch (Exception e) {
            log.error("Failed to calculate current config hash", e);
            return null;
        }
    }

    private boolean needsReindex(Path localMetadataPath, String currentHash) {
        if (currentHash == null) {
            return false;
        }

        try {
            if (!Files.exists(localMetadataPath) && blobStorage.metadataExists()) {
                blobStorage.downloadMetadataToPath(localMetadataPath);
            }

            if (!Files.exists(localMetadataPath)) {
                return true;
            }

            Map<String, String> saved =
                    objectMapper.readValue(localMetadataPath.toFile(), new TypeReference<>() {});
            return !currentHash.equals(saved.get("contentHash"));
        } catch (Exception e) {
            log.warn("Failed to check metadata for reindex - assuming rebuild needed", e);
            return true;
        }
    }

    private void rebuildIndex(
            Path localVectorPath, Path localPdfPath, Path localMetadataPath, String currentHash) {
        try {
            if (!Files.exists(localPdfPath)) {
                blobStorage.downloadPdfToPath(localPdfPath);
            }

            try (InputStream pdfStream = Files.newInputStream(localPdfPath)) {
                List<Document> docs =
                        chunker.chunk(
                                pdfStream,
                                properties.getGuide().getGuideUrl(),
                                properties.getGuide().getChunkSize(),
                                properties.getGuide().getOverlap());
                log.info("Chunked PDF into {} documents", docs.size());

                indexer.clear();
                indexer.index(docs, localVectorPath.toString());
                log.info("In-memory vector index rebuilt");

                blobStorage.uploadVectorStore(localVectorPath);

                Map<String, String> meta =
                        Map.of(
                                "contentHash", currentHash,
                                "chunkSize", String.valueOf(properties.getGuide().getChunkSize()),
                                "overlap", String.valueOf(properties.getGuide().getOverlap()),
                                "pdfFilename", PDF_FILE_NAME,
                                "timestamp", String.valueOf(System.currentTimeMillis()));

                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValue(localMetadataPath.toFile(), meta);

                blobStorage.uploadMetadata(localMetadataPath);

                log.info("Index rebuild and upload completed successfully");
            }
        } catch (Exception e) {
            log.error("Failed to rebuild vector index - AI Helper Bot remains unavailable", e);
        }
    }
}
