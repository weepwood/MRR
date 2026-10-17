package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import org.springframework.stereotype.Service;

/** Re-reads durable authorization; no task-supplied username or expired JWT is trusted. */
@Service
public class OcrAccess {
    private final AuthUserMapper users;
    public OcrAccess(AuthUserMapper users) { this.users = users; }

    public AuthSession require(Long id, String... permissions) {
        if (id == null) throw new BusinessException(401, "请先登录");
        AuthUser user = users.findById(id);
        if (user == null || !"active".equalsIgnoreCase(user.getStatus()) || user.isPasswordChangeRequired())
            throw new BusinessException(403, "OCR 授权账号不可用");
        AuthSession actor = new AuthSession();
        actor.setId(user.getId()); actor.setUsername(user.getUsername());
        actor.setRoleCode(user.getRoleCode()); actor.setStatus(user.getStatus());
        actor.setPermissions(user.getPermissions());
        for (String permission : permissions)
            if (!actor.hasPermission(permission)) throw new BusinessException(403, "没有 OCR 操作权限");
        return actor;
    }
}
