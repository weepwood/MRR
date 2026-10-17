package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrAccessTest {
    private final AuthUserMapper users = mock(AuthUserMapper.class);
    private final OcrAccess access = new OcrAccess(users);

    @Test void rejectsMissingIdentityAndRevokedPermission() {
        assertThrows(BusinessException.class, () -> access.require(null, "ocr:run"));
        AuthSession claimed = new AuthSession(); claimed.setId(9L); claimed.setRoleCode("ADMIN");
        AuthUser user = user(); user.setPermissionsCsv("record:read,ocr:read");
        when(users.findById(9L)).thenReturn(user);
        assertThrows(BusinessException.class, () -> access.require(claimed.getId(), "ocr:run"));
    }

    @Test void disabledAdministratorCannotExecutePersistedAuthorization() {
        AuthUser user = user(); user.setRoleCode("ADMIN"); user.setStatus("disabled");
        when(users.findById(9L)).thenReturn(user);
        assertThrows(BusinessException.class, () -> access.require(9L, "ocr:run"));
    }

    @Test void resolvesRealActorFromDatabaseInsteadOfTaskUsername() {
        AuthUser user = user(); user.setPermissionsCsv("record:read,ocr:run");
        when(users.findById(9L)).thenReturn(user);
        assertEquals("synthetic-operator", access.require(9L, "ocr:run").getUsername());
    }

    private AuthUser user() {
        AuthUser user = new AuthUser(); user.setId(9L); user.setUsername("synthetic-operator");
        user.setStatus("active"); user.setRoleCode("READER"); return user;
    }
}
