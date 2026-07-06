package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.PaginationUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PaginationUtils 分页工具测试")
class PaginationUtilsTest {

    @ParameterizedTest
    @CsvSource({
        "1, 10, 0",
        "2, 10, 10",
        "3, 20, 40",
        "5, 50, 200"
    })
    @DisplayName("calculateOffset — 正确计算偏移量")
    void calculateOffset(int page, int size, int expectedOffset) {
        assertThat(PaginationUtils.calculateOffset(page, size)).isEqualTo(expectedOffset);
    }

    @Test
    @DisplayName("validatePageParams — 合法参数不抛异常")
    void validatePageParams_valid() {
        PaginationUtils.validatePageParams(1, 10);
        PaginationUtils.validatePageParams(999, 1000);
    }

    @Test
    @DisplayName("validatePageParams — page小于1抛异常")
    void validatePageParams_pageLessThanOne() {
        assertThatThrownBy(() -> PaginationUtils.validatePageParams(0, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("页码必须大于等于1");
    }

    @Test
    @DisplayName("validatePageParams — size为0抛异常")
    void validatePageParams_sizeZero() {
        assertThatThrownBy(() -> PaginationUtils.validatePageParams(1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("每页大小");
    }

    @Test
    @DisplayName("validatePageParams — size超过1000抛异常")
    void validatePageParams_sizeExceedsMax() {
        assertThatThrownBy(() -> PaginationUtils.validatePageParams(1, 1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("每页大小");
    }
}
