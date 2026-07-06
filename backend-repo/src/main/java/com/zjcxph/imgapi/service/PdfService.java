package com.zjcxph.imgapi.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.io.image.ImageDataFactory;
import com.zjcxph.imgapi.common.ResultCode;
import com.zjcxph.imgapi.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class PdfService {
    private static final Logger logger = LoggerFactory.getLogger(PdfService.class);

    /**
     * 将图片列表合并为 PDF 文件。
     * <p>
     * 改进点（相对原实现）：
     * 1. 不再吞异常返回 false，改为抛出 BusinessException，调用方可区分失败原因；
     * 2. 异常路径下确保 PdfDocument 被关闭，避免 iText 资源泄漏；
     * 3. 区分 IO 异常与其他异常，日志更精准。
     * </p>
     *
     * @param outputPath    输出 PDF 路径
     * @param imagePathList 图片路径列表
     * @throws BusinessException 当 PDF 创建失败时抛出
     */
    public void createPdfFromImages(String outputPath, List<String> imagePathList) {
        PdfDocument pdfDoc = null;
        try {
            pdfDoc = new PdfDocument(new PdfWriter(outputPath));
            Document doc = new Document(pdfDoc);

            for (String imagePath : imagePathList) {
                Image img = new Image(ImageDataFactory.create(imagePath));
                doc.add(img);
            }

            doc.close();
            logger.info("PDF 创建成功: {}, 包含 {} 张图片", outputPath, imagePathList.size());

        } catch (IOException e) {
            logger.error("PDF 文件写入失败: {}", outputPath, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "PDF 文件写入失败: " + e.getMessage());
        } catch (Exception e) {
            logger.error("PDF 创建失败: {}", outputPath, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR.getCode(), "PDF 创建失败: " + e.getMessage());
        } finally {
            // 异常路径下确保 PdfDocument 被关闭，避免资源泄漏
            if (pdfDoc != null && !pdfDoc.isClosed()) {
                try {
                    pdfDoc.close();
                } catch (Exception e) {
                    logger.warn("关闭 PdfDocument 失败", e);
                }
            }
        }
    }
}