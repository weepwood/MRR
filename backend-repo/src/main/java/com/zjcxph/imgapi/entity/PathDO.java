package com.zjcxph.imgapi.entity;

import lombok.Data;

@Data
public class PathDO {
    private String folder;
    private String filename;
    private String BRXH;
    private String BAH;

    public PathDO(String folder, String filename, String BRXH, String BAH) {
        this.folder = folder;
        this.filename = filename;
        this.BRXH = BRXH;
        this.BAH = BAH;
    }


}
