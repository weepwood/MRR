package com.zjcxph.imgapi.service;

import java.util.List;
import java.util.Map;

/**
 * 以病案主档为中心的数据关系只读查询服务。
 */
public interface DataRelationService {

    /**
     * 获取核心业务表关系覆盖率和最近一次数据质量结果。
     */
    Map<String, Object> getOverview();

    /**
     * 按 archive_id、BAH 或 SJH 搜索病案主档。
     */
    List<Map<String, Object>> searchArchives(String type, String value, int limit);

    /**
     * 获取一份病案在统计、患者、扫描、装箱和迁移数据中的关系详情。
     */
    Map<String, Object> getArchiveRelation(long archiveId);
}
