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

    public String getFolder() {
        return folder;
    }
    public void setFolder(String folder) {
        this.folder = folder;
    }
    public String getFilename() {
        return filename;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getBRXH() {
        return BRXH;
    }
    public void setBRXH(String BRXH) {
        this.BRXH = BRXH;
    }
    public String getBAH() {
        return BAH;
    }
    public void setBAH(String BAH) {
        this.BAH = BAH;
    }


}
