package com.solbeg.sas.perfmgmnt.service.rag.validation;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.solbeg.sas.perfmgmnt.config.properties.RagValidationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Second filter in the validation pipeline. Uses the <em>Lingua</em> library to detect the
 * language of the input and rejects it when a non-accepted language is detected with sufficient
 * confidence.
 */
@Component
@Order(2)
public class LanguageDetectorFilter implements InputFilter {

    private final int shortInputThreshold;
    private final Set<Language> acceptedLanguages;
    private final LanguageDetector detector;

    public LanguageDetectorFilter(RagValidationProperties properties, LanguageDetector detector) {
        RagValidationProperties.LanguageDetector config = properties.getLanguageDetector();
        this.acceptedLanguages = parseLanguages(config.getAcceptedLanguages());
        this.shortInputThreshold = config.getShortInputThreshold();
        this.detector = detector;
    }

    private static Set<Language> parseLanguages(String csv) {
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(name -> {
                    try {
                        return Language.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalStateException(
                                "Invalid language name in 'accepted-languages' configuration: '"
                                        + name + "'. Must be a valid Lingua Language enum constant "
                                        + "(e.g. ENGLISH, GERMAN, FRENCH).",
                                e);
                    }
                })
                .collect(Collectors.toSet());
    }

    @Override
    public FilterResult check(String input) {
        if (input == null || input.trim().length() < shortInputThreshold) {
            return FilterResult.pass();
        }

        Language detected = detector.detectLanguageOf(input);

        if (detected == Language.UNKNOWN) {
            return FilterResult.pass();
        }

        if (!acceptedLanguages.contains(detected)) {
            return FilterResult.fail(
                    "Please ask your question in English as the User Guide is available in English");
        }

        return FilterResult.pass();
    }
}
