package com.zjcxph.imgapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssPropertiesTest {

    @Test
    void pathStyleAccessIsDisabledByDefault() {
        OssProperties properties = new OssProperties();

        assertFalse(properties.isPathStyleAccess());
    }

    @Test
    void bindsPathStyleAccessFromConfiguration() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "oss.path-style-access", "true"
        )));

        OssProperties properties = binder.bind("oss", Bindable.of(OssProperties.class))
                .orElseThrow(() -> new IllegalStateException("OSS 配置绑定失败"));

        assertTrue(properties.isPathStyleAccess());
    }
}
