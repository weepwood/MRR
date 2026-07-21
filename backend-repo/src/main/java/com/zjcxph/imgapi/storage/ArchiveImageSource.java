package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;

import java.io.IOException;
import java.io.InputStream;

/**
 * 受控图片来源读取端口。实现只能根据服务端配置和数据库元数据定位图片。
 */
public interface ArchiveImageSource {

    boolean supports(PathDO image);

    InputStream open(PathDO image) throws IOException;

    long size(PathDO image) throws IOException;

    String describeSource(PathDO image);
}
