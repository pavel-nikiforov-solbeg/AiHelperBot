package com.solbeg.sas.perfmgmnt.dto.request;

import com.solbeg.sas.perfmgmnt.model.BotFeedbackType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO for submitting bot feedback (thumbs up/down) from the user.
 *
 * @param question       the original question asked (max 4000 chars, matching DB column)
 * @param answer         the bot answer being rated (max 10000 chars, matching DB column)
 * @param botFeedbackType LIKE or DISLIKE
 */
public record BotFeedbackRequest(
        @NotBlank(message = "Question is required")
        @Size(max = 4000, message = "Question too long")
        String question,

        @NotBlank(message = "Answer required")
        @Size(max = 10000, message = "Answer too long")
        String answer,

        @NotNull
        BotFeedbackType botFeedbackType
) {}
