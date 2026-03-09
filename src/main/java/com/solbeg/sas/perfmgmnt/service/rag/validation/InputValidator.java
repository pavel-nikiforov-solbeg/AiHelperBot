package com.solbeg.sas.perfmgmnt.service.rag.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the RAG input validation pipeline by running a list of {@link InputFilter}s in
 * order and short-circuiting on the first failure.
 *
 * <p>Spring automatically sorts the injected {@link InputFilter} beans according to their
 * {@code @Order} annotations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InputValidator {

    private final List<InputFilter> filters;

    /**
     * Evaluates the given input against all registered filters in order.
     *
     * @param input the raw user input to validate
     * @return {@link Optional#empty()} if all filters pass; an {@link Optional} containing the
     *     human-readable rejection reason if any filter rejects the input
     */
    public Optional<String> validate(String input) {
        for (InputFilter filter : filters) {
            FilterResult result = filter.check(input);
            if (!result.passed()) {
                log.debug(
                        "Input rejected by {}: {}",
                        filter.getClass().getSimpleName(),
                        result.reason());
                return Optional.of(result.reason());
            }
        }
        return Optional.empty();
    }
}
