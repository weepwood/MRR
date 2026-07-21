package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;

import java.io.IOException;
import java.io.InputStream;

/**
 * 影像二进制存储访问端口。
 *
 * <p>业务与控制器只通过该接口读取影像，避免感知 Windows 路径、NAS 目录或 OSS Key。
 * 当前默认实现为本地文件系统，后续可增加 NAS/S3 实现而不修改导出业务。</p>
 */
public interface ImageStorage {

    InputStream open(PathDO image) throws IOException;

    long size(PathDO image) throws IOException;
}
