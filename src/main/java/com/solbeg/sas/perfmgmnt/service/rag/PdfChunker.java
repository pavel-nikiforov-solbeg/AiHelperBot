package com.solbeg.sas.perfmgmnt.service.rag;

import com.solbeg.sas.perfmgmnt.config.properties.RagProperties;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PdfChunker {

    private final RagProperties properties;

    public List<Document> chunk(
            InputStream pdfStream,
            String guideUrl,
            int chunkSize,
            int overlap) throws IOException {

        List<Document> docs = new ArrayList<>();

        try (PDDocument doc = PDDocument.load(pdfStream)) {

            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = doc.getNumberOfPages();

            for (int page = properties.getGuide().getPages().getStart(); page <= totalPages; page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = stripper.getText(doc);
                if (pageText == null || pageText.isBlank()) {
                    continue;
                }

                List<String> paragraphs = Arrays.stream(pageText.split("\\n\\s*\\n"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

                StringBuilder buf = new StringBuilder();

                for (String p : paragraphs) {

                    if (buf.length() + p.length() + 2 <= chunkSize) {
                        buf.append(p).append("\n\n");
                    } else {
                        String content = buf.toString();
                        docs.add(toDoc(content, guideUrl, page));

                        // overlap
                        String tail = content.substring(
                                Math.max(0, content.length() - overlap));

                        buf = new StringBuilder(tail)
                                .append(p)
                                .append("\n\n");
                    }
                }

                if (!buf.isEmpty()) {
                    docs.add(toDoc(buf.toString(), guideUrl, page));
                }
            }
        }

        return docs;
    }

    private Document toDoc(String content, String guideUrl, int page) {
        return new Document(
                content,
                Map.of(
                        "source", guideUrl,
                        "page", page));
    }
}
