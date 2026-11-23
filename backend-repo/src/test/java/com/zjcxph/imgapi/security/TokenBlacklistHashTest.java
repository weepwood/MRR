package com.zjcxph.imgapi.security;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("TokenBlacklist 撤销哈希查询")
class TokenBlacklistHashTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final TokenBlacklist blacklist = new TokenBlacklist(jdbc);

    @Test
    @DisplayName("tokenHash 与撤销表存储形式一致（SHA-256 十六进制）")
    void tokenHashMatchesRevocationStorage() {
        assertThat(TokenBlacklist.tokenHash("synthetic-jti")).isEqualTo(DigestUtils.sha256Hex("synthetic-jti"));
        assertThatThrownBy(() -> TokenBlacklist.tokenHash(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("按哈希查询撤销状态")
    void queriesRevocationByHash() {
        String hash = TokenBlacklist.tokenHash("synthetic-jti");
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(hash))).thenReturn(true);

        assertThat(blacklist.isRevokedHash(hash)).isTrue();
        verify(jdbc).queryForObject(anyString(), eq(Boolean.class), eq(hash));
    }

    @Test
    @DisplayName("格式非法的哈希按已撤销处理且不查询数据库")
    void malformedHashFailsClosed() {
        assertThat(blacklist.isRevokedHash(null)).isTrue();
        assertThat(blacklist.isRevokedHash("ABC")).isTrue();
        assertThat(blacklist.isRevokedHash("G".repeat(64))).isTrue();
        verifyNoInteractions(jdbc);
    }
}
