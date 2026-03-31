package ai.workley.core.chat.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class FileTextExtractor {
    private static final Logger log = LoggerFactory.getLogger(FileTextExtractor.class);

    private final int maxLength;

    public FileTextExtractor(@Value("${attachment.extracted-text-max-length:50000}") int maxLength) {
        this.maxLength = maxLength;
    }

    public String extract(byte[] data, String mimeType) {
        try {
            String text = switch (mimeType) {
                case "application/pdf" -> extractPdf(data);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> extractDocx(data);
                default -> null;
            };
            if (text != null && text.length() > maxLength) {
                text = text.substring(0, maxLength);
            }
            return text;
        } catch (Exception e) {
            log.warn("Failed to extract text from file (mimeType={})", mimeType, e);
            return null;
        }
    }

    private String extractPdf(byte[] data) throws Exception {
        try (PDDocument document = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocx(byte[] data) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
