package com.solbeg.sas.perfmgmnt.service;

import com.solbeg.sas.perfmgmnt.config.properties.BotProperties;
import com.solbeg.sas.perfmgmnt.dto.response.BotTopicsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotTopicsServiceImpl implements BotTopicsService {

    private final BotProperties botProperties;

    @Override
    public BotTopicsResponse getIntroAndTopics() {
        log.info("Returning intro text");
        return new BotTopicsResponse(botProperties.getIntroText());
    }
}
