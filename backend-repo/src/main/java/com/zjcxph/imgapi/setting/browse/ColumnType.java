package com.zjcxph.imgapi.setting.browse;

/**
 * 列的逻辑类型。用于参数解析、默认操作符集合与前端渲染，不直接暴露 PostgreSQL 类型名。
 */
public enum ColumnType {

    TEXT,
    NUMBER,
    BOOLEAN,
    DATETIME,
    DATE,
    JSON,
    BINARY;

    public boolean isTemporal() {
        return this == DATETIME || this == DATE;
    }
}
