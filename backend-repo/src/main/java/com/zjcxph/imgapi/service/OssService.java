package com.zjcxph.imgapi.service;

public interface OssService {

    /**
     * Upload a local file to OSS.
     * @param localFilePath absolute path to the local file
     * @param ossKey the OSS object key (path in bucket)
     * @return the OSS object key on success
     */
    String uploadFile(String localFilePath, String ossKey);

    /**
     * Generate a pre-signed URL for private read access.
     * @param ossKey the OSS object key
     * @return a time-limited signed URL
     */
    String generatePresignedUrl(String ossKey);

    /**
     * Calculate the MD5 checksum of a local file.
     * @param filePath path to the file
     * @return hex-encoded MD5 string
     */
    String calculateMd5(String filePath);

    /**
     * Check if an object exists in OSS.
     * @param ossKey the OSS object key
     * @return true if the object exists
     */
    boolean doesObjectExist(String ossKey);

    /**
     * Delete an object from OSS.
     * @param ossKey the OSS object key
     */
    void deleteObject(String ossKey);

    /**
     * Get the size of a local file in bytes.
     * @param filePath path to the file
     * @return file size in bytes
     */
    long getFileSize(String filePath);

    /**
     * Verify uploaded file integrity by downloading and comparing MD5.
     * This is a strict verification method that ensures data integrity.
     * @param ossKey the OSS object key
     * @param expectedMd5 the expected MD5 checksum
     * @return true if the file integrity is verified
     */
    boolean verifyUploadIntegrity(String ossKey, String expectedMd5);
}
