package com.zjcxph.imgapi.entity;

import lombok.Data;

@Data
public class PathDO {
    private String folder;
    private String filename;
    private String brxh;
    private String bah;

    public PathDO() {
    }

    public PathDO(String folder, String filename, String brxh, String bah) {
        this.folder = folder;
        this.filename = filename;
        this.brxh = brxh;
        this.bah = bah;
    }
}
