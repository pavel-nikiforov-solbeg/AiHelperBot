package com.solbeg.sas.perfmgmnt.controller;

import com.solbeg.sas.perfmgmnt.dto.response.BotTopicsResponse;
import com.solbeg.sas.perfmgmnt.service.BotTopicsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bot intro and topics")
@RestController
@RequestMapping("/api/v1/bot")
@RequiredArgsConstructor
public class BotTopicsController {

    private final BotTopicsService botTopicsService;

    @Operation(summary = "Get bot intro text and available topics")
    @GetMapping("/topics")
    public ResponseEntity<BotTopicsResponse> getIntroAndTopics() {
        return ResponseEntity.ok(botTopicsService.getIntroAndTopics());
    }
}
