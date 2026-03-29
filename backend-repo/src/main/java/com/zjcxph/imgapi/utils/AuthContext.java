package com.zjcxph.imgapi.utils;

import com.zjcxph.imgapi.common.AuthSession;

public final class AuthContext {

    private static final ThreadLocal<AuthSession> CURRENT_USER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setCurrentUser(AuthSession user) {
        if (user == null) {
            CURRENT_USER.remove();
            return;
        }
        CURRENT_USER.set(user);
    }

    public static AuthSession getCurrentUser() {
        return CURRENT_USER.get();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
