package com.hireai.util;

import com.hireai.exception.FileStorageException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;

@Component
public class TextExtractor {

    public String extract(String filePath) {
        String extension = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "pdf" -> extractFromPdf(filePath);
            case "docx" -> extractFromDocx(filePath);
            default -> throw new FileStorageException("Unsupported file type: " + extension);
        };
    }

    private String extractFromPdf(String filePath) {
        try (PDDocument document = Loader.loadPDF(Path.of(filePath).toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document).trim();
        } catch (IOException e) {
            throw new FileStorageException("Failed to extract text from PDF: " + filePath, e);
        }
    }

    private String extractFromDocx(String filePath) {
        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n")).trim();
        } catch (IOException e) {
            throw new FileStorageException("Failed to extract text from DOCX: " + filePath, e);
        }
    }
}
