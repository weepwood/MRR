package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.controller.DataExchangeController;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据交换批量导出必须与影像、PDF 导出使用同一档权限。
 *
 * <p>CSV 导出会一次性输出至多十万行患者姓名、身份证号与病案编号，
 * 其数据敏感度不低于 PDF/影像下载。历史实现只要求 {@code record:read}，
 * 导致"可查看"等价于"可批量导出"，本测试防止该状态回退。</p>
 */
class DataExchangeExportPermissionTest {

    @Test
    void exportPatientsRequiresDownloadPermission() throws Exception {
        assertExportPermission("exportPatients", String.class, HttpServletResponse.class);
    }

    @Test
    void exportStatisticsRequiresDownloadPermission() throws Exception {
        assertExportPermission(
                "exportStatistics",
                String.class, String.class, String.class, String.class,
                String.class, String.class, HttpServletResponse.class
        );
    }

    @Test
    void exportArchivesRequiresDownloadPermission() throws Exception {
        assertExportPermission(
                "exportArchives",
                String.class, String.class, String.class, String.class,
                String.class, String.class, String.class, HttpServletResponse.class
        );
    }

    @Test
    void exportArchiveBoxesRequiresDownloadPermission() throws Exception {
        assertExportPermission(
                "exportArchiveBoxes",
                String.class, String.class, String.class, String.class,
                String.class, HttpServletResponse.class
        );
    }

    @Test
    void exportScanRequiresDownloadPermission() throws Exception {
        assertExportPermission(
                "exportScan",
                String.class, String.class, String.class, String.class,
                String.class, Integer.class, Long.class, HttpServletResponse.class
        );
    }

    @Test
    void importEndpointsKeepEditPermission() throws Exception {
        Method method = DataExchangeController.class.getMethod(
                "importScan",
                org.springframework.web.multipart.MultipartFile.class,
                boolean.class
        );

        RequirePermissions permissions = method.getAnnotation(RequirePermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("record:edit");
    }

    private void assertExportPermission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = DataExchangeController.class.getMethod(methodName, parameterTypes);

        RequirePermissions permissions = method.getAnnotation(RequirePermissions.class);

        assertThat(permissions).as("%s 必须声明导出权限", methodName).isNotNull();
        assertThat(permissions.value()).containsExactly("record:download");
    }
}
