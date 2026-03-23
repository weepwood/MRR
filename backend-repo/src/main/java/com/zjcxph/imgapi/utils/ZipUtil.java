package com.zjcxph.imgapi.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtil {

    public static void zipJpgFiles(String srcDir, String destZipFile) throws IOException {
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(Paths.get(destZipFile)))) {
            File fileSrcDir = new File(srcDir);
            zipJpgFile(fileSrcDir, "", zipOut);
        }
    }

    private static void zipJpgFile(File fileToZip, String parentDirectoryName, ZipOutputStream zipOut) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            for (File childFile : Objects.requireNonNull(fileToZip.listFiles())) {
                zipJpgFile(childFile, parentDirectoryName + fileToZip.getName() + "/", zipOut);
            }
            return;
        } else {
            if (!fileToZip.getName().toLowerCase().endsWith(".jpg")) {
                return; // 只处理.jpg文件
            }
        }

        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            String zipFilePath = parentDirectoryName + fileToZip.getName();
            ZipEntry zipEntry = new ZipEntry(zipFilePath);
            zipOut.putNextEntry(zipEntry);

            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zipOut.write(bytes, 0, length);
            }
        }
    }
}
