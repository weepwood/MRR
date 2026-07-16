package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "classification")
public class ClassificationProperties {

    /** 总开关。默认关闭，配置好 OCR 命令后再显式开启。 */
    private boolean enabled = false;

    /** 同时运行的病案识别任务数量。 */
    private int workerThreads = 1;

    /** 单次从数据库获取的图片数量。 */
    private int batchSize = 100;

    /** 单张图片 OCR 超时时间。 */
    private int timeoutSeconds = 60;

    /** 批量确认高置信度建议时使用的默认阈值。 */
    private double highConfidence = 0.92D;

    private String modelVersion = "ocr-keyword-v1";
    private String ruleVersion = "2026.07.1";
    private String tempDirectory = "";
    private Ocr ocr = new Ocr();

    public boolean isOcrConfigured() {
        return ocr != null && ocr.getExecutable() != null && !ocr.getExecutable().isBlank();
    }

    @Data
    public static class Ocr {
        /**
         * 本地 OCR 可执行文件，例如 python、paddleocr 或医院封装的 OCR 程序。
         * OCR 程序应将 UTF-8 识别文本写到标准输出。
         */
        private String executable = "";

        /** 命令参数，{image} 会替换为临时图片绝对路径。 */
        private List<String> arguments = new ArrayList<>(List.of("{image}"));
    }
}
