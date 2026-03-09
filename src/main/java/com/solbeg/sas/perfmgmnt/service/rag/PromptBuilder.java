package com.solbeg.sas.perfmgmnt.service.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds structured chat messages for LLM queries from user questions and retrieved documents.
 *
 * <p>Produces a {@link ChatMessages} record with separate {@code system} and {@code user}
 * messages, matching the OpenRouter / OpenAI Chat Completions API format. Keeping the
 * system prompt and user content separate allows models to apply different processing
 * to behavioral instructions vs. the actual query and context.
 */
@Component
@Slf4j
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
        Answer the user's question using ONLY information from the provided text.
        Instructions:
        - Extract information that directly answers the question.
        - Provide ONLY relevant information. Do NOT include extra context or notes.
        - If the question is about errors, issues, problems, bugs, or technical help - look ONLY for Support section information.
        - If the question is about inbox, notifications, or requests - look for Inbox module information.
        - If the question is about how to schedule, launch, start, create, or request a review or sync-up:
          * Look for MANUAL steps (Request Review button, Employee profile, overlay, select type, set date, SAVE)
          * Provide step-by-step instructions with specific actions
          * For People Partner: same process as Manager (Employee List -> Employee profile -> Request Review)
          * Ignore any automatic generation information in the context unless explicitly asked
        - If the question is about peer selection - focus on Manager's actions for selecting peers from dropdown.
        - If the question is about reminding peers to submit their review - look for Review form submission tab and REMIND button information.
        - If the question is about growth plan, tasks, progress, statuses:
          * Look ONLY for Growth Plan section information.
          * Provide step-by-step instructions with specific actions (click Add, select status, SAVE, etc.)
          * Differentiate by roles: Employee vs Manager/People Partner
          * If role not specified, assume Employee role and provide instructions accordingly.
          * Include how to create tasks (from review or manually), update progress, change status, edit, cancel.
          * Ignore review process details unless directly related to moving competencies to tasks.
        - Keep answers concise with actionable steps.
        - Match key terms from the question with context.
        - DO NOT add information not in the text.
        - DO NOT include "Also note that..." sections.
        - If unrelated to User Guide - write: "Not specified in the User Guide".
        """;

    /**
     * Builds structured chat messages for the OpenRouter Chat Completions API.
     *
     * @param userQuestion the user's question
     * @param contextDocs  the list of context documents retrieved from the vector store
     * @return {@link ChatMessages} containing the system prompt and user content
     */
    public ChatMessages build(String userQuestion, List<Document> contextDocs) {
        StringBuilder context = new StringBuilder();
        for (Document document : contextDocs) {
            Object page = document.getMetadata().getOrDefault("page", "N/A");
            context.append("Page ").append(page).append(":\n")
                    .append(document.getText()).append("\n\n");
        }

        String userContent = "User Question:\n" + userQuestion + "\n\n"
                + "Context (User Guide excerpts):\n" + context;

        log.debug("Built chat messages: {} context docs, user content length={}",
                contextDocs.size(), userContent.length());

        return new ChatMessages(SYSTEM_PROMPT, userContent);
    }

    /**
     * Holds the system and user messages for a single LLM request.
     *
     * @param systemMessage behavioral instructions for the assistant (sent as {@code "system"} role)
     * @param userMessage   the user question and RAG context (sent as {@code "user"} role)
     */
    public record ChatMessages(String systemMessage, String userMessage) {}
}
