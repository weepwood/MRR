package com.zjcxph.imgapi.utils;

/**
 * 分页工具类
 * 提供统一的分页参数计算功能
 */
public final class PaginationUtils {

    private PaginationUtils() {
        // 防止实例化
    }

    /**
     * 根据页码和每页大小计算偏移量
     *
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 偏移量
     */
    public static int calculateOffset(int page, int size) {
        return (page - 1) * size;
    }

    /**
     * 计算总页数
     *
     * @param total 总记录数
     * @param size  每页大小
     * @return 总页数
     */
    public static int calculateTotalPages(long total, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / size);
    }

    /**
     * 验证分页参数的合法性
     *
     * @param page 页码
     * @param size 每页大小
     * @throws IllegalArgumentException 如果参数不合法
     */
    public static void validatePageParams(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于等于1");
        }
        if (size < 1 || size > 1000) {
            throw new IllegalArgumentException("每页大小必须在1-1000之间");
        }
    }
}
