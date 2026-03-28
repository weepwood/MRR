package com.zjcxph.imgapi;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Controller 接口集成测试类
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public class ControllerIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(ControllerIntegrationTest.class);

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * 测试系统信息接口
     */
    @Test
    public void testSystemInfo() {
        logger.info("========== 测试系统信息接口 ==========");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/system/info", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        Map<String, Object> body = response.getBody();
        logger.info("系统信息：{}", body);
        
        assertTrue(body.containsKey("application"));
        assertTrue(body.containsKey("jvm"));
        assertTrue(body.containsKey("operatingSystem"));
    }

    /**
     * 测试健康检查接口
     */
    @Test
    public void testHealthCheck() {
        logger.info("========== 测试健康检查接口 ==========");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/system/health", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
        logger.info("健康状态：{}", body.get("status"));
    }

    /**
     * 测试统计 API - 获取所有数据
     */
    @Test
    public void testStatisticsAPI() {
        logger.info("========== 测试统计 API ==========");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/statistics-api", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        
        assertNotNull(body);
        assertEquals(200, ((Map<?, ?>) body.get("data")).get("code"));
        logger.info("统计数据接口响应正常");
    }

    /**
     * 测试统计 API - 病案号汇总
     */
    @Test
    public void testBAHSummaryAPI() {
        logger.info("========== 测试病案号汇总接口 ==========");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/statistics-api/bah-summary", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        logger.info("病案号汇总接口响应正常");
    }

    /**
     * 测试统计 API - 日期汇总
     */
    @Test
    public void testDateSummaryAPI() {
        logger.info("========== 测试日期汇总接口 ==========");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/statistics-api/date-summary", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        logger.info("日期汇总接口响应正常");
    }

    /**
     * 测试统计 API - 总体统计
     */
    @Test
    public void testTotalSummaryAPI() {
        logger.info("========== 测试总体统计接口 ==========");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/statistics-api/summary", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        logger.info("总体统计接口响应正常");
    }

    /**
     * 测试心跳接口
     */
    @Test
    public void testHelloAPI() {
        logger.info("========== 测试心跳接口 ==========");
        
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/img-api/hello", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        
        assertNotNull(body);
        logger.info("心跳响应：{}", body.get("message"));
        assertEquals("服务正常", ((Map<?, ?>) body.get("data")).get("message"));
    }

    /**
     * 测试带分页的统计查询
     */
    @Test
    public void testStatisticsWithPagination() {
        logger.info("========== 测试带分页的统计查询 ==========");
        
        // 测试默认分页（第 1 页，每页 100 条）
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/statistics-api?page=1&size=10", 
            Map.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        
        assertNotNull(body);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        
        assertTrue(data.containsKey("list"));
        assertTrue(data.containsKey("total"));
        assertTrue(data.containsKey("page"));
        assertTrue(data.containsKey("size"));
        
        logger.info("分页查询：page={}, size={}, total={}", 
            data.get("page"), data.get("size"), data.get("total"));
    }

    /**
     * 测试错误处理 - 无效的病案号格式
     */
    @Test
    public void testInvalidBAHFormat() {
        logger.info("========== 测试无效病案号格式 ==========");
        
        // 测试不符合正则表达式的病案号
        ResponseEntity<Map> response = restTemplate.getForEntity(
            "/v1/img-api/invalid-bah", 
            Map.class
        );
        
        // 应该返回 400 或 404
        assertTrue(response.getStatusCode().is4xxClientError());
        logger.info("无效病案号返回状态码：{}", response.getStatusCode());
    }

    /**
     * 测试并发请求
     */
    @Test
    public void testConcurrentRequests() throws InterruptedException {
        logger.info("========== 测试并发请求 ==========");
        
        Thread[] threads = new Thread[5];
        
        // 创建 5 个并发线程
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    ResponseEntity<Map> response = restTemplate.getForEntity(
                        "/v1/system/health", 
                        Map.class
                    );
                    logger.info("线程 {} - 状态码：{}", threadId, response.getStatusCode());
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                } catch (Exception e) {
                    logger.error("线程 {} 异常：{}", threadId, e.getMessage());
                }
            });
        }
        
        // 启动所有线程
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        logger.info("并发测试完成");
    }
}
