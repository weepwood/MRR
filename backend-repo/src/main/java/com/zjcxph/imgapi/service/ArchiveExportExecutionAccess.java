package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.entity.ArchiveExportJob;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.IntSupplier;

/** Current account authorization for a durable user-owned export, independent of its original JWT. */
@Service
public class ArchiveExportExecutionAccess {
    static final String REVOKED = "EXPORT_AUTH_REVOKED";
    static final String MESSAGE = "导出任务授权已失效，请重新发起";
    private final AuthUserMapper users;
    private final AuthRoleMapper roles;
    private final TransactionTemplate transaction;

    public ArchiveExportExecutionAccess(AuthUserMapper users, AuthRoleMapper roles, PlatformTransactionManager manager) {
        this.users = users;
        this.roles = roles;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(10);
    }

    public void require(ArchiveExportJob job) {
        Long ownerId = job.getOwnerUserId();
        var owner = ownerId == null ? null : users.findSessionById(ownerId);
        if (owner == null || !"active".equalsIgnoreCase(owner.getStatus()) || owner.isPasswordChangeRequired()) {
            throw revoked();
        }
        var actor = new AuthSession();
        actor.setRoleCode(owner.getRoleCode());
        actor.setPermissions(owner.getPermissions());
        String permission = switch (job.getFormat()) {
            case "ZIP" -> Permissions.RECORD_DOWNLOAD;
            case "PDF" -> Permissions.RECORD_PDF_EXPORT;
            default -> throw revoked();
        };
        if (!actor.hasPermission(Permissions.RECORD_READ) || !actor.hasPermission(permission)) throw revoked();
    }

    /** Fence publication against management changes; no file I/O inside this transaction. */
    public int publish(ArchiveExportJob job, IntSupplier update) {
        return transaction.execute(status -> {
            if (job.getOwnerUserId() == null || roles.lockAdminRole() == null
                    || users.lockAuthorizationActor(job.getOwnerUserId()) == null) throw revoked();
            require(job);
            return update.getAsInt();
        });
    }

    private BusinessException revoked() {
        return new BusinessException(403, MESSAGE, REVOKED);
    }
}
