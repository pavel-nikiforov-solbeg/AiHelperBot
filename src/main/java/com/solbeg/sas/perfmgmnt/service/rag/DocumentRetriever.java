package com.solbeg.sas.perfmgmnt.service.rag;

import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/**
 * Retrieves and ranks documents based on query type and relevance. Implements different retrieval
 * strategies for different query types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentRetriever {

    private static final int CANDIDATE_BUFFER = 10;
    private static final String GLOSSARY = "glossary";

    private final EmbeddingIndexer indexer;
    private final QueryAnalyzer queryAnalyzer;
    private final DocumentRanker documentRanker;
    private final RagProperties properties;

    /**
     * Retrieves relevant documents based on query type.
     *
     * @param query the user's query
     * @param queryType the type of query
     * @param maxDocuments maximum number of documents to return
     * @return list of relevant documents
     */
    public List<Document> retrieve(String query, QueryType queryType, int maxDocuments) {
        return switch (queryType) {
            case DEFINITION -> retrieveForDefinition(query, maxDocuments);
            case TIME_RELATED -> retrieveForTimeRelated(query, maxDocuments);
            case GENERAL -> retrieveForGeneral(query, maxDocuments);
            case SUPPORT -> retrieveForSupport(query, maxDocuments);
            case INBOX -> retrieveForInbox(query, maxDocuments);
            case PEERS -> retrieveForPeers(query, maxDocuments);
            case REVIEW_PROCESS -> retrieveForReviewProcess(query, maxDocuments);
            case REMIND_PEERS -> retrieveForRemindPeers(query, maxDocuments);
            case GROWTH_PLAN -> retrieveForGrowthPlan(query, maxDocuments);
        };
    }

    private List<Document> retrieveForDefinition(String query, int maxDocuments) {
        String term = queryAnalyzer.extractTerm(query);
        String searchQuery = GlossaryTerms.contains(term)
                ? "Glossary definition of " + term
                : term + " definition meaning";

        List<Document> candidates = searchCandidates(searchQuery, maxDocuments);
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Document> glossaryDocs = filterGlossaryDocuments(candidates, maxDocuments);
        return documentRanker.mergeWithGlossaryPage(
                glossaryDocs, properties.getGuide().getPages().getGlossary(), maxDocuments);
    }

    private List<Document> retrieveForTimeRelated(String query, int maxDocuments) {
        String term = queryAnalyzer.extractTerm(query, true);
        String searchQuery =
                "Time-related information about "
                        + term
                        + " date deadline schedule period Q1 Q2 Q3 Q4 launch launched scheduled";
        String rankingQuery = "date deadline schedule period Q1 Q2 Q3 Q4 launch launched scheduled";

        List<Document> candidates = searchCandidates(searchQuery, maxDocuments + CANDIDATE_BUFFER);
        return documentRanker.rankByTimeRelevance(candidates, rankingQuery, maxDocuments);
    }

    private List<Document> retrieveForGeneral(String query, int maxDocuments) {
        List<Document> candidates = searchCandidates(query, maxDocuments + CANDIDATE_BUFFER);
        return documentRanker.rankByKeywords(candidates, query, maxDocuments);
    }

    private List<Document> retrieveForSupport(String query, int maxDocuments) {
        String searchQuery = query + " support contact help error issue problem bug";
        List<Document> docs = indexer.search(searchQuery, maxDocuments + CANDIDATE_BUFFER);
        return documentRanker.mergeWithSupportPage(
                docs,
                properties.getGuide().getPages().getSupport(),
                maxDocuments);
    }

    private List<Document> retrieveForInbox(String query, int maxDocuments) {
        List<Document> candidates = searchCandidates(query, maxDocuments + CANDIDATE_BUFFER);
        return documentRanker.mergeWithInboxPage(
                candidates, properties.getGuide().getPages().getInbox(), maxDocuments);
    }

    private List<Document> retrieveForPeers(String query, int maxDocuments) {
        String searchQuery =
                query
                        + " select peers suggest peer choose peer peer assessment peer selection from dropdown manager actions";
        List<Document> candidates = searchCandidates(searchQuery, maxDocuments + CANDIDATE_BUFFER);
        return documentRanker.rankByKeywords(
                candidates,
                "select peers suggest peer choose peer peer assessment peer selection from dropdown manager actions",
                maxDocuments);
    }

    private List<Document> retrieveForReviewProcess(String query, int maxDocuments) {
        String lowerQueryForCheck = query.toLowerCase();
        boolean isAutoGenerationQuery =
                lowerQueryForCheck.contains("automatic")
                        || lowerQueryForCheck.contains("toggle")
                        || lowerQueryForCheck.contains("generate review");

        String searchQuery =
                query
                        + " request review button employee profile overlay select type set date SAVE people partner manager employee list";
        String rankingQuery =
                "request review button employee profile overlay select type set date SAVE people partner manager employee list";

        List<Document> candidates = searchCandidates(searchQuery, maxDocuments + CANDIDATE_BUFFER);
        log.info(
                "Found {} candidates for REVIEW_PROCESS query (auto: {})",
                candidates.size(),
                isAutoGenerationQuery);

        if (!isAutoGenerationQuery) {
            candidates =
                    candidates.stream()
                            .filter(
                                    doc -> {
                                        String text = doc.getText().toLowerCase();
                                        boolean isAutoDoc =
                                                text.contains("generate review")
                                                        && text.contains("toggle")
                                                        && !text.contains("request review")
                                                        && !text.contains("click");
                                        return !isAutoDoc;
                                    })
                            .toList();
            log.info("After filtering automatic generation docs: {} candidates", candidates.size());
        }

        return documentRanker.rankByKeywords(candidates, rankingQuery, maxDocuments);
    }

    private List<Document> retrieveForRemindPeers(String query, int maxDocuments) {
        String searchQuery =
                query
                        + " remind REMIND button submission tab peers submitted review form row People Partner actions";

        List<Document> candidates = searchCandidates(searchQuery, maxDocuments + CANDIDATE_BUFFER);
        log.info("Found {} candidates for REMIND_PEERS query", candidates.size());

        int remindPage = properties.getGuide().getPages().getRemindSubmission();
        List<Document> remindDocs = indexer.findByPageNumber(remindPage);
        log.info(
                "Found {} documents from remind submission page {}", remindDocs.size(), remindPage);

        List<Document> merged =
                Stream.concat(remindDocs.stream(), candidates.stream()).distinct().toList();

        return documentRanker.rankByKeywords(
                merged, "remind REMIND button submission tab peers review form", maxDocuments);
    }

    private List<Document> retrieveForGrowthPlan(String query, int maxDocuments) {
        String lowerQuery = query.toLowerCase();
        boolean isManager =
                lowerQuery.contains("manager")
                        || lowerQuery.contains("people partner")
                        || lowerQuery.contains("pp");

        String searchQuery =
                query
                        + " growth plan tasks add update edit cancel status progress to do in progress done move competence approve deny change request cancellation request employee manager people partner";
        String rankingQuery =
                "growth plan tasks add update edit cancel status progress to do in progress done move competence approve deny change request cancellation request";

        List<Document> candidates = searchCandidates(searchQuery, maxDocuments + CANDIDATE_BUFFER);
        log.info(
                "Found {} candidates for GROWTH_PLAN query (manager role: {})",
                candidates.size(),
                isManager);

        int page =
                isManager
                        ? properties.getGuide().getPages().getGpManager()
                        : properties.getGuide().getPages().getGpEmployee();

        List<Document> roleDocs = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            roleDocs.addAll(indexer.findByPageNumber(page + i));
        }

        log.info("Found {} documents from growth plan pages starting at {}", roleDocs.size(), page);

        List<Document> merged =
                Stream.concat(roleDocs.stream(), candidates.stream()).distinct().toList();

        return documentRanker.rankByKeywords(merged, rankingQuery, maxDocuments);
    }

    private List<Document> searchCandidates(String searchQuery, int maxDocuments) {
        List<Document> results = indexer.search(searchQuery, maxDocuments + CANDIDATE_BUFFER);
        return results != null ? results : List.of();
    }

    private List<Document> filterGlossaryDocuments(List<Document> candidates, int maxDocuments) {
        List<Document> glossaryDocs =
                candidates.stream()
                        .filter(doc -> doc.getText().toLowerCase().contains(GLOSSARY))
                        .limit(maxDocuments)
                        .toList();

        return glossaryDocs.isEmpty()
                ? candidates.stream().limit(maxDocuments).toList()
                : glossaryDocs;
    }
}
