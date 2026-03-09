package com.solbeg.sas.perfmgmnt.service.rag.validation;

import com.solbeg.sas.perfmgmnt.config.properties.RagValidationProperties;
import com.solbeg.sas.perfmgmnt.service.rag.EmbeddingIndexer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Third and final filter in the validation pipeline. Runs an embedding similarity search against
 * the vector store to determine whether the input is topically relevant to the indexed User Guide.
 */
@Component
@Order(3)
@Slf4j
public class DomainRelevanceChecker implements InputFilter {

    private final EmbeddingIndexer embeddingIndexer;
    private final double minScore;
    private final int maxSearchResults;

    public DomainRelevanceChecker(
            EmbeddingIndexer embeddingIndexer,
            RagValidationProperties properties) {
        this.embeddingIndexer = embeddingIndexer;
        this.minScore = properties.getDomainChecker().getMinScore();
        this.maxSearchResults = properties.getDomainChecker().getMaxSearchResults();
    }

    @Override
    public FilterResult check(String input) {
        List<Document> results;
        try {
            results = embeddingIndexer.search(input, maxSearchResults);
        } catch (Exception e) {
            log.warn("Vector store search failed during domain relevance check, allowing input through: {}", e.getMessage());
            return FilterResult.pass();
        }

        if (results == null || results.isEmpty()) {
            return FilterResult.fail("Question appears to be outside scope of User Guide");
        }

        Document topResult = results.get(0);
        Double score = topResult.getScore();

        if (score == null) {
            return FilterResult.fail("Unable to determine relevance to User Guide");
        }

        if (score < minScore) {
            return FilterResult.fail("Question appears to be outside scope of User Guide");
        }

        return FilterResult.pass();
    }
}
