package com.solbeg.sas.perfmgmnt.exceptionhandler.exception;

import com.solbeg.sas.perfmgmnt.exceptionhandler.ErrorCodes;

import java.io.Serial;

public class RestException extends CodedException {

    @Serial
    private static final long serialVersionUID = 7587934933756030867L;

    public RestException(String message, int httpStatus, String errorCode) {
        super(message, httpStatus, errorCode);
    }

    public RestException(ErrorCodes errorCode, boolean writableStackTrace) {
        super(errorCode, writableStackTrace);
    }

    public RestException(ErrorCodes errorCode) {
        super(errorCode);
    }
}
