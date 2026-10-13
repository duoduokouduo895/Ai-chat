package org.example.ai_agent.document.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import dev.langchain4j.data.document.DocumentParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.tika.Tika;
import org.junit.jupiter.api.Test;
import org.example.ai_agent.knowledge.config.KnowledgeBaseConfig;

class ApacheTikaDocumentParserCompatibilityTest {

    private final DocumentParser parser = new KnowledgeBaseConfig().documentParser();

    @Test
    void resolvesTheTikaVersionManagedByLangChain4j() {
        assertThat(Tika.class.getPackage().getImplementationVersion()).isEqualTo("3.2.3");
    }

    @Test
    void parsesPlainTextWithTheLangChain4jManagedTikaVersion() {
        var input = new ByteArrayInputStream("Tika 3 plain text compatibility".getBytes(StandardCharsets.UTF_8));

        assertThat(parser.parse(input).text()).contains("Tika 3 plain text compatibility");
    }

    @Test
    void parsesDocxWithTheLangChain4jManagedTikaVersion() throws IOException {
        var input = new ByteArrayInputStream(docx("Tika 3 DOCX compatibility"));

        assertThat(parser.parse(input).text()).contains("Tika 3 DOCX compatibility");
    }

    @Test
    void parsesPdfWithTheLangChain4jManagedTikaVersion() throws IOException {
        var input = new ByteArrayInputStream(pdf("Tika 3 PDF compatibility"));

        assertThat(parser.parse(input).text()).contains("Tika 3 PDF compatibility");
    }

    private static byte[] docx(String text) throws IOException {
        try (var output = new ByteArrayOutputStream(); var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private static byte[] pdf(String text) throws IOException {
        try (var output = new ByteArrayOutputStream(); var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
