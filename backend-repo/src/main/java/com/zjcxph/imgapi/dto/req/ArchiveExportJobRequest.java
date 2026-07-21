package com.zjcxph.imgapi.dto.req;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ArchiveExportJobRequest {
    private String format;
    private String bah;
    private String sjh;
    private List<String> ids = new ArrayList<>();
    private String idempotencyKey;
}
