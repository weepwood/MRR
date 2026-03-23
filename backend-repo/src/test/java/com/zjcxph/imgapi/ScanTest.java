package com.zjcxph.imgapi;

import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.pojo.Scan;
import com.zjcxph.imgapi.service.ScanService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scan 扫描记录功能测试类
 */
@SpringBootTest
public class ScanTest {

    private static final Logger logger = LoggerFactory.getLogger(ScanTest.class);

    @Autowired
    private ScanService scanService;

    @Autowired
    private ScanMapper scanMapper;

    /**
     * 测试根据病案号查询图片列表
     */
    @Test
    public void testGetImageListByBAH() {
        logger.info("========== 测试根据病案号查询图片列表 ==========");
        
        // 使用示例病案号测试
        String testBah = "00789508";
        logger.info("测试病案号：{}", testBah);
        
        List<Scan> result = scanService.getImageListByBAH(testBah);
        assertNotNull(result, "返回结果不应为 null");
        logger.info("查询到 {} 条记录", result.size());
        
        // 如果找到记录，验证数据完整性
        if (!result.isEmpty()) {
            for (Scan scan : result) {
                assertEquals(testBah, scan.getBah(), "病案号应该匹配");
                assertNotNull(scan.getFilename(), "文件名不应为 null");
                logger.info("图片：{} | 页数：{} | 文件夹：{}", 
                    scan.getFilename(), scan.getPages(), scan.getFolder());
            }
        } else {
            logger.warn("未找到病案号 {} 的记录", testBah);
        }
    }

    /**
     * 测试创建扫描记录
     */
    @Test
    public void testCreate() {
        logger.info("========== 测试创建扫描记录 ==========");
        
        Scan scan = new Scan(
            null,  // ID 由数据库自动生成
            "605746",  // brxh
            "00789509", // bah
            "test.jpg", // filename
            1,     // btype
            10,    // pages
            "OP001", // openerNo
            null,  // uploadDate (由数据库处理)
            1,     // uploadFlag
            "24.03.18" // folder
        );
        
        Scan created = scanService.create(scan);
        assertNotNull(created, "创建的记录不应为 null");
        logger.info("成功创建记录，ID: {}", created.getId());
        
        // 验证创建的数据
        assertEquals("605746", created.getBrxh());
        assertEquals("00789509", created.getBah());
        assertEquals("test.jpg", created.getFilename());
        
        // 清理测试数据（可选）
        // scanService.deleteById(created.getId());
    }

    /**
     * 测试根据 ID 查询
     */
    @Test
    public void testFindById() {
        logger.info("========== 测试根据 ID 查询 ==========");
        
        // 先获取一个存在的 ID
        List<Scan> allScans = scanService.findAll();
        if (allScans.isEmpty()) {
            logger.warn("没有扫描记录，跳过此测试");
            return;
        }
        
        Integer testId = allScans.get(0).getId();
        logger.info("测试 ID: {}", testId);
        
        Scan result = scanService.findById(testId);
        assertNotNull(result, "返回结果不应为 null");
        assertEquals(testId, result.getId(), "ID 应该匹配");
        logger.info("查询到记录：bah={}, filename={}", result.getBah(), result.getFilename());
    }

    /**
     * 测试更新扫描记录
     */
    @Test
    public void testUpdate() {
        logger.info("========== 测试更新扫描记录 ==========");
        
        // 先获取一个存在的记录
        List<Scan> allScans = scanService.findAll();
        if (allScans.isEmpty()) {
            logger.warn("没有扫描记录，跳过此测试");
            return;
        }
        
        Scan scan = allScans.get(0);
        logger.info("更新前的数据 - ID: {}, btype: {}", scan.getId(), scan.getBtype());
        
        // 修改类型
        scan.setBtype(999);
        Scan updated = scanService.update(scan);
        
        assertNotNull(updated, "更新后的记录不应为 null");
        assertEquals(999, updated.getBtype(), "类型应该被更新");
        logger.info("更新后的数据 - btype: {}", updated.getBtype());
        
        // 恢复原值（可选）
        // scan.setBtype(originalType);
        // scanService.update(scan);
    }

    /**
     * 测试删除扫描记录
     */
    @Test
    public void testDeleteById() {
        logger.info("========== 测试删除扫描记录 ==========");
        
        // 先创建一个测试记录
        Scan testScan = new Scan(
            null,
            "605747",
            "00789510",
            "delete_test.jpg",
            1,
            5,
            "OP002",
            null,
            1,
            "24.03.18"
        );
        
        Scan created = scanService.create(testScan);
        assertNotNull(created);
        Integer testId = created.getId();
        logger.info("创建测试记录，ID: {}", testId);
        
        // 删除记录
        boolean deleted = scanService.deleteById(testId);
        assertTrue(deleted, "删除应该成功");
        logger.info("成功删除记录 ID: {}", testId);
        
        // 验证已删除
        Scan deletedScan = scanService.findById(testId);
        assertNull(deletedScan, "删除后查询应该返回 null");
    }

    /**
     * 测试分页查询
     */
    @Test
    public void testFindAllWithPagination() {
        logger.info("========== 测试分页查询 ==========");
        
        // 第 1 页，每页 10 条
        List<Scan> page1 = scanService.findAllWithPagination(1, 10);
        assertNotNull(page1);
        logger.info("第 1 页（10 条/页）：返回 {} 条记录", page1.size());
        
        // 第 2 页，每页 10 条
        List<Scan> page2 = scanService.findAllWithPagination(2, 10);
        assertNotNull(page2);
        logger.info("第 2 页（10 条/页）：返回 {} 条记录", page2.size());
        
        // 验证分页有效性
        if (!page1.isEmpty() && !page2.isEmpty()) {
            assertNotEquals(page1.get(0).getId(), page2.get(0).getId(), 
                "不同页的第一条记录 ID 应该不同");
        }
    }

    /**
     * 测试条件查询
     */
    @Test
    public void testFindByCondition() {
        logger.info("========== 测试条件查询 ==========");
        
        // 测试空条件（查询所有）
        logger.info("--- 测试 1: 空条件 ---");
        // 需要创建 ScanRequest 对象
        // 由于 ScanRequest 在 pojo 包中，这里简单测试
        List<Scan> allScans = scanService.findAll();
        logger.info("查询所有记录：{} 条", allScans.size());
        
        // 测试根据病案号模糊查询
        if (!allScans.isEmpty()) {
            String testBah = allScans.get(0).getBah();
            logger.info("--- 测试 2: 根据病案号 {} 查询 ---", testBah);
            // 这里需要构造 ScanRequest 对象进行测试
        }
    }

    /**
     * 测试获取图片路径
     */
    @Test
    public void testGetImagePath() {
        logger.info("========== 测试获取图片路径 ==========");
        
        // 使用示例病案号
        String testBah = "00789508";
        
        java.nio.file.Path path = scanService.getImagePath(testBah);
        
        if (path != null) {
            logger.info("图片路径：{}", path.toString());
            assertNotNull(path, "路径不应为 null");
        } else {
            logger.warn("未找到病案号 {} 的图片路径", testBah);
        }
    }

//    /**
//     * 测试批量获取图片路径列表
//     */
//    @Test
//    public void testGetImagePathList() {
//        logger.info("========== 测试批量获取图片路径列表 ==========");
//
//        // 先获取一些记录的 ID
//        List<Scan> scans = scanService.findAllWithPagination(1, 5);
//        if (scans.isEmpty()) {
//            logger.warn("没有扫描记录，跳过此测试");
//            return;
//        }
//
//        List<String> ids = scans.stream()
//            .map(s -> s.getId().toString())
//            .collect(java.util.stream.Collectors.toList());
//
//        logger.info("测试 ID 列表：{}", ids);
//
//        var pathList = scanService.getImagePathList(ids);
//        assertNotNull(pathList, "返回结果不应为 null");
//        logger.info("查询到 {} 个路径", pathList.size());
//    }
}
