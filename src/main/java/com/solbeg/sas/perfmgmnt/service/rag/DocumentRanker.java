package com.solbeg.sas.perfmgmnt.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Ranks documents by relevance using various scoring strategies. Provides methods for keyword-based
 * ranking and merging with specific page documents.
 */
@Component
@Slf4j
public class DocumentRanker {

    private final EmbeddingIndexer indexer;

    private static final Pattern TIME_SCORE_PATTERN =
            Pattern.compile(
                    "(?i)\\b(\\d{1,2}/\\d{1,2}/\\d{4}|\\d{1,2} months?|\\d{1,2} "
                            + "days?|deadline|schedule|time|period|Q[1-4]|launch|launched|scheduled)\\b");

    /**
     * Constructs a new DocumentRanker.
     *
     * @param indexer the embedding indexer for accessing the document store
     */
    public DocumentRanker(EmbeddingIndexer indexer) {
        this.indexer = indexer;
    }

    /**
     * Ranks documents by keyword match score. Documents are scored based on the number of unique
     * query words they contain (case-insensitive matching).
     *
     * @param documents the documents to rank
     * @param query the query string
     * @param maxDocuments maximum number of documents to return
     * @return list of documents ranked by keyword relevance
     */
    public List<Document> rankByKeywords(List<Document> documents, String query, int maxDocuments) {
        String[] queryWords = extractQueryWords(query);

        List<Document> ranked =
                documents.stream()
                        .sorted(createKeywordComparator(queryWords))
                        .limit(maxDocuments)
                        .toList();

        log.info("Ranked by keywords size: {}", ranked.size());
        for (int i = 0; i < ranked.size(); i++) {
            Document doc = ranked.get(i);
            Object pageObj = doc.getMetadata().getOrDefault("page", "N/A");
            String page = pageObj != null ? pageObj.toString() : "N/A";
            String shortText =
                    doc.getText().length() > 100
                            ? doc.getText().substring(0, 100) + "..."
                            : doc.getText();
            int score = calculateKeywordScore(doc, queryWords);
            log.debug(
                    "Ranked document #{}: Page={}, Score={}, Short text='{}'",
                    i + 1,
                    page,
                    score,
                    shortText);
        }

        return ranked;
    }

    /**
     * Ranks documents by time-related relevance. Combines keyword scoring with time-specific
     * pattern matching for higher relevance in time queries.
     *
     * @param documents the documents to rank
     * @param query the query string
     * @param maxDocuments maximum number of documents to return
     * @return list of documents ranked by time relevance
     */
    public List<Document> rankByTimeRelevance(
            List<Document> documents, String query, int maxDocuments) {
        String[] queryWords = extractQueryWords(query);

        return documents.stream()
                .sorted(
                        Comparator.comparingInt(
                                        (Document doc) ->
                                                calculateKeywordScore(doc, queryWords)
                                                        + calculateTimeScore(doc))
                                .reversed())
                .limit(maxDocuments)
                .toList();
    }

    /**
     * Merges documents with glossary page documents, removing duplicates. Glossary documents are
     * prepended to the list.
     *
     * @param documents the base documents
     * @param glossaryPage the page number containing glossary section
     * @param maxDocuments maximum number of documents to return
     * @return merged list with glossary documents prioritized
     */
    public List<Document> mergeWithGlossaryPage(
            List<Document> documents, int glossaryPage, int maxDocuments) {
        List<Document> glossaryDocs = indexer.findByPageNumber(glossaryPage);

        return Stream.concat(glossaryDocs.stream(), documents.stream())
                .distinct()
                .limit(maxDocuments)
                .toList();
    }

    /**
     * Merges documents with support page documents, removing duplicates. Support documents are
     * prepended to the list.
     *
     * @param documents the base documents
     * @param supportPage the page number containing support section
     * @param maxDocuments maximum number of documents to return
     * @return merged list with support documents prioritized
     */
    public List<Document> mergeWithSupportPage(
            List<Document> documents, int supportPage, int maxDocuments) {
        List<Document> supportDocs = indexer.findByPageNumber(supportPage);

        return Stream.concat(supportDocs.stream(), documents.stream())
                .distinct()
                .limit(maxDocuments)
                .toList();
    }

    /**
     * Merges documents with inbox page documents, removing duplicates. Inbox documents are
     * prepended to the list.
     *
     * @param documents the base documents
     * @param inboxPage the page number containing inbox section
     * @param maxDocuments maximum number of documents to return
     * @return merged list with inbox documents prioritized
     */
    public List<Document> mergeWithInboxPage(
            List<Document> documents, int inboxPage, int maxDocuments) {
        List<Document> inboxDocs = indexer.findByPageNumber(inboxPage);

        return Stream.concat(inboxDocs.stream(), documents.stream())
                .distinct()
                .limit(maxDocuments)
                .toList();
    }

    private String[] extractQueryWords(String query) {
        return query.toLowerCase().split("\\s+");
    }

    private Comparator<Document> createKeywordComparator(String[] queryWords) {
        return Comparator.comparingInt((Document doc) -> calculateKeywordScore(doc, queryWords))
                .reversed();
    }

    private int calculateKeywordScore(Document document, String[] queryWords) {
        String docText = document.getText().toLowerCase();
        int score = 0;

        for (String word : queryWords) {
            if (docText.contains(word)) {
                score++;
            }
        }

        return score;
    }

    private int calculateTimeScore(Document document) {
        String docText = document.getText();
        var matcher = TIME_SCORE_PATTERN.matcher(docText);
        int score = 0;
        while (matcher.find()) {
            score++;
        }
        return score;
    }
}
