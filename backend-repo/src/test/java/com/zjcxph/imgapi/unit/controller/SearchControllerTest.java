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

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
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
        @DisplayName("时间戳超出窗口时拒绝，且不回显内部异常信息")
        void rejectsStaleTimestamp() {
            String stale = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());

            Result<List<Patient>> r = controller.getBAHByEncryptID("bad-encrypted", "user", "iv", stale);

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isEqualTo("加密身份证查询参数无效");
        }

        @Test
        @DisplayName("非数字时间戳被拒绝，不再进入解密")
        void rejectsNonNumericTimestamp() {
            Result<List<Patient>> r = controller.getBAHByEncryptID("bad-encrypted", "user", "iv", "ts");

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isEqualTo("加密身份证查询参数无效");
        }

        @Test
        @DisplayName("时间戳在窗口内时进入解密，失败信息为通用文案")
        void decryptFailsWithGenericMessage() {
            String fresh = String.valueOf(Instant.now().getEpochSecond());

            Result<List<Patient>> r = controller.getBAHByEncryptID("bad-encrypted", "user", "iv", fresh);

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isEqualTo("加密身份证查询失败");
        }

        @Test
        @DisplayName("容差配置为 0 时关闭窗口校验")
        void toleratesDisabledWindow() {
            ReflectionTestUtils.setField(controller, "timestampToleranceSeconds", 0);

            Result<List<Patient>> r = controller.getBAHByEncryptID("bad-encrypted", "user", "iv", "0");

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isEqualTo("加密身份证查询失败");
        }
    }

    @Nested
    @DisplayName("getBAHByEncryptIDLegacy")
    class GetBAHByEncryptIDLegacy {

        @Test
        @DisplayName("默认停用：不进入解密、不查库，返回 410 并指路新接口")
        void disabledByDefault() {
            Result<List<Patient>> r = controller.getBAHByEncryptIDLegacy("any-cipher", "user", "iv");

            assertThat(r.getCode()).isEqualTo(410);
            assertThat(r.getMessage()).contains("已停用").contains("getBAHByEncryptID");
            verifyNoInteractions(searchService);
        }

        @Test
        @DisplayName("显式关闭开关时同样拒绝")
        void rejectsWhenExplicitlyDisabled() {
            ReflectionTestUtils.setField(controller, "legacyEncryptIdEnabled", false);

            Result<List<Patient>> r = controller.getBAHByEncryptIDLegacy("any-cipher", "user", "iv");

            assertThat(r.getCode()).isEqualTo(410);
            verifyNoInteractions(searchService);
        }

        @Test
        @DisplayName("开启后仍可服务旧调用方，解密失败走通用文案")
        void worksWhenExplicitlyEnabled() {
            ReflectionTestUtils.setField(controller, "legacyEncryptIdEnabled", true);

            Result<List<Patient>> r = controller.getBAHByEncryptIDLegacy("bad-encrypted", "user", "iv");

            assertThat(r.getCode()).isEqualTo(400);
            assertThat(r.getMessage()).isEqualTo("加密身份证查询失败");
        }
    }
}
