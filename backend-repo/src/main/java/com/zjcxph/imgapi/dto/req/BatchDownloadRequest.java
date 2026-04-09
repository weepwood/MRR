package com.zjcxph.imgapi.dto.req;

import lombok.Data;

import java.util.List;

@Data
public class BatchDownloadRequest {
    private List<String> ids;

}
