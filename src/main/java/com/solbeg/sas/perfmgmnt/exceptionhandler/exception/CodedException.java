package com.solbeg.sas.perfmgmnt.exceptionhandler.exception;

import com.solbeg.sas.perfmgmnt.exceptionhandler.ErrorCodes;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;

@Getter
@Setter
public class CodedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 182214224795326875L;

    private final int httpStatus;
    private final String errorCode;

    public CodedException(String message, int httpStatus, String errorCode) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public CodedException(ErrorCodes errorCode, boolean writableStackTrace) {
        super(errorCode.getMessage(), null, true, writableStackTrace);
        this.httpStatus = errorCode.getHttpStatus().value();
        this.errorCode = errorCode.getCode();
    }

    public CodedException(ErrorCodes errorCode) {
        super(errorCode.getMessage());
        this.httpStatus = errorCode.getHttpStatus().value();
        this.errorCode = errorCode.getCode();
    }
}
