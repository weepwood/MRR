package com.zjcxph.imgapi.entity;

import lombok.Data;

@Data
public class PathDO {
    private Integer scanId;
    private String folder;
    private String filename;
    private String brxh;
    private String bah;
    private String sjh;
    private String sourceType;
    private String sourceNode;
    private String sourceRef;
    private String ossUrl;
    private Long fileSize;

    public PathDO() {
    }

    public PathDO(String folder, String filename, String brxh, String bah) {
        this.folder = folder;
        this.filename = filename;
        this.brxh = brxh;
        this.bah = bah;
    }

    public PathDO(Integer scanId,
                  String folder,
                  String filename,
                  String brxh,
                  String bah,
                  String sjh,
                  String sourceType,
                  String sourceNode,
                  String sourceRef,
                  String ossUrl,
                  Long fileSize) {
        this.scanId = scanId;
        this.folder = folder;
        this.filename = filename;
        this.brxh = brxh;
        this.bah = bah;
        this.sjh = sjh;
        this.sourceType = sourceType;
        this.sourceNode = sourceNode;
        this.sourceRef = sourceRef;
        this.ossUrl = ossUrl;
        this.fileSize = fileSize;
    }
}
