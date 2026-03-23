package com.zjcxph.imgapi.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PdfService {
    public boolean createPdfFromImages(String outputPath, List<String> imagePathList) {
        try {
            // 创建 PDF
            PdfDocument pdfDoc = new PdfDocument(new PdfWriter(outputPath));
            Document doc = new Document(pdfDoc);

            // Add images to the PDF
            for (String imagePath : imagePathList) {
                Image img = new Image(ImageDataFactory.create(imagePath));
                doc.add(img);
            }

            doc.close();
            return true;

        } catch (Exception e) {
            System.out.println("Error creating PDF: " + e.getMessage());
            return false;
        }


    }
}