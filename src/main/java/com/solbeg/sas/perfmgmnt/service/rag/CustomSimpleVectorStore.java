package com.solbeg.sas.perfmgmnt.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CustomSimpleVectorStore {

    /**
     * Safe upper bound for full-index enumeration. A PDF guide will never produce
     * more than a few thousand chunks; 50 000 is a generous cap that avoids the
     * heap pressure caused by {@code Integer.MAX_VALUE}.
     */
    private static final int MAX_INDEX_SIZE = 50_000;

    private final SimpleVectorStore delegate;
    private final Map<String, Map<Object, Set<String>>> metadataIndexes;
    private final Map<String, Document> documentCache;

    public CustomSimpleVectorStore(EmbeddingModel embeddingModel) {
        this.delegate = SimpleVectorStore.builder(embeddingModel).build();
        this.metadataIndexes = new ConcurrentHashMap<>();
        this.documentCache = new ConcurrentHashMap<>();
    }

    public void add(List<Document> documents) {
        delegate.add(documents);
        indexDocuments(documents);
    }

    public List<Document> similaritySearch(SearchRequest request) {
        return delegate.similaritySearch(request);
    }

    public void save(File file) {
        delegate.save(file);
    }

    public void load(File file) {
        delegate.load(file);
        rebuildIndexes();
    }

    /**
     * Clears all documents from the in-memory store and metadata indexes.
     * Must be called before re-indexing to prevent accumulation of stale documents.
     */
    public void clear() {
        List<String> allIds = List.copyOf(documentCache.keySet());
        if (!allIds.isEmpty()) {
            delegate.delete(allIds);
        }
        metadataIndexes.clear();
        documentCache.clear();
    }

    /**
     * Returns {@code true} if the store contains at least one document.
     */
    public boolean isEmpty() {
        return documentCache.isEmpty();
    }

    public List<Document> findByMetadata(String metadataKey, Object value) {
        Set<String> docIds = metadataIndexes
                .getOrDefault(metadataKey, Collections.emptyMap())
                .getOrDefault(value, Collections.emptySet());

        return docIds.stream()
                .map(documentCache::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Document> findByPageNumber(int pageNumber) {
        return findByMetadata("page", pageNumber);
    }

    private void indexDocuments(List<Document> documents) {
        for (Document doc : documents) {
            documentCache.put(doc.getId(), doc);
            doc.getMetadata().forEach((key, value) -> {
                if (value != null) {
                    metadataIndexes.computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                            .computeIfAbsent(value, v -> ConcurrentHashMap.newKeySet())
                            .add(doc.getId());
                }
            });
        }
    }

    /**
     * Rebuilds the metadata indexes from the documents loaded into the delegate store.
     *
     * <p>Uses {@code similarityThreshold(0.0)} to include all documents, and caps at
     * {@link #MAX_INDEX_SIZE} to avoid excessive heap allocation.
     */
    private void rebuildIndexes() {
        metadataIndexes.clear();
        documentCache.clear();

        List<Document> allDocs = delegate.similaritySearch(
                SearchRequest.builder()
                        .query("")
                        .topK(MAX_INDEX_SIZE)
                        .similarityThreshold(0.0)
                        .build());

        indexDocuments(allDocs);
    }
}
