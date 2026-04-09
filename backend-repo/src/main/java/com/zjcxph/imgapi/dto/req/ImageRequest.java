package com.zjcxph.imgapi.dto.req;

import lombok.Data;

@Data
public class ImageRequest {
    private Integer btype;

    public ImageRequest() {
    }


    public void setBtype(Integer btype) {
        this.btype = btype;
    }
}
