package com.solbeg.sas.perfmgmnt.service.rag.validation;

/**
 * Strategy interface for a single step in the RAG input validation pipeline.
 *
 * <p>Implementations are ordered via {@code @Order} and collected by {@link InputValidator}.
 */
public interface InputFilter {

    /**
     * Evaluates the given input string.
     *
     * @param input the raw user input to evaluate
     * @return a {@link FilterResult} indicating whether the input passed or failed this filter
     */
    FilterResult check(String input);
}
