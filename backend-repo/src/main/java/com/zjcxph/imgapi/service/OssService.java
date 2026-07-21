package com.zjcxph.imgapi.service;

public interface OssService {

    String uploadFile(String localFilePath, String ossKey);

    String generatePresignedUrl(String ossKey);

    String calculateMd5(String filePath);

    boolean doesObjectExist(String ossKey);

    void deleteObject(String ossKey);

    long getFileSize(String filePath);

    boolean verifyUploadIntegrity(String ossKey, String expectedMd5);
}
