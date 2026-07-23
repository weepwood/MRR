package com.zjcxph.imgapi.unit.handler;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.handler.GlobalExceptionHandler;
import com.zjcxph.imgapi.utils.ErrorReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerErrorReferenceTest {

    @Test
    void unexpectedExceptionShouldReturnTraceableErrorIdWithoutInternalDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Result<Void>> response = handler.handleException(
                new IllegalStateException("jdbc:postgresql://localhost:5432/secret"),
                request
        );

        String errorId = response.getHeaders().getFirst(ErrorReference.RESPONSE_HEADER);
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(errorId);
        assertTrue(errorId.matches("ERR-\\d{8}-[A-F0-9]{8}"));
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage().contains(errorId));
        assertTrue(!response.getBody().getMessage().contains("jdbc:postgresql"));
    }
}
