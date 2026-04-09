package com.zjcxph.imgapi.entity;

import lombok.Data;

@Data
public class AuthRole {
    private String code;
    private String name;
    private String description;
    private String permissions;
    private Integer sortOrder;

}
