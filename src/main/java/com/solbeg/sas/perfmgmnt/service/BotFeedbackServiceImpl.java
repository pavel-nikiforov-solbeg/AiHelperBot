package com.solbeg.sas.perfmgmnt.service;

import com.solbeg.sas.perfmgmnt.dto.request.BotFeedbackRequest;
import com.solbeg.sas.perfmgmnt.dto.response.BotFeedbackResponse;
import com.solbeg.sas.perfmgmnt.exceptionhandler.ErrorCodes;
import com.solbeg.sas.perfmgmnt.exceptionhandler.exception.RestException;
import com.solbeg.sas.perfmgmnt.mapper.BotFeedbackMapper;
import com.solbeg.sas.perfmgmnt.model.BotFeedback;
import com.solbeg.sas.perfmgmnt.repository.BotFeedbackRepository;
import com.solbeg.sas.perfmgmnt.service.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BotFeedbackServiceImpl implements BotFeedbackService {

    private final BotFeedbackRepository botFeedbackRepository;
    private final BotFeedbackMapper botFeedbackMapper;
    private final SecurityUtils securityUtils;

    @Transactional
    public BotFeedbackResponse save(BotFeedbackRequest request) {
        BotFeedback entity = botFeedbackMapper.toEntity(request);
        entity.setAnswer(request.answer());
        entity.setEmployeeEmail(securityUtils.getUserName());

        entity = botFeedbackRepository.save(entity);
        return botFeedbackMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public BotFeedbackResponse findById(Long id) {
        return botFeedbackRepository.findById(id)
                .map(botFeedbackMapper::toDto)
                .orElseThrow(() -> new RestException(ErrorCodes.BOT_FEEDBACK_NOT_FOUND));
    }
}
