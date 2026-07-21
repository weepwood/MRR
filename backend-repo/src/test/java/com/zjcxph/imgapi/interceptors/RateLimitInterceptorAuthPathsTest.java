package com.zjcxph.imgapi.interceptors;

import com.zjcxph.imgapi.security.ApiRateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitInterceptorAuthPathsTest {

    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(mock(ApiRateLimiter.class));

    @Test
    void shouldRateLimitPasswordAndCredentialEndpoints() {
        assertThat(interceptor.isRateLimited("/api/v1/auth/login")).isTrue();
        assertThat(interceptor.isRateLimited("/api/v1/auth/password/edit")).isTrue();
        assertThat(interceptor.isRateLimited("/api/v1/auth/password/required-change")).isTrue();
        assertThat(interceptor.isRateLimited("/api/v1/auth/users/20/password/reset")).isTrue();
    }

    @Test
    void shouldNotTreatOrdinaryUserReadsAsSensitivePasswordAttempts() {
        assertThat(interceptor.isRateLimited("/api/v1/auth/me")).isFalse();
        assertThat(interceptor.isRateLimited("/api/v1/auth/users")).isFalse();
        assertThat(interceptor.isRateLimited("/api/v1/auth/users/20")).isFalse();
    }
}
