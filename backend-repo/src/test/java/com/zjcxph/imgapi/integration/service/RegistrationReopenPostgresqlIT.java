package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.dto.req.RegistrationApprovalRequest;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegistrationReopenPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthUserMapper users;
    @Autowired AuthRoleMapper roles;

    @org.junit.jupiter.api.AfterEach
    void clearActor() {
        com.zjcxph.imgapi.utils.AuthContext.clear();
    }

    @Test
    void rejectedRegistrationReopensWithoutGrantingLoginOrChangingCredentials() {
        String suffix = UUID.randomUUID().toString();
        Long admin = jdbc.queryForObject(
                "insert into app.mr_auth_user(username,password_hash,role_code,status) values (?, 'synthetic', 'ADMIN','active') returning id",
                Long.class, "reopen-admin-" + suffix);
        String role = jdbc.queryForObject("select code from app.mr_auth_role where upper(code) <> 'ADMIN' order by code limit 1", String.class);
        Long target = jdbc.queryForObject(
                "insert into app.mr_auth_user(username,password_hash,role_code,status,applied_at,reviewed_at,reviewed_by,reject_reason,password_version) values (?, 'synthetic', ?, 'rejected', now(), now(), ?, 'original reason', 7) returning id",
                Long.class, "reopen-target-" + suffix, role, admin);
        var service = new AuthServiceImpl(users, roles, mock(com.zjcxph.imgapi.security.LoginRateLimiter.class), null);
        var session = new com.zjcxph.imgapi.common.AuthSession();
        session.setId(admin);
        session.setPasswordVersion(users.findById(admin).effectivePasswordVersion());
        com.zjcxph.imgapi.utils.AuthContext.setCurrentUser(session);
        var before = users.findById(target);
        assertThatThrownBy(() -> service.disableUser(target)).isInstanceOf(BusinessException.class);
        var reopened = service.reopenRegistration(target, admin, "127.0.0.1");
        assertThat(reopened.getStatus()).isEqualTo("pending");
        var after = users.findById(target);
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(after.getPasswordVersion()).isEqualTo(7);
        assertThat(after.getRoleCode()).isEqualTo(role);
        assertThat(after.getRejectReason()).isEqualTo("original reason");
        assertThat(after.getReviewedBy()).isEqualTo(admin);
        assertThat(after.getAppliedAt()).isEqualTo(before.getAppliedAt());
        assertThat(users.reopenRegistration(target)).isZero();
        assertThatThrownBy(() -> service.disableUser(target)).isInstanceOf(BusinessException.class);
        RegistrationApprovalRequest approval = new RegistrationApprovalRequest();
        approval.setRoleCode(role);
        assertThat(service.approveRegistration(target, approval, admin, "127.0.0.1").getStatus()).isEqualTo("active");
        assertThat(users.findById(target).getRejectReason()).isNull();
    }
}
