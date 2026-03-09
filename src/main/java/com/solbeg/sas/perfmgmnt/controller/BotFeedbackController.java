package com.solbeg.sas.perfmgmnt.controller;

import com.solbeg.sas.perfmgmnt.dto.request.BotFeedbackRequest;
import com.solbeg.sas.perfmgmnt.dto.response.BotFeedbackResponse;
import com.solbeg.sas.perfmgmnt.service.BotFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/botfeedback")
@Tag(name = "Bot Feedback")
public class BotFeedbackController {

    private final BotFeedbackService botFeedbackService;

    @Operation(summary = "Save bot feedback")
    @ApiResponse(responseCode = "201", description = "Bot feedback saved")
    @PostMapping()
    public ResponseEntity<BotFeedbackResponse> saveBotFeedback(@Valid @RequestBody BotFeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(botFeedbackService.save(request));
    }

    @Operation(summary = "Get bot feedback by id")
    @ApiResponse(responseCode = "200", description = "Bot feedback found")
    @ApiResponse(responseCode = "404", description = "Bot feedback not found")
    @GetMapping("/{id}")
    public ResponseEntity<BotFeedbackResponse> getBotFeedbackById(@PathVariable Long id) {
        return ResponseEntity.ok(botFeedbackService.findById(id));
    }
}
