package com.zjcxph.imgapi.scheduler;

import com.zjcxph.imgapi.controller.ImageController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;

@Component
public class TempZipCleaner {

    private static final String TEMP_DIR = "./temp";
    // 设置文件的老化期限为一天
    private static final Duration FILE_AGE_LIMIT = Duration.ofDays(1);

    private static final Logger logger = LoggerFactory.getLogger(TempZipCleaner.class);

    /**
     * 定时删除 temp 文件夹下超过一天的 zip 文件。
     *
     */
    @Scheduled(cron = "0 0 12 * * ?") // 每天中午 12 点执行一次
    public void cleanUpTempZips() {
        File dir = new File(TEMP_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return; // 如果目录不存在或不是一个目录，则直接返回
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".temp"));
        if (files != null) {
            for (File file : files) {
                try {
                    Path filePath = file.toPath();
                    BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);
                    Instant fileLastModifiedTime = attrs.lastModifiedTime().toInstant();
                    Instant now = Instant.now();

                    // 只删除超过一天的文件
                    if (Duration.between(fileLastModifiedTime, now).compareTo(FILE_AGE_LIMIT) > 0) {
                        Files.deleteIfExists(filePath);
                        logger.warn("删除临时文件: " + file.getName());
                    }
                } catch (Exception e) {
                    // 处理可能发生的异常，比如权限问题等
                    e.printStackTrace();
                }
            }
        }
    }
}
