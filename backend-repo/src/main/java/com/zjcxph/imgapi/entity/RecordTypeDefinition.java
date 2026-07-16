package com.zjcxph.imgapi.entity;

import lombok.Data;

@Data
public class RecordTypeDefinition {
    private Integer btype;
    private String typeCode;
    private String typeName;
    private String keywords;
    private String negativeKeywords;
    private Boolean enabled;
    private Integer sortOrder;
}
