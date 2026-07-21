package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.controller.ImageController;
import com.zjcxph.imgapi.controller.ScanController;
import com.zjcxph.imgapi.dto.req.BatchDownloadRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyExportPermissionTest {

    @Test
    void legacyArchiveDownloadRequiresDownloadPermission() throws Exception {
        Method method = ImageController.class.getMethod("download", String.class, String.class);

        RequirePermissions permissions = method.getAnnotation(RequirePermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("record:download");
    }

    @Test
    void legacyBatchDownloadRequiresDownloadPermission() throws Exception {
        Method method = ScanController.class.getMethod("batchDownload", BatchDownloadRequest.class);

        RequirePermissions permissions = method.getAnnotation(RequirePermissions.class);

        assertThat(permissions).isNotNull();
        assertThat(permissions.value()).containsExactly("record:download");
    }
}
