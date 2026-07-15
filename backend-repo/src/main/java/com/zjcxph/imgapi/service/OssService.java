package com.zjcxph.imgapi.service;

public interface OssService {

    /**
     * Upload a local file to OSS. Callers that already calculated the checksum
     * should use the three-argument overload to avoid reading the file twice.
     */
    default String uploadFile(String localFilePath, String ossKey) {
        return uploadFile(localFilePath, ossKey, calculateMd5(localFilePath));
    }

    /**
     * Upload a local file using a precomputed hexadecimal MD5 checksum.
     * The checksum is sent as Content-MD5 and stored as object metadata.
     */
    String uploadFile(String localFilePath, String ossKey, String checksumMd5);

    String generatePresignedUrl(String ossKey);

    String calculateMd5(String filePath);

    boolean doesObjectExist(String ossKey);

    /**
     * Check whether an existing object has the expected size and checksum.
     * A plain existence check is not sufficient for migration correctness.
     */
    boolean isObjectEquivalent(String ossKey, long expectedSize, String expectedMd5);

    void deleteObject(String ossKey);

    long getFileSize(String filePath);

    /**
     * Strict verification by downloading the object. This is intentionally not
     * used for every migrated file because it doubles transfer volume.
     */
    boolean verifyUploadIntegrity(String ossKey, String expectedMd5);
}
