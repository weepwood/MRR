package com.zjcxph.imgapi.integration.service;

import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
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
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> service.disableUser(firstId)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "LAST_ACTIVE_ADMIN_REQUIRED");
        assertThat(userMapper.countActiveAdmins()).isEqualTo(1);
    }

    @Test
    void editingLastAdminToDisabledIsRejected() {
        jdbc.update("update app.mr_auth_user set status = 'disabled' where id = ?", secondId);
        AuthUserUpdateRequest request = new AuthUserUpdateRequest();
        request.setRoleCode("ADMIN");
        request.setStatus("disabled");

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager)
                .execute(status -> service.updateUser(firstId, request)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("reasonCode", "LAST_ACTIVE_ADMIN_REQUIRED");
        assertThat(userMapper.countActiveAdmins()).isEqualTo(1);
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
        }
    }

    private long insertAdmin(String username) {
        return jdbc.queryForObject("""
                insert into app.mr_auth_user (username, display_name, password_hash, role_code, status)
                values (?, 'Synthetic Admin', 'synthetic-hash', 'ADMIN', 'active') returning id
                """, Long.class, username);
    }
}
