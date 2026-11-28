package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegistrationApprovalRequest;
import com.zjcxph.imgapi.dto.req.RegistrationRejectionRequest;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@MybatisTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LastActiveAdminPostgresqlIT extends PostgresqlIntegrationTestSupport {

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AuthUserMapper userMapper;
    @Autowired AuthRoleMapper roleMapper;

    private final List<Map<String, Object>> previousAdmins = new ArrayList<>();
    private long firstId;
    private long secondId;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        previousAdmins.addAll(jdbc.queryForList("select id, status from app.mr_auth_user where upper(role_code) = 'ADMIN'"));
        jdbc.update("update app.mr_auth_user set status = 'disabled' where upper(role_code) = 'ADMIN'");
        String suffix = UUID.randomUUID().toString();
        firstId = insertAdmin("synthetic-admin-a-" + suffix);
        secondId = insertAdmin("synthetic-admin-b-" + suffix);
        service = new AuthServiceImpl(userMapper, roleMapper, mock(com.zjcxph.imgapi.security.LoginRateLimiter.class), null);
        assertThat(userMapper.countActiveAdmins()).isEqualTo(2);
    }

    @AfterEach
    void tearDown() {
        com.zjcxph.imgapi.utils.AuthContext.clear();
        jdbc.update("delete from app.mr_auth_user where id in (?, ?)", firstId, secondId);
        for (Map<String, Object> row : previousAdmins) {
            jdbc.update("update app.mr_auth_user set status = ? where id = ?", row.get("status"), row.get("id"));
        }
    }

    @Test
    void concurrentDisablesKeepOneActiveAdmin() throws Exception {
        assertConcurrentRemovalKeepsOne(false);
    }

    @Test
    void concurrentDemotionsKeepOneActiveAdmin() throws Exception {
        assertConcurrentRemovalKeepsOne(true);
    }

    @Test
    void lastActiveAdminHasStableRejectionCode() {
        jdbc.update("update app.mr_auth_user set status = 'disabled' where id = ?", secondId);
        installActor(firstId);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> service.disableUser(firstId)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "LAST_ACTIVE_ADMIN_REQUIRED");
        assertThat(userMapper.countActiveAdmins()).isEqualTo(1);
    }

    @Test
    void editingLastAdminToDisabledIsRejected() {
        jdbc.update("update app.mr_auth_user set status = 'disabled' where id = ?", secondId);
        installActor(firstId);
        AuthUserUpdateRequest request = new AuthUserUpdateRequest();
        request.setRoleCode("ADMIN");
        request.setStatus("disabled");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> service.updateUser(firstId, request)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "LAST_ACTIVE_ADMIN_REQUIRED");
        assertThat(userMapper.countActiveAdmins()).isEqualTo(1);
    }

    @Test
    void revokedAdministratorCannotApproveRegistrationAfterRoleLock() throws Exception {
        assertRevokedAdministratorCannotReview(true);
    }

    @Test
    void revokedAdministratorCannotRejectRegistrationAfterRoleLock() throws Exception {
        assertRevokedAdministratorCannotReview(false);
    }

    @Test
    void revokedAdministratorCannotCreateUserAfterRoleLock() throws Exception {
        String username = "synthetic-new-" + UUID.randomUUID().toString().substring(0, 20);
        var request = new com.zjcxph.imgapi.dto.req.AdminCreateUserRequest();
        request.setUsername(username);
        request.setRoleCode("DOCTOR");
        try {
            assertRevokedAdministratorCannotExecute(false, "USER_CREATED",
                    () -> service.createUser(request, firstId, null));
            assertThat(userMapper.findByUsername(username)).isNull();
        } finally {
            jdbc.update("delete from app.mr_auth_user where username = ?", username);
        }
    }

    @Test
    void demotedAdministratorCannotResetPasswordAfterRoleLock() throws Exception {
        String syntheticPassword = "SyntheticAdminReview123";
        jdbc.update("update app.mr_auth_user set password_hash = ? where id = ?",
                com.zjcxph.imgapi.utils.PasswordUtil.encode(syntheticPassword), firstId);
        var before = userMapper.findById(secondId);
        var request = new com.zjcxph.imgapi.dto.req.AdminResetPasswordRequest();
        request.setAdministratorPassword(syntheticPassword);
        assertRevokedAdministratorCannotExecute(true, "USER_PASSWORD_RESET",
                () -> service.resetPassword(secondId, request, firstId, null));
        var after = userMapper.findById(secondId);
        assertThat(after.getPasswordHash()).isEqualTo(before.getPasswordHash());
        assertThat(after.effectivePasswordVersion()).isEqualTo(before.effectivePasswordVersion());
        assertThat(after.getTemporaryPasswordExpiresAt()).isEqualTo(before.getTemporaryPasswordExpiresAt());
    }

    @Test
    void revokedActorCannotUpdateUserAfterRoleLock() throws Exception {
        assertRevokedActorCannotModifyUser(false);
    }

    @Test
    void revokedActorCannotDisableUserAfterRoleLock() throws Exception {
        assertRevokedActorCannotModifyUser(true);
    }

    private void assertRevokedActorCannotModifyUser(boolean disable) throws Exception {
        long target = jdbc.queryForObject("""
                insert into app.mr_auth_user (username, password_hash, role_code, status)
                values (?, 'synthetic-hash', 'DOCTOR', 'active') returning id
                """, Long.class, "synthetic-managed-" + UUID.randomUUID());
        try {
            var before = userMapper.findById(target);
            assertRevokedAdministratorCannotExecute(false, disable ? "USER_DISABLED" : "USER_ROLE_OR_STATUS_CHANGED", () -> {
                if (disable) service.disableUser(target);
                else {
                    var request = new AuthUserUpdateRequest();
                    request.setRoleCode("DOCTOR");
                    request.setStatus("active");
                    request.setDisplayName("Synthetic edit");
                    service.updateUser(target, request);
                }
            }, "当前账号没有操作权限");
            var after = userMapper.findById(target);
            assertThat(after.getStatus()).isEqualTo(before.getStatus());
            assertThat(after.getDisplayName()).isEqualTo(before.getDisplayName());
            assertThat(after.effectivePasswordVersion()).isEqualTo(before.effectivePasswordVersion());
        } finally {
            jdbc.update("delete from app.mr_auth_user where id = ?", target);
        }
    }

    @Test
    void demotedActorCannotUpdateRoleAfterRoleLock() throws Exception {
        var before = roleMapper.findByCode("DOCTOR");
        try {
            assertRevokedAdministratorCannotExecute(true, "ROLE_UPDATED", () ->
                    service.updateRole("DOCTOR", null, "Synthetic edit", null, null), "当前账号没有操作权限");
            assertThat(roleMapper.findByCode("DOCTOR").getDescription()).isEqualTo(before.getDescription());
        } finally {
            jdbc.update("update app.mr_auth_role set description = ? where code = 'DOCTOR'", before.getDescription());
        }
    }

    @Test
    void delegatedManagerRetainsOnlyGrantedPermissions() {
        String code = "SYNTHETIC_" + UUID.randomUUID();
        jdbc.update("insert into app.mr_auth_role(code,name,permissions) values (?, 'Synthetic manager', 'user:manage,role:manage')", code);
        long target = jdbc.queryForObject("""
                insert into app.mr_auth_user(username,password_hash,role_code,status)
                values (?, 'synthetic-hash', 'DOCTOR', 'active') returning id
                """, Long.class, "synthetic-delegated-" + UUID.randomUUID());
        try {
            jdbc.update("update app.mr_auth_user set role_code = ? where id = ?", code, firstId);
            installActor(firstId);
            var tx = new TransactionTemplate(transactionManager);
            var request = new AuthUserUpdateRequest();
            request.setRoleCode("DOCTOR");
            request.setStatus("active");
            request.setDisplayName("Synthetic managed");
            assertThat(tx.execute(status -> service.updateUser(target, request)).getDisplayName()).isEqualTo("Synthetic managed");
            assertThat(tx.execute(status -> service.updateRole(code, null, "Synthetic description", "user:manage", null)).getDescription()).isEqualTo("Synthetic description");
            // The same request context retains a stale role:manage snapshot; the database no longer grants it.
            assertThatThrownBy(() -> tx.execute(status -> service.updateRole(code, null, "Denied", null, null)))
                    .isInstanceOf(BusinessException.class).hasMessage("当前账号没有操作权限");
            assertThat(roleMapper.findByCode(code).getDescription()).isEqualTo("Synthetic description");
            Integer disabled = tx.execute(status -> service.disableUser(target));
            assertThat(disabled).isEqualTo(1);
            assertThat(userMapper.findById(target).getStatus()).isEqualTo("disabled");
        } finally {
            com.zjcxph.imgapi.utils.AuthContext.clear();
            jdbc.update("update app.mr_auth_user set role_code = 'ADMIN' where id = ?", firstId);
            jdbc.update("delete from app.mr_auth_user where id = ?", target);
            jdbc.update("delete from app.mr_auth_role where code = ?", code);
        }
    }

    private void installActor(long id) {
        var user = userMapper.findById(id);
        var session = new com.zjcxph.imgapi.common.AuthSession();
        session.setId(id);
        session.setRoleCode(user.getRoleCode());
        session.setPasswordVersion(user.effectivePasswordVersion());
        session.setPermissions(user.getPermissions());
        session.setStatus(user.getStatus());
        com.zjcxph.imgapi.utils.AuthContext.setCurrentUser(session);
    }

    private void assertRevokedAdministratorCannotExecute(boolean demote, String auditEvent, Runnable operation) throws Exception {
        assertRevokedAdministratorCannotExecute(demote, auditEvent, operation, "当前账号不是有效管理员");
    }

    private void assertRevokedAdministratorCannotExecute(boolean demote, String auditEvent, Runnable operation, String denial) throws Exception {
        var worker = Executors.newSingleThreadExecutor();
        String runId = "mrr-admin-write-race-" + UUID.randomUUID();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("SECURITY_AUDIT");
        var audit = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        audit.start();
        logger.addAppender(audit);
        try {
            installActor(secondId);
            Future<String> outcome = new TransactionTemplate(transactionManager).execute(status -> {
                if (demote) {
                    var update = new AuthUserUpdateRequest();
                    update.setRoleCode("DOCTOR");
                    update.setStatus("active");
                    service.updateUser(firstId, update);
                } else {
                    service.disableUser(firstId);
                }
                Future<String> result = worker.submit(() -> {
                    try {
                        installActor(firstId);
                        return new TransactionTemplate(transactionManager).execute(tx -> {
                            jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, runId);
                            operation.run();
                            return "SUCCESS";
                        });
                    } catch (BusinessException failure) {
                        return failure.getMessage();
                    } finally {
                        com.zjcxph.imgapi.utils.AuthContext.clear();
                    }
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!result.isDone() && System.nanoTime() < deadline) {
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    int waiting = jdbc.queryForObject("""
                            select count(*) from pg_stat_activity
                            where application_name = ? and wait_event_type = 'Lock'
                            """, Integer.class, runId);
                    if (waiting == 1) return result;
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                assertThat(result.isDone()).isTrue();
                return result;
            });
            assertThat(outcome.get(5, TimeUnit.SECONDS)).isEqualTo(denial);
            assertThat(audit.list).noneMatch(event -> event.getArgumentArray() != null && event.getArgumentArray().length > 0
                    && Long.valueOf(firstId).equals(event.getArgumentArray()[0])
                    && event.getMessage().contains("event=" + auditEvent + " ")
                    && event.getMessage().contains("result=SUCCESS"));
        } finally {
            com.zjcxph.imgapi.utils.AuthContext.clear();
            worker.shutdownNow();
            logger.detachAppender(audit);
            audit.stop();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertRevokedAdministratorCannotReview(boolean approve) throws Exception {
        long target = jdbc.queryForObject("""
                insert into app.mr_auth_user (username, password_hash, role_code, status)
                values (?, 'synthetic-hash', 'DOCTOR', 'pending') returning id
                """, Long.class, "synthetic-review-" + UUID.randomUUID());
        var worker = Executors.newSingleThreadExecutor();
        String runId = "mrr-review-race-" + UUID.randomUUID();
        try {
            installActor(secondId);
            Future<String> review = new TransactionTemplate(transactionManager).execute(status -> {
                // Hold the same lock as the real account administration path and revoke
                // the actor without committing until the competing review reaches PG.
                service.disableUser(firstId);
                Future<String> result = worker.submit(() -> {
                    try {
                        return new TransactionTemplate(transactionManager).execute(tx -> {
                            jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, runId);
                            if (approve) {
                                RegistrationApprovalRequest request = new RegistrationApprovalRequest();
                                request.setRoleCode("DOCTOR");
                                service.approveRegistration(target, request, firstId, null);
                            } else {
                                RegistrationRejectionRequest request = new RegistrationRejectionRequest();
                                request.setRejectReason("synthetic review");
                                service.rejectRegistration(target, request, firstId, null);
                            }
                            return "SUCCESS";
                        });
                    } catch (BusinessException failure) {
                        return failure.getMessage();
                    }
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!result.isDone() && System.nanoTime() < deadline) {
                    // Refresh the transaction-local activity snapshot before observing the worker.
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    int waiting = jdbc.queryForObject("""
                            select count(*) from pg_stat_activity
                            where application_name = ? and wait_event_type = 'Lock'
                            """, Integer.class, runId);
                    if (waiting == 1) return result;
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
                // Old implementation can commit while the revocation is uncommitted.
                // A timeout without an observed lock is an invalid experiment.
                assertThat(result.isDone()).isTrue();
                return result;
            });
            assertThat(review.get(5, TimeUnit.SECONDS)).isEqualTo("当前账号不是有效管理员");
            assertThat(userMapper.findById(target).getStatus()).isEqualTo("pending");
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            jdbc.update("delete from app.mr_auth_user where id = ?", target);
        }
    }

    private void assertConcurrentRemovalKeepsOne(boolean demote) throws Exception {
        var workers = Executors.newFixedThreadPool(3);
        var rowsLocked = new CountDownLatch(1);
        var releaseRows = new CountDownLatch(1);
        var start = new CountDownLatch(1);
        String runId = "mrr-admin-race-" + UUID.randomUUID();
        try {
            Future<?> blocker = workers.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForList("select id from app.mr_auth_user where id in (?, ?) for update", firstId, secondId);
                rowsLocked.countDown();
                try {
                    assertThat(releaseRows.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                return null;
            }));
            assertThat(rowsLocked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<String> first = workers.submit(() -> removeAfterStart(start, firstId, demote, runId));
            Future<String> second = workers.submit(() -> removeAfterStart(start, secondId, demote, runId));
            start.countDown();
            // 等待两个事务确实进入 PostgreSQL 锁等待，再放开目标行。
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            int waiting = 0;
            while (System.nanoTime() < deadline) {
                waiting = jdbc.queryForObject("""
                        select count(*) from pg_stat_activity
                        where application_name like ? and wait_event_type = 'Lock'
                        """, Integer.class, runId + "%");
                if (waiting == 2) break;
                Thread.sleep(50);
            }
            assertThat(waiting).isEqualTo(2);
            assertThat(first.isDone()).isFalse();
            assertThat(second.isDone()).isFalse();
            releaseRows.countDown();
            blocker.get(5, TimeUnit.SECONDS);
            assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SUCCESS", "LAST_ACTIVE_ADMIN_REQUIRED");
            assertThat(userMapper.countActiveAdmins()).isEqualTo(1);
        } finally {
            releaseRows.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private String removeAfterStart(CountDownLatch start, long id, boolean demote, String runId) throws Exception {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            installActor(id);
            return new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, runId + "-" + id);
                if (demote) {
                    AuthUserUpdateRequest request = new AuthUserUpdateRequest();
                    request.setRoleCode("DOCTOR");
                    request.setStatus("active");
                    service.updateUser(id, request);
                } else {
                    service.disableUser(id);
                }
                return "SUCCESS";
            });
        } catch (BusinessException exception) {
            return exception.getMessage().contains("最后一个有效管理员")
                    ? "LAST_ACTIVE_ADMIN_REQUIRED" : exception.getMessage();
        } finally {
            com.zjcxph.imgapi.utils.AuthContext.clear();
        }
    }

    private long insertAdmin(String username) {
        return jdbc.queryForObject("""
                insert into app.mr_auth_user (username, display_name, password_hash, role_code, status)
                values (?, 'Synthetic Admin', 'synthetic-hash', 'ADMIN', 'active') returning id
                """, Long.class, username);
    }
}
