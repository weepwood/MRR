package com.zjcxph.imgapi.service;

import java.util.Map;

/**
 * 系统信息服务接口。
 * <p>
 * 将原 SystemInfoController 中通过 Controller 互调（this.xxx().getData()）组装 overview
 * 的逻辑下沉到 Service 层，避免 Controller 内部互调导致的：
 * 1. 每个被调方法重建 Result 包装对象，产生大量临时对象；
 * 2. 组装逻辑难以单元测试；
 * 3. 分层职责混乱（Controller 承担了数据组装）。
 * </p>
 */
public interface SystemInfoService {

    /**
     * 获取统一监控数据（info / memory / runtime / health / properties / gc / threads）。
     * 各子项由 Service 内部方法直接组装 Map，不再经过 Controller。
     */
    Map<String, Object> getOverview();

    Map<String, Object> getSystemInfo();

    Map<String, Object> getMemoryInfo();

    Map<String, Object> getRuntimeInfo();

    Map<String, Object> getHealth();

    Map<String, String> getSystemProperties();

    Map<String, Object> getGcStats();

    Map<String, Object> getThreadStats();
}
