package com.solbeg.sas.perfmgmnt.service;

import com.solbeg.sas.perfmgmnt.dto.request.BotFeedbackRequest;
import com.solbeg.sas.perfmgmnt.dto.response.BotFeedbackResponse;

public interface BotFeedbackService {

    BotFeedbackResponse save(BotFeedbackRequest request);

    BotFeedbackResponse findById(Long id);
}
