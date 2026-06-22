package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.utils.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthContext 认证上下文测试")
class AuthContextTest {

    @AfterEach
    void tearDown() {
        // ThreadLocal 必须清理，避免线程复用导致的用例间污染
        AuthContext.clear();
    }

    @Nested
    @DisplayName("setCurrentUser / getCurrentUser")
    class SetAndGet {

        @Test
        @DisplayName("初始状态 getCurrentUser 返回 null")
        void getCurrentUser_initialNull() {
            assertThat(AuthContext.getCurrentUser()).isNull();
        }

        @Test
        @DisplayName("set 后 get 应返回同一实例")
        void setThenGet_sameInstance() {
            AuthSession session = new AuthSession();
            session.setUsername("doctor1");

            AuthContext.setCurrentUser(session);

            assertThat(AuthContext.getCurrentUser()).isSameAs(session);
            assertThat(AuthContext.getCurrentUser().getUsername()).isEqualTo("doctor1");
        }

        @Test
        @DisplayName("传入 null 等同于清理")
        void setNull_clears() {
            AuthContext.setCurrentUser(new AuthSession());
            AuthContext.setCurrentUser(null);

            assertThat(AuthContext.getCurrentUser()).isNull();
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("clear 后 getCurrentUser 返回 null")
        void clear_makesNull() {
            AuthContext.setCurrentUser(new AuthSession());

            AuthContext.clear();

            assertThat(AuthContext.getCurrentUser()).isNull();
        }

        @Test
        @DisplayName("对未设置的上下文 clear 不抛异常")
        void clear_whenEmpty_noException() {
            AuthContext.clear();
            AuthContext.clear();
            assertThat(AuthContext.getCurrentUser()).isNull();
        }
    }
}
