package com.solbeg.sas.perfmgmnt.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.solbeg.sas.perfmgmnt.model.BotFeedbackType;

import java.time.LocalDateTime;

/**
 * DTO for returning saved bot feedback to the caller.
 */
public record BotFeedbackResponse(
        Long id,
        String question,
        String answer,
        BotFeedbackType botFeedbackType,
        String employeeEmail,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDateTime createdAt
) {}
