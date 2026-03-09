package com.solbeg.sas.perfmgmnt.service.rag;

import java.io.File;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Component;

/**
 * Manages vector store indexing and search.
 *
 * <p>The store is loaded into memory once at startup (via {@link BootstrapIndexRunner}). Subsequent
 * {@link #search} calls query the in-memory store directly — no disk or Blob Storage access occurs
 * per request.
 */
@Component
@RequiredArgsConstructor
public class EmbeddingIndexer {

    private final CustomSimpleVectorStore store;

    /**
     * Indexes documents and persists the store to a local file. The in-memory store is updated
     * immediately via {@code store.add()}. The file is used only for subsequent upload to Blob
     * Storage.
     *
     * @param docs documents to index
     * @param storePath local path to write the serialized store
     */
    public void index(List<Document> docs, String storePath) {
        store.add(docs);
        store.save(new File(storePath));
    }

    /**
     * Loads the store from a local file into memory. Should be called once at application startup
     * by {@link BootstrapIndexRunner}.
     *
     * @param storePath local path to the serialized store file
     */
    public void load(String storePath) {
        store.load(new File(storePath));
    }

    /**
     * Searches the in-memory store. No file or network I/O.
     *
     * @param query search query
     * @param topK maximum number of results
     * @return matching documents
     */
    public List<Document> search(String query, int topK) {
        return store.similaritySearch(SearchRequest.builder().query(query).topK(topK).build());
    }

    public CustomSimpleVectorStore getStore() {
        return store;
    }

    /**
     * Clears all documents from the in-memory store and its metadata indexes. Must be called before
     * rebuilding the index to prevent document duplication when store.add() is called after a
     * previous load().
     */
    public void clear() {
        store.clear();
    }

    /**
     * Returns {@code true} if the in-memory store contains at least one document. Used during
     * startup to decide whether degraded mode is viable when Blob Storage is temporarily
     * unavailable.
     */
    public boolean isLoaded() {
        return !store.isEmpty();
    }

    public List<Document> findByPageNumber(int pageNumber) {
        return store.findByPageNumber(pageNumber);
    }
}
