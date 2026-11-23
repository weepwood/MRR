package com.zjcxph.imgapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 档案袋影像下发方式（ADR-0009）。
 *
 * <ul>
 *     <li>{@link Mode#DIRECT}（默认）：沿用历史行为，列表接口返回影像源 Nginx 地址或 OSS 预签名地址，
 *     浏览器直接取图；</li>
 *     <li>{@link Mode#GATEWAY}：列表接口只返回 {@code /api/v1/img/content/{id}}，浏览器凭 HttpOnly
 *     影像访问 Cookie 经后端内容网关取图，逐张校验权限并写入访问审计。</li>
 * </ul>
 *
 * <p>通过 {@code ARCHIVE_IMAGE_DELIVERY_MODE=gateway} 灰度开启，改回 {@code direct} 并重启即可回退。</p>
 */
@ConfigurationProperties(prefix = "archive.image-delivery")
public class ImageDeliveryProperties {

    public enum Mode { DIRECT, GATEWAY }

    private Mode mode = Mode.DIRECT;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.DIRECT : mode;
    }

    public boolean isGateway() {
        return mode == Mode.GATEWAY;
    }
}
