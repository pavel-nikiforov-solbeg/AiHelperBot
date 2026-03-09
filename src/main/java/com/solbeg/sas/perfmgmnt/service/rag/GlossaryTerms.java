package com.solbeg.sas.perfmgmnt.service.rag;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Contains glossary terms from the User Guide (page 3).
 * Used to determine if a definition query should use glossary-specific search.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GlossaryTerms {

    private static final Set<String> TERMS = Set.of(
            "review",
            "sync up",
            "growth plan",
            "employee",
            "manager",
            "people partner",
            "pp",
            "bum",
            "business unit manager",
            "peer"
    );

    /**
     * Checks if the given term exists in the glossary.
     * Comparison is case-insensitive and whitespace-trimmed.
     *
     * @param term the term to check
     * @return true if the term is in the glossary, false otherwise
     */
    public static boolean contains(String term) {
        if (term == null || term.isBlank()) {
            return false;
        }
        return TERMS.contains(term.toLowerCase().trim());
    }
}
