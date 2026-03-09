package com.solbeg.sas.perfmgmnt.exceptionhandler.exception;

import java.io.Serial;

/**
 * Thrown when the LLM API call fails — network error, HTTP error, or empty/null response.
 *
 * <p>Separates LLM infrastructure failures from application logic, allowing callers
 * to handle them explicitly rather than receiving silent fallback strings.
 */
public class LlmException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = -3812744870645127942L;

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
