package com.zjcxph.imgapi.pojo;

import java.util.List;

public class IdRequest {

    private List<String> id;

    // 必须提供无参构造函数
    public IdRequest() {}

    // Getter 和 Setter 方法
    public List<String> getId() {
        return id;
    }

    public void setId(List<String> id) {
        this.id = id;
    }
}