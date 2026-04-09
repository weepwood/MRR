package com.zjcxph.imgapi.dto.req;

import lombok.Data;

import java.util.List;

@Data
public class IdRequest {

    // Getter 和 Setter 方法
    private List<String> id;

    // 必须提供无参构造函数
    public IdRequest() {}

}