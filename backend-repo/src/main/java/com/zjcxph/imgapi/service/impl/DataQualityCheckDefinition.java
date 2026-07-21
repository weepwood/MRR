package com.zjcxph.imgapi.service.impl;

record DataQualityCheckDefinition(
        String code,
        String name,
        String severity,
        String countSql,
        String sampleSql
) {
}
