package com.solbeg.sas.perfmgmnt.mapper;

import com.solbeg.sas.perfmgmnt.dto.request.BotFeedbackRequest;
import com.solbeg.sas.perfmgmnt.dto.response.BotFeedbackResponse;
import com.solbeg.sas.perfmgmnt.model.BotFeedback;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BotFeedbackMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "employeeEmail", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "answer", ignore = true)
    BotFeedback toEntity(BotFeedbackRequest dto);

    BotFeedbackResponse toDto(BotFeedback entity);
}
