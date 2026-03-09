package com.solbeg.sas.perfmgmnt.exceptionhandler;

import com.solbeg.sas.perfmgmnt.exceptionhandler.exception.CodedException;
import com.solbeg.sas.perfmgmnt.exceptionhandler.exception.LlmException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    private static final String ERROR_CODE = "errorCode"; 
    private  static final String MESSAGE = "message";

    @ExceptionHandler(CodedException.class)
    public ResponseEntity<Map<String, Object>> handleCodedException(CodedException ex) {
        log.error("CodedException: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR_CODE, ex.getErrorCode());
        body.put(MESSAGE, ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus()).body(body);
    }

    @ExceptionHandler(LlmException.class)
    public ResponseEntity<Map<String, Object>> handleLlmException(LlmException ex) {
        log.error("LLM service error: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ERROR_CODE, "LLM_ERROR");
        body.put(MESSAGE, "AI service temporarily unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(MESSAGE, "Validation failed");
        body.put("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(MESSAGE, "Internal server error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
