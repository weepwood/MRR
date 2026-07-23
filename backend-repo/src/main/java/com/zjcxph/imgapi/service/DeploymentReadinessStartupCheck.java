package com.zjcxph.imgapi.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 应用完成数据库迁移和 Spring 初始化后立即执行一次部署就绪检查，
 * 使首个业务写请求到来前已经确定读写模式。
 */
@Component
public class DeploymentReadinessStartupCheck {

    private static final Logger logger = LoggerFactory.getLogger(DeploymentReadinessStartupCheck.class);

    private final DeploymentReadinessService readinessService;

    public DeploymentReadinessStartupCheck(DeploymentReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void evaluateAfterStartup() {
        Map<String, Object> snapshot = readinessService.refreshSnapshot();
        logger.warn(
                "部署就绪检查完成: ready={}, mode={}, checkedAt={}",
                snapshot.get("ready"),
                snapshot.get("mode"),
                snapshot.get("checkedAt")
        );
    }
}
