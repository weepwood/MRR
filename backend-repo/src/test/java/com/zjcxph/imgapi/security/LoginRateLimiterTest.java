package com.zjcxph.imgapi.security;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginRateLimiter 滑动窗口测试")
class LoginRateLimiterTest {

    private final AtomicLong nowMillis = new AtomicLong(1_000_000L);
    private final AtomicLong nowNanos = new AtomicLong();
    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        Ticker ticker = nowNanos::get;
        rateLimiter = new LoginRateLimiter(ticker, nowMillis::get);
    }

    @Test
    @DisplayName("同一账号和 IP 达到五次失败后被限制")
    void blocksAfterFiveLoginFailures() {
        String key = "admin|10.0.0.8";
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordLoginFailure(key);
        }

        assertThat(rateLimiter.isLoginBlocked(key)).isTrue();
        assertThat(rateLimiter.isLoginBlocked("admin|10.0.0.9")).isFalse();
    }

    @Test
    @DisplayName("登录失败窗口过期后自动解除限制")
    void loginWindowExpires() {
        String key = "admin|10.0.0.8";
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordLoginFailure(key);
        }
        assertThat(rateLimiter.isLoginBlocked(key)).isTrue();

        advance(Duration.ofMinutes(15).plusMillis(1));

        assertThat(rateLimiter.isLoginBlocked(key)).isFalse();
    }

    @Test
    @DisplayName("登录成功后清除失败记录")
    void resetClearsLoginFailures() {
        String key = "admin|10.0.0.8";
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordLoginFailure(key);
        }

        rateLimiter.resetLoginFailures(key);

        assertThat(rateLimiter.isLoginBlocked(key)).isFalse();
    }

    @Test
    @DisplayName("同一 IP 一分钟三次注册后被限制并按时恢复")
    void registerWindowIsBounded() {
        String clientIp = "10.0.0.8";
        for (int i = 0; i < 3; i++) {
            rateLimiter.recordRegisterAttempt(clientIp);
        }
        assertThat(rateLimiter.isRegisterBlocked(clientIp)).isTrue();

        advance(Duration.ofMinutes(1).plusMillis(1));

        assertThat(rateLimiter.isRegisterBlocked(clientIp)).isFalse();
    }

    @Test
    @DisplayName("空键不会写入或触发限制")
    void blankKeysAreIgnored() {
        rateLimiter.recordLoginFailure(" ");
        rateLimiter.recordRegisterAttempt(null);

        assertThat(rateLimiter.isLoginBlocked(" ")).isFalse();
        assertThat(rateLimiter.isRegisterBlocked(null)).isFalse();
    }

    private void advance(Duration duration) {
        nowMillis.addAndGet(duration.toMillis());
        nowNanos.addAndGet(duration.toNanos());
    }
}
