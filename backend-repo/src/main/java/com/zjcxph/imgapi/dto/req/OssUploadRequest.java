package com.zjcxph.imgapi.dto.req;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class OssUploadRequest {
    private List<Integer> scanIds;

}
