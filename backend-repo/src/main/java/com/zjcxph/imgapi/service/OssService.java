package com.zjcxph.imgapi.service;

import java.io.IOException;
import java.io.InputStream;

public interface OssService {

    String uploadFile(String localFilePath, String ossKey);

    String generatePresignedUrl(String ossKey);

    String calculateMd5(String filePath);

    boolean doesObjectExist(String ossKey);

    void deleteObject(String ossKey);

    long getFileSize(String filePath);

    boolean verifyUploadIntegrity(String ossKey, String expectedMd5);

    /**
     * 使用后端 SDK 和服务端凭据直接打开对象流。
     * 调用方必须关闭返回流。
     */
    InputStream openObject(String ossKey) throws IOException;

    /**
     * 使用 OSS 元数据接口获取对象大小，不生成签名 URL。
     */
    long getObjectSize(String ossKey) throws IOException;
}
