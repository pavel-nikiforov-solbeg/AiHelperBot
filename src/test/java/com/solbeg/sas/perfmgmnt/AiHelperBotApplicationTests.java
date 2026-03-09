package com.solbeg.sas.perfmgmnt;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiHelperBotApplicationTests {

    // CustomSimpleVectorStore requires EmbeddingModel; mock it so the test
    // context doesn't need a running Ollama instance.
    @MockBean
    EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
    }
}
