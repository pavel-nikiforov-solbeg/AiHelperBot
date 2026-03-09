package com.solbeg.sas.perfmgmnt.service.rag.validation;

/**
 * Represents the outcome of a single {@link InputFilter} check.
 *
 * <p>Use the factory methods {@link #pass()} and {@link #fail(String)} to create instances.
 */
public record FilterResult(boolean passed, String reason) {

    public FilterResult {
        if (passed && reason != null) {
            throw new IllegalArgumentException("A passing result must not carry a reason");
        }
        if (!passed && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A failing result must have a non-blank reason");
        }
    }

    public static FilterResult pass() {
        return new FilterResult(true, null);
    }

    public static FilterResult fail(String reason) {
        return new FilterResult(false, reason);
    }
}
