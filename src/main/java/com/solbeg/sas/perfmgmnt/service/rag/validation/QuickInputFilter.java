package com.solbeg.sas.perfmgmnt.service.rag.validation;

import com.solbeg.sas.perfmgmnt.config.properties.RagValidationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * First filter in the validation pipeline. Performs cheap, local checks with no external
 * dependencies:
 *
 * <ul>
 *   <li>Rejects {@code null} or blank input.
 *   <li>Rejects input whose trimmed length is below the configured minimum.
 *   <li>Rejects input that contains no Unicode letters (via {@code Character.isLetter}).
 * </ul>
 */
@Component
@Order(1)
public class QuickInputFilter implements InputFilter {

    private final int minLength;

    public QuickInputFilter(RagValidationProperties properties) {
        this.minLength = properties.getQuickFilter().getMinLength();
    }

    @Override
    public FilterResult check(String input) {
        if (input == null || input.isBlank()) {
            return FilterResult.fail("Please enter a question");
        }

        String trimmed = input.trim();

        if (trimmed.length() < minLength) {
            return FilterResult.fail(
                    "Please enter a more detailed question (at least " + minLength + " characters)");
        }

        if (trimmed.chars().noneMatch(Character::isLetter)) {
            return FilterResult.fail("Please enter a valid question with letters");
        }

        return FilterResult.pass();
    }
}
