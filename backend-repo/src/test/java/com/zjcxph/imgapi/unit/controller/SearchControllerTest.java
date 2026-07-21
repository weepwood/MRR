package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.SearchController;
import com.zjcxph.imgapi.dto.req.IdCardQueryRequest;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.service.SearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchController 搜索控制器测试")
class SearchControllerTest {

    @Mock
    private SearchService searchService;
    @Mock
    private HttpServletRequest mockRequest;

    @InjectMocks
    private SearchController controller;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "secretKey", "test-secret-key-32bytes!!");
    }

    @Nested
    @DisplayName("hello")
    class Hello {

        @Test
        @DisplayName("返回含 IP 的字符串")
        void returnsStringWithIp() {
            when(mockRequest.getHeader("X-Forwarded-For")).thenReturn(null);
            when(mockRequest.getHeader("X-Real-IP")).thenReturn(null);
            when(mockRequest.getRemoteAddr()).thenReturn("10.0.0.1");

            String result = controller.hello(mockRequest);
            assertThat(result).contains("10.0.0.1");
        }
    }

    @Nested
    @DisplayName("getBAHByIdCard (POST)")
    class GetBAHByIdCard {

        @Test
        @DisplayName("合法身份证号查询成功")
        void validIdCard() {
            Patient patient = new Patient();
            patient.setBah("00789508");
            patient.setName("张三");
            when(searchService.getBAHByID("123456199001011234")).thenReturn(List.of(patient));

            IdCardQueryRequest req = new IdCardQueryRequest();
            req.setIdCard("123456199001011234");
            Result<List<Patient>> r = controller.getBAHByIdCard(req);

            assertThat(r.getCode()).isEqualTo(200);
            assertThat(r.getData()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getBAHByEncryptID")
    class GetBAHByEncryptID {

        @Test
        @DisplayName("解密失败返回 fail")
        void decryptFails() {
            Result<List<Patient>> r = controller.getBAHByEncryptID("bad-encrypted", "user", "iv", "ts");
            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).contains("解密失败");
        }
    }
}
