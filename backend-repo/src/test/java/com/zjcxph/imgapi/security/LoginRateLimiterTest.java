package com.zjcxph.imgapi.security;

import com.github.benmanes.caffeine.cache.Ticker;
import com.zjcxph.imgapi.setting.RuntimeSettingStore;
import com.zjcxph.imgapi.setting.SettingRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    @DisplayName("锁定窗口设为 60 分钟时，40 分钟后仍保持限制（缓存空闲过期不得早于窗口）")
    void longLockWindowIsNotCutShortByCacheExpiry() {
        RuntimeSettingStore settings = mock(RuntimeSettingStore.class);
        when(settings.intValue(SettingRegistry.LOGIN_MAX_ATTEMPTS)).thenReturn(5);
        when(settings.intValue(SettingRegistry.LOGIN_LOCK_WINDOW_MINUTES)).thenReturn(60);
        LoginRateLimiter limiter = new LoginRateLimiter(nowNanos::get, nowMillis::get, settings);
        String key = "admin|10.0.0.8";
        for (int i = 0; i < 5; i++) {
            limiter.recordLoginFailure(key);
        }

        advance(Duration.ofMinutes(40));
        assertThat(limiter.isLoginBlocked(key)).isTrue();

        advance(Duration.ofMinutes(20).plusMillis(1));
        assertThat(limiter.isLoginBlocked(key)).isFalse();
    }

    @Test
    @DisplayName("注册窗口设为 10 分钟时，6 分钟后仍保持限制")
    void longRegisterWindowIsNotCutShortByCacheExpiry() {
        RuntimeSettingStore settings = mock(RuntimeSettingStore.class);
        when(settings.intValue(SettingRegistry.REGISTER_MAX_ATTEMPTS_PER_IP)).thenReturn(3);
        when(settings.intValue(SettingRegistry.REGISTER_WINDOW_SECONDS)).thenReturn(600);
        LoginRateLimiter limiter = new LoginRateLimiter(nowNanos::get, nowMillis::get, settings);
        for (int i = 0; i < 3; i++) {
            limiter.recordRegisterAttempt("10.0.0.8");
        }

        advance(Duration.ofMinutes(6));

        assertThat(limiter.isRegisterBlocked("10.0.0.8")).isTrue();
    }

    @Test
    @DisplayName("轮换来源 IP 的失败累计到账号维度上限后被限制")
    void blocksAccountAcrossRotatingAddresses() {
        int limit = 5 * LoginRateLimiter.ACCOUNT_ATTEMPT_MULTIPLIER;
        for (int i = 0; i < limit - 1; i++) {
            rateLimiter.recordLoginFailure("admin|10.0.1." + i);
            rateLimiter.recordAccountFailure("admin");
        }
        assertThat(rateLimiter.isAccountBlocked("admin")).isFalse();
        assertThat(rateLimiter.isLoginBlocked("admin|10.0.1.0")).isFalse();

        rateLimiter.recordAccountFailure("admin");

        assertThat(rateLimiter.isAccountBlocked("admin")).isTrue();
        assertThat(rateLimiter.isAccountBlocked("doctor")).isFalse();
    }

    @Test
    @DisplayName("账号维度限制随窗口过期，并在登录成功后清除")
    void accountFailuresExpireAndReset() {
        int limit = 5 * LoginRateLimiter.ACCOUNT_ATTEMPT_MULTIPLIER;
        for (int i = 0; i < limit; i++) {
            rateLimiter.recordAccountFailure("admin");
        }
        assertThat(rateLimiter.isAccountBlocked("admin")).isTrue();

        advance(Duration.ofMinutes(15).plusMillis(1));
        assertThat(rateLimiter.isAccountBlocked("admin")).isFalse();

        for (int i = 0; i < limit; i++) {
            rateLimiter.recordAccountFailure("admin");
        }
        rateLimiter.resetAccountFailures("admin");
        assertThat(rateLimiter.isAccountBlocked("admin")).isFalse();
    }

    private void advance(Duration duration) {
        nowMillis.addAndGet(duration.toMillis());
        nowNanos.addAndGet(duration.toNanos());
    }
}
