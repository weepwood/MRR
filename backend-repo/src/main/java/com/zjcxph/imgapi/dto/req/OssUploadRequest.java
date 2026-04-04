package com.zjcxph.imgapi.dto.req;

import java.util.List;

public class OssUploadRequest {
    private List<Integer> scanIds;

    public List<Integer> getScanIds() {
        return scanIds;
    }

    public void setScanIds(List<Integer> scanIds) {
        this.scanIds = scanIds;
    }
}
