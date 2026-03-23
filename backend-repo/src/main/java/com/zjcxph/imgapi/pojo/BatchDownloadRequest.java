package com.zjcxph.imgapi.pojo;

import java.util.List;

public class BatchDownloadRequest {
    private List<String> ids;

    public List<String> getIds() {
        return ids;
    }

    public void setIds(List<String> ids) {
        this.ids = ids;
    }
}
