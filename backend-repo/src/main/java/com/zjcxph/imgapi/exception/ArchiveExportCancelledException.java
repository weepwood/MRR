package com.zjcxph.imgapi.exception;

import java.io.IOException;

public class ArchiveExportCancelledException extends IOException {

    public ArchiveExportCancelledException() {
        super("导出任务已取消");
    }
}
