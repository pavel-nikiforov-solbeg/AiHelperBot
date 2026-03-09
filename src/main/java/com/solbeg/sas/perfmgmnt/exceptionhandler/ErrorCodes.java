package com.solbeg.sas.perfmgmnt.exceptionhandler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCodes {

    BOT_FEEDBACK_NOT_FOUND("300000", "Bot feedback not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
