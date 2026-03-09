package com.solbeg.sas.perfmgmnt.controller;

import com.solbeg.sas.perfmgmnt.dto.request.LlmRequest;
import com.solbeg.sas.perfmgmnt.dto.response.LlmResponse;
import com.solbeg.sas.perfmgmnt.service.rag.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Question for LLM")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class BotQueryController {

    private final RagService ragService;

    @Operation(summary = "Ask LLM")
    @PostMapping("/ask")
    public ResponseEntity<LlmResponse> ask(@Valid @RequestBody LlmRequest request) {
        String answer = ragService.answer(request.question());
        return ResponseEntity.ok(new LlmResponse(answer));
    }
}
