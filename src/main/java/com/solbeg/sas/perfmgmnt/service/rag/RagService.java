package com.solbeg.sas.perfmgmnt.service.rag;

import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;
import com.solbeg.sas.perfmgmnt.exceptionhandler.exception.LlmException;
import com.solbeg.sas.perfmgmnt.service.rag.validation.InputValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for handling Retrieval-Augmented Generation (RAG) queries. Coordinates document
 * retrieval, prompt building, and LLM interaction to answer user questions based on indexed
 * documents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private static final String DEFAULT_RESPONSE = "Not specified in the User Guide";
    private static final String ERROR_RESPONSE =
            "AI Helper Bot temporarily unavailable. Check application logs for details.";

    private final QueryAnalyzer queryAnalyzer;
    private final DocumentRetriever documentRetriever;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final RagProperties properties;
    private final EmbeddingIndexer indexer;
    private final InputValidator inputValidator;

    /**
     * Answers a user question using Retrieval-Augmented Generation. Analyzes the query type,
     * retrieves relevant documents, and generates an answer using the LLM.
     *
     * @param question the user's question
     * @return the answer from the LLM, a default message if no relevant documents found,
     *         or an error message if the LLM is unavailable
     */
    public String answer(String question) {
        log.info("==== RAG Query Start ====");

        if (!indexer.isLoaded()) {
            log.warn("AI Helper Bot unavailable: vector store not loaded");
            return ERROR_RESPONSE;
        }

        Optional<String> validationError = inputValidator.validate(question);
        if (validationError.isPresent()) {
            log.info("Input did not pass validation pipeline: {}", validationError.get());
            return validationError.get();
        }

        log.debug("Question: {}", question);

        QueryType queryType = queryAnalyzer.analyze(question);
        log.debug("Query type analyzed: {}", queryType);

        List<Document> documents =
                documentRetriever.retrieve(
                        question, queryType, properties.getLlm().getMaxDocuments());
        log.debug("Retrieved documents size: {}", documents.size());

        if (documents.isEmpty()) {
            log.warn("No documents found, returning default response");
            return DEFAULT_RESPONSE;
        }

        try {
            String answer = generateAnswer(question, documents);

            if (isAnswerUnsatisfactory(answer) && queryType == QueryType.DEFINITION) {
                log.debug("Answer unsatisfactory for DEFINITION query, retrying with GENERAL");
                answer = retryWithFallbackStrategy(question);
            }

            log.info("==== RAG Query End ====");
            return answer;

        } catch (LlmException e) {
            log.error("LLM call failed for question: {}", question, e);
            return ERROR_RESPONSE;
        }
    }

    private String generateAnswer(String question, List<Document> documents) {
        PromptBuilder.ChatMessages messages = promptBuilder.build(question, documents);
        return llmClient.ask(messages.systemMessage(), messages.userMessage());
    }

    private boolean isAnswerUnsatisfactory(String answer) {
        String lowerAnswer = answer.toLowerCase();
        return properties.getLlm().getUnsatisfactoryPhrases().stream()
                .anyMatch(lowerAnswer::contains);
    }

    private String retryWithFallbackStrategy(String question) {
        List<Document> fallbackDocuments =
                documentRetriever.retrieve(
                        question, QueryType.GENERAL, properties.getLlm().getMaxDocuments());

        if (fallbackDocuments.isEmpty()) {
            return DEFAULT_RESPONSE;
        }
        return generateAnswer(question, fallbackDocuments);
    }
}
