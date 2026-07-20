package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.service.DeveloperModeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeveloperModeStatusControllerTest {

    @Test
    void shouldExposeOnlyDisabledFlag() {
        DeveloperModeService service = mock(DeveloperModeService.class);
        when(service.isEnabled()).thenReturn(false);
        DeveloperModeStatusController controller = new DeveloperModeStatusController(service);

        var result = controller.status();

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsOnlyKeys("enabled");
        assertThat(result.getData().get("enabled")).isFalse();
    }
}
