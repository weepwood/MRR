package com.zjcxph.imgapi.utils;

public record HttpByteRange(long start, long end) {

    public HttpByteRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("无效的字节区间");
        }
    }

    public long length() {
        return end - start + 1;
    }

    public static HttpByteRange parse(String header, long totalLength) {
        if (totalLength <= 0) {
            throw new IllegalArgumentException("文件长度必须大于 0");
        }
        if (header == null || !header.startsWith("bytes=") || header.contains(",")) {
            throw new IllegalArgumentException("仅支持单个 bytes Range");
        }
        String value = header.substring("bytes=".length()).trim();
        int separator = value.indexOf('-');
        if (separator < 0) {
            throw new IllegalArgumentException("Range 格式不正确");
        }
        String startText = value.substring(0, separator).trim();
        String endText = value.substring(separator + 1).trim();
        try {
            if (startText.isEmpty()) {
                long suffixLength = Long.parseLong(endText);
                if (suffixLength <= 0) {
                    throw new IllegalArgumentException("Range 后缀长度必须大于 0");
                }
                long start = Math.max(0, totalLength - suffixLength);
                return new HttpByteRange(start, totalLength - 1);
            }
            long start = Long.parseLong(startText);
            if (start >= totalLength) {
                throw new IllegalArgumentException("Range 起点超过文件长度");
            }
            long end = endText.isEmpty() ? totalLength - 1 : Long.parseLong(endText);
            end = Math.min(end, totalLength - 1);
            if (end < start) {
                throw new IllegalArgumentException("Range 终点小于起点");
            }
            return new HttpByteRange(start, end);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Range 数值格式不正确", exception);
        }
    }
}
