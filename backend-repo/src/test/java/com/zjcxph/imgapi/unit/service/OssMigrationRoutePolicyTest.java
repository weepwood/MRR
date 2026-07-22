package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.impl.OssMigrationRoutePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OSS 上架号路由规则测试")
class OssMigrationRoutePolicyTest {

    @Test
    @DisplayName("保留上架号前导零并按前四位分组")
    void buildsObjectKeyFromSjhPrefix() {
        Scan scan = scan("00123456", "00789124", "0013.jpg");

        assertThat(OssMigrationRoutePolicy.buildObjectKey(scan))
                .isEqualTo("medical-records/0012/00123456-00789124/0013.jpg");
    }

    @Test
    @DisplayName("上架号为空、过短或非数字时等待补齐")
    void rejectsMissingOrInvalidSjh() {
        assertThat(OssMigrationRoutePolicy.hasValidSjh(scan(null, "00789124", "0013.jpg"))).isFalse();
        assertThat(OssMigrationRoutePolicy.hasValidSjh(scan("123", "00789124", "0013.jpg"))).isFalse();
        assertThat(OssMigrationRoutePolicy.hasValidSjh(scan("12A4", "00789124", "0013.jpg"))).isFalse();
    }

    private Scan scan(String sjh, String bah, String filename) {
        Scan scan = new Scan();
        scan.setSjh(sjh);
        scan.setBah(bah);
        scan.setFilename(filename);
        return scan;
    }
}
