package com.zjcxph.imgapi.security;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import org.springframework.stereotype.Service;

/** Re-reads durable authorization; no task-supplied username or expired JWT is trusted. */
@Service
public class MedicalAccess {
    private final AuthUserMapper users;
    private final com.zjcxph.imgapi.mapper.AuthRoleMapper roles;
    public MedicalAccess(AuthUserMapper users, com.zjcxph.imgapi.mapper.AuthRoleMapper roles) {
        this.users = users;
        this.roles = roles;
    }

    /** Request-bound sensitive writes: use the existing management lock order and verified token. */
    public AuthSession requireWrite(Long id, String... permissions) {
        lockAuthorization(id);
        var session = com.zjcxph.imgapi.utils.AuthContext.getCurrentUser();
        String hash = com.zjcxph.imgapi.utils.AuthContext.getCurrentTokenHash();
        if (session == null || !id.equals(session.getId()))
            throw new BusinessException(403, "当前授权账号不可用");
        if (hash == null) return require(id, permissions);
        var user = users.observeSession(id, hash);
        if (user == null || !Boolean.FALSE.equals(user.getTokenRevoked())
                || user.effectivePasswordVersion() != session.effectivePasswordVersion())
            throw new BusinessException(403, "当前授权账号不可用");
        return authorize(user, permissions);
    }

    public AuthSession requireCurrentWrite(String... permissions) {
        var session = com.zjcxph.imgapi.utils.AuthContext.getCurrentUser();
        if (session == null || session.getId() == null) throw new BusinessException(401, "请先登录");
        return requireWrite(session.getId(), permissions);
    }

    /** Durable background tasks are authorized by their current owner, not a request JWT. */
    public AuthSession requireExecutionWrite(Long id, String... permissions) {
        lockAuthorization(id);
        return require(id, permissions);
    }

    private void lockAuthorization(Long id) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("医疗写入授权需要事务");
        if (id == null || roles.lockAdminRole() == null || users.lockAuthorizationActor(id) == null)
            throw new BusinessException(403, "当前授权账号不可用");
    }

    public AuthSession require(Long id, String... permissions) {
        if (id == null) throw new BusinessException(401, "请先登录");
        return authorize(users.findSessionById(id), permissions);
    }

    private AuthSession authorize(AuthUser user, String... permissions) {
        if (user == null || !"active".equalsIgnoreCase(user.getStatus()) || user.isPasswordChangeRequired())
            throw new BusinessException(403, "当前授权账号不可用");
        AuthSession actor = new AuthSession();
        actor.setId(user.getId()); actor.setUsername(user.getUsername());
        actor.setRoleCode(user.getRoleCode()); actor.setStatus(user.getStatus());
        actor.setPermissions(user.getPermissions());
        for (String permission : permissions)
            if (!actor.hasPermission(permission)) throw new BusinessException(403, "没有操作权限");
        return actor;
    }
}
