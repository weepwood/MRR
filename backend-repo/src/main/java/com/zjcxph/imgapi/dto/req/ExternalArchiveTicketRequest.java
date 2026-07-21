package com.zjcxph.imgapi.dto.req;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部系统申请影像档案袋访问票据。
 *
 * <p>所有定位条件采用并集语义，最终会解析成精确的病案号/上架号集合并去重。</p>
 */
@Data
public class ExternalArchiveTicketRequest {

    private String externalUserId;
    private String idCard;
    private String bah;
    private String sjh;
    private List<String> bahs = new ArrayList<>();
    private List<String> sjhs = new ArrayList<>();
    private List<ArchiveSelector> archives = new ArrayList<>();
    private boolean allowDownload;

    @Data
    public static class ArchiveSelector {
        private String bah;
        private String sjh;
    }
}
