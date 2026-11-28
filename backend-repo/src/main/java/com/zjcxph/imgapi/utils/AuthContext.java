package com.zjcxph.imgapi.utils;

import com.zjcxph.imgapi.common.AuthSession;

public final class AuthContext {

    /** Server-only context; never serialize a token hash into AuthSession or logs. */
    private record Current(AuthSession user, String tokenHash) { }
    private static final ThreadLocal<Current> CURRENT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setCurrentUser(AuthSession user) {
        setCurrentUser(user, null);
    }

    public static void setCurrentUser(AuthSession user, String tokenHash) {
        if (user == null) {
            clear();
            return;
        }
        CURRENT.set(new Current(user, tokenHash));
    }

    public static AuthSession getCurrentUser() {
        Current current = CURRENT.get();
        return current == null ? null : current.user();
    }

    public static String getCurrentTokenHash() {
        Current current = CURRENT.get();
        return current == null ? null : current.tokenHash();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
