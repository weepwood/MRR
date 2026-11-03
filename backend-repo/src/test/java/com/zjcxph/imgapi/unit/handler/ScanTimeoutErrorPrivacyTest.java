package com.zjcxph.imgapi.unit.handler;

import com.zjcxph.imgapi.handler.GlobalExceptionHandler;
import com.zjcxph.imgapi.utils.ErrorReference;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ScanTimeoutErrorPrivacyTest {
    @Test
    void timedOutQueriesUseTheExistingSafeErrorResponse() {
        var response = new GlobalExceptionHandler().handleException(
                new QueryTimeoutException("SYNTHETIC_PRIVATE_SQL_AND_FILTER"), new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        String reference = response.getHeaders().getFirst(ErrorReference.RESPONSE_HEADER);
        assertThat(reference).matches("ERR-\\d{8}-[A-F0-9]{8}");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains(reference).doesNotContain("SYNTHETIC_PRIVATE");
    }
}
