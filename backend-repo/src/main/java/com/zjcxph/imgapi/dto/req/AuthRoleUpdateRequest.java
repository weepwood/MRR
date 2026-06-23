package com.zjcxph.imgapi.dto.req;

import lombok.Data;

@Data
public class AuthRoleUpdateRequest {
    private String name;
    private String description;
    private String permissions;
    private Integer sortOrder;
}
