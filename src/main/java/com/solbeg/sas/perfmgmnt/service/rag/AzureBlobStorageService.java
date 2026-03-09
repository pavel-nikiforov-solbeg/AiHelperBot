package com.solbeg.sas.perfmgmnt.service.rag;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobProperties;
import com.azure.storage.blob.models.BlobStorageException;
import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Service for managing file storage operations via Azure Blob Storage. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AzureBlobStorageService {

    private static final String PDF_BLOB = "Performance-User-Guide.pdf";
    private static final String VECTOR_STORE_BLOB = "vectorstore.bin";
    private static final String METADATA_BLOB = "index-metadata.json";
    private static final String VECTOR_STORE_LABEL = "VectorStore";
    private static final String METADATA_LABEL = "Metadata";
    private static final String PDF_LABEL = "PDF";

    private final RagProperties ragProperties;

    private BlobContainerClient containerClient;

    @PostConstruct
    public void init() {
        String storageUrl = ragProperties.getStorage().getBlob().getUrl();
        if (storageUrl == null || storageUrl.isBlank()) {
            log.warn("Azure Blob Storage URL is not configured — blob operations will be unavailable");
            return;
        }
        containerClient = new BlobContainerClientBuilder().endpoint(storageUrl).buildClient();
        log.info("AzureBlobStorageService initialised. Container: {}",
                containerClient.getBlobContainerName());
    }

    /**
     * Checks if Azure Blob Storage is reachable and at least one of the required RAG files exists.
     * Does not throw exceptions — returns false in case of any problems.
     *
     * @return true if storage is accessible and has at least PDF or vectorstore, false otherwise
     */
    public boolean isHealthy() {
        if (containerClient == null) {
            return false;
        }

        try {
            boolean vectorStorePresent = containerClient.getBlobClient(VECTOR_STORE_BLOB).exists();
            boolean pdfPresent = containerClient.getBlobClient(PDF_BLOB).exists();

            if (vectorStorePresent || pdfPresent) {
                log.debug("Azure Blob Storage healthy: vectorStore={}, pdf={}",
                        vectorStorePresent, pdfPresent);
                return true;
            }

            log.warn("Azure Blob Storage reachable, but no RAG files found (neither '{}' nor '{}')",
                    VECTOR_STORE_BLOB, PDF_BLOB);
            return false;

        } catch (Exception e) {
            log.error("Cannot reach Azure Blob Storage: {}", e.getMessage(), e);
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Existence checks
    // -------------------------------------------------------------------------

    /** @return true if the PDF guide exists in blob storage */
    public boolean pdfExists() {
        return blobExists(PDF_BLOB);
    }

    /** @return true if the serialised vector store exists in blob storage */
    public boolean vectorStoreExists() {
        return blobExists(VECTOR_STORE_BLOB);
    }

    /** @return true if the index metadata file exists in blob storage */
    public boolean metadataExists() {
        return blobExists(METADATA_BLOB);
    }

    // -------------------------------------------------------------------------
    // Downloads
    // -------------------------------------------------------------------------

    public void downloadPdfToPath(Path target) throws IOException {
        downloadBlobToPath(PDF_BLOB, target, PDF_LABEL);
    }

    public void downloadVectorStoreToPath(Path target) throws IOException {
        downloadBlobToPath(VECTOR_STORE_BLOB, target, VECTOR_STORE_LABEL);
    }

    public void downloadMetadataToPath(Path target) throws IOException {
        downloadBlobToPath(METADATA_BLOB, target, METADATA_LABEL);
    }

    // -------------------------------------------------------------------------
    // Uploads — accept Path so we can obtain accurate file size via Files.size()
    // -------------------------------------------------------------------------

    /**
     * Uploads the serialised vector store file to blob storage.
     *
     * @param source local path to the vector store file
     * @throws IOException if the file cannot be read
     */
    public void uploadVectorStore(Path source) throws IOException {
        uploadBlobFromPath(VECTOR_STORE_BLOB, source, VECTOR_STORE_LABEL);
    }

    /**
     * Uploads the index metadata file to blob storage.
     *
     * @param source local path to the metadata file
     * @throws IOException if the file cannot be read
     */
    public void uploadMetadata(Path source) throws IOException {
        uploadBlobFromPath(METADATA_BLOB, source, METADATA_LABEL);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private boolean blobExists(String blobName) {
        if (containerClient == null) {
            return false;
        }
        try {
            return containerClient.getBlobClient(blobName).exists();
        } catch (BlobStorageException e) {
            log.warn("Error checking existence of blob '{}': {}", blobName, e.getMessage());
            return false;
        }
    }

    private void downloadBlobToPath(String blobName, Path target, String label) throws IOException {
        if (containerClient == null) {
            throw new IllegalStateException("Blob Storage is not configured");
        }
        try {
            BlobClient client = containerClient.getBlobClient(blobName);
            BlobProperties properties = client.getProperties();
            log.info("Downloading {} from Azure Blob '{}', size: {} bytes",
                    label, blobName, properties.getBlobSize());
            try (InputStream is = client.openInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Downloaded {} to local path: {}", label, target);
        } catch (BlobStorageException e) {
            log.error("Azure Blob error downloading '{}': {}", blobName, e.getMessage(), e);
            throw new RuntimeException("Azure Blob download failed for " + label, e);
        }
    }

    private void uploadBlobFromPath(String blobName, Path source, String label) throws IOException {
        if (containerClient == null) {
            throw new IllegalStateException("Blob Storage is not configured");
        }
        long size = Files.size(source);
        log.info("Uploading {} to Azure Blob '{}', size: {} bytes", label, blobName, size);
        try (InputStream data = Files.newInputStream(source)) {
            BlobClient client = containerClient.getBlobClient(blobName);
            client.upload(data, size, true);
            log.info("Uploaded {} to Azure Blob '{}'", label, blobName);
        } catch (BlobStorageException e) {
            log.error("Azure Blob error uploading '{}': {}", blobName, e.getMessage(), e);
            throw new RuntimeException("Azure Blob upload failed for " + label, e);
        }
    }
}
