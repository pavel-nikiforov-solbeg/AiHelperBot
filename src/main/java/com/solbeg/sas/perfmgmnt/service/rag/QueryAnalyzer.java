package com.solbeg.sas.perfmgmnt.service.rag;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Analyzes user queries to determine their type and extract relevant information.
 * Helps optimize document retrieval strategy based on query intent.
 */
@Component
public class QueryAnalyzer {

    private static final Pattern DEFINITION_PATTERN = Pattern.compile(
            "(?i)\\b(what is|what does|who is|define|what mean)\\b"
    );
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?i)\\b(when|date|deadline|schedule|time|first)\\b"
    );
    private static final Pattern SUPPORT_PATTERN = Pattern.compile(
            "(?i)\\b(error|issue|problem|help|support|technical|bug|not working|doesn't work|contact)\\b"
    );
    private static final Pattern INBOX_PATTERN = Pattern.compile(
            "(?i)\\b(inbox|notification|notifications|request|requests|find.*request|where.*request|see.*request|check.*request|view.*request|sent|approve|deny|open issue|suggest peer|remind|get to inbox|access inbox|search.*inbox|comment.*inbox|set up.*notification|filter.*notification|mobile.*inbox|history.*request|cancel.*request|edit.*request|overdue.*request|anonymous.*notification)\\b"
    );
    private static final Pattern PEERS_PATTERN = Pattern.compile(
            "(?i)\\b(assign.*peer|select.*peer|choose.*peer|find.*peer|peer.*assessment|peer.*selection|suggest.*peer|peers for assessment|select peers|where.*find.*peer)\\b"
    );
    private static final Pattern REVIEW_PROCESS_PATTERN = Pattern.compile(
            "(?i)\\b(how.*schedule|how.*launch|how.*start|how.*create|how.*request|how.*initiate|schedule.*review|schedule.*sync.?up|launch.*review|launch.*sync.?up|start.*review|start.*sync.?up|create.*review|create.*sync.?up|request.*review|request.*sync.?up|initiate.*review|initiate.*sync.?up)\\b"
    );
    private static final Pattern REMIND_PEERS_PATTERN = Pattern.compile(
            "(?i)\\b(remind.*peer|remind.*peers|remind.*submission|REMIND.*button)\\b"
    );
    private static final Pattern GROWTH_PLAN_PATTERN = Pattern.compile(
            "(?i)\\b(growth plan|growthplan|tasks?\\b|progress|status|move competence|add task|update task|change status|edit task|cancel task|how to go through growth plan)\\b"
    );
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("^(a |the )", Pattern.CASE_INSENSITIVE);
    private static final int MAX_TERM_WORDS = 3;

    private static final List<String> DEFINITION_PHRASES = List.of(
            "what is",
            "what does",
            "who is",
            "define",
            "what mean"
    );

    private static final List<String> TIME_PHRASES = List.of(
            "when is",
            "what is the date",
            "what is the deadline",
            "what is the schedule",
            "what time",
            "how long",
            "how much time"
    );

    /**
     * Analyzes the query to determine its type.
     * Priority order: SUPPORT > REVIEW_PROCESS > PEERS > REMIND_PEERS > INBOX > GROWTH_PLAN > TIME_RELATED > DEFINITION > GENERAL.
     *
     * @param query the user's query
     * @return the determined query type
     */
    public QueryType analyze(String query) {
        if (SUPPORT_PATTERN.matcher(query).find()) {
            return QueryType.SUPPORT;
        }

        if (REVIEW_PROCESS_PATTERN.matcher(query).find()) {
            return QueryType.REVIEW_PROCESS;
        }

        if (PEERS_PATTERN.matcher(query).find()) {
            return QueryType.PEERS;
        }

        if (REMIND_PEERS_PATTERN.matcher(query).find()) {
            return QueryType.REMIND_PEERS;
        }

        if (INBOX_PATTERN.matcher(query).find()) {
            return QueryType.INBOX;
        }

        if (GROWTH_PLAN_PATTERN.matcher(query).find()) {
            return QueryType.GROWTH_PLAN;
        }

        if (TIME_PATTERN.matcher(query).find()) {
            return QueryType.TIME_RELATED;
        }

        if (DEFINITION_PATTERN.matcher(query).find()) {
            String term = extractTerm(query, false);
            return isValidDefinitionTerm(term) ? QueryType.DEFINITION : QueryType.GENERAL;
        }

        return QueryType.GENERAL;
    }

    /**
     * Extracts the search term from a definition or time-related query.
     * Removes question marks and leading articles.
     *
     * @param query the user's query
     * @param isTimeQuery flag indicating if it's a time-related query
     * @return the extracted term or the original query if extraction fails
     */
    public String extractTerm(String query, boolean isTimeQuery) {
        String lowerQuery = query.toLowerCase();

        int startIndex = findTermStartIndex(lowerQuery, isTimeQuery);
        if (startIndex == -1) {
            return query;
        }

        return cleanTerm(query.substring(startIndex));
    }

    /**
     * Extracts the search term from a definition query (non-time version).
     * Delegates to the general extractTerm with isTimeQuery=false.
     *
     * @param query the user's query
     * @return the extracted term or the original query if extraction fails
     */
    public String extractTerm(String query) {
        return extractTerm(query, false);
    }

    /**
     * Finds the starting index of the term in a definition or time query.
     *
     * @param lowerQuery the lowercase query string
     * @param isTimeQuery flag indicating if it's a time-related query
     * @return the start index of the term, or -1 if not found
     */
    private int findTermStartIndex(String lowerQuery, boolean isTimeQuery) {
        List<String> phrases = isTimeQuery ? TIME_PHRASES : DEFINITION_PHRASES;
        return phrases.stream()
                .filter(lowerQuery::contains)
                .findFirst()
                .map(phrase -> lowerQuery.indexOf(phrase) + phrase.length() + 1)
                .orElse(-1);
    }

    /**
     * Cleans the extracted term by removing question marks and articles.
     *
     * @param rawTerm the raw extracted term
     * @return the cleaned term
     */
    private String cleanTerm(String rawTerm) {
        return ARTICLE_PATTERN.matcher(rawTerm.trim().replace("?", ""))
                .replaceFirst("")
                .trim();
    }

    /**
     * Validates if the extracted term is suitable for a definition or time query.
     * Terms longer than the maximum word count are considered too complex.
     *
     * @param term the extracted term
     * @return true if the term is valid for specialized search, false otherwise
     */
    private boolean isValidDefinitionTerm(String term) {
        return term.split("\\s+").length <= MAX_TERM_WORDS;
    }
}
