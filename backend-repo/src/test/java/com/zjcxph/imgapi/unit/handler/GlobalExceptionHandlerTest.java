package com.zjcxph.imgapi.unit.handler;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void genericExceptionDoesNotExposeInternalDetails() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        RuntimeException exception = new RuntimeException(
                "jdbc:postgresql://127.0.0.1:5432/imgapi password=secret C:\\MRR\\secrets");

        var entity = handler.handleException(exception, response);
        Result<Void> result = entity.getBody();

        assertThat(entity.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getHeader("X-Error-Code")).isEqualTo("MRR-SYSTEM-9000");
        assertThat(result).isNotNull();
        assertThat(result.getErrorCode()).isEqualTo("MRR-SYSTEM-9000");
        assertThat(result.getMessage()).isEqualTo("服务器内部错误，请联系管理员");
        assertThat(result.getData()).isNull();
        assertThat(result.toString()).doesNotContain("postgresql", "secret", "C:\\MRR");
    }

    @Test
    void illegalArgumentFiltersInfrastructureDetails() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        var entity = handler.handleIllegalArgumentException(
                new IllegalArgumentException("SQL error at C:\\MRR\\data"), response);

        assertThat(entity.getStatusCode().value()).isEqualTo(400);
        assertThat(entity.getBody()).isNotNull();
        assertThat(entity.getBody().getMessage()).isEqualTo("请求参数错误");
    }
}
