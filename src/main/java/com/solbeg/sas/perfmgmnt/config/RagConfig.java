package com.solbeg.sas.perfmgmnt.config;

import com.solbeg.sas.perfmgmnt.config.properties.LlmProperties;
import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Configuration class for RAG (Retrieval-Augmented Generation) components.
 * Provides {@link RestClient} bean for synchronous HTTP communication with OpenRouter.
 */
@Configuration
public class RagConfig {

    /**
     * Creates a {@link RestClient} pre-configured for the OpenRouter Chat Completions API.
     *
     * <p>Sets the {@code Authorization: Bearer} header, {@code Content-Type: application/json},
     * and optional OpenRouter attribution headers ({@code HTTP-Referer}, {@code X-Title}).
     *
     * @param properties RAG configuration properties containing LLM connection details
     * @return configured {@link RestClient} instance
     */
    @Bean
    public RestClient llmRestClient(RagProperties properties) {
        LlmProperties llm = properties.getLlm();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(llm.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llm.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (llm.getHttpReferer() != null && !llm.getHttpReferer().isBlank()) {
            builder.defaultHeader("HTTP-Referer", llm.getHttpReferer());
        }
        if (llm.getAppTitle() != null && !llm.getAppTitle().isBlank()) {
            builder.defaultHeader("X-Title", llm.getAppTitle());
        }

        return builder.build();
    }
}
