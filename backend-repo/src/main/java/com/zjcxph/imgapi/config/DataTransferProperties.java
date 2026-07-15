package com.zjcxph.imgapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据交换中心配置。
 *
 * <p>所有路径均由服务端配置，接口不接受任意绝对路径，避免目录穿越和敏感文件读取。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.data-transfer")
public class DataTransferProperties {

    /** 上传文件、错误报告和导出文件根目录。 */
    private String baseDir = "./data/data-transfer";

    /** 运维人员预先放置超大 CSV 的受控目录。 */
    private String inboxDir = "./data/import-inbox";

    /** 每个任务最多保存到数据库的错误样本数。 */
    private int maxErrorSamples = 500;

    /** 导出 mr_scan 时每个压缩 CSV 的最大行数。 */
    private int exportRowsPerPart = 1_000_000;

    /** 单次任务允许登记的最大文件数。 */
    private int maxFilesPerJob = 100;

    /** 允许上传到 Web 接口的单文件字节数；更大的文件应使用 inbox。 */
    private long webUploadMaxBytes = 2L * 1024 * 1024 * 1024;
}
