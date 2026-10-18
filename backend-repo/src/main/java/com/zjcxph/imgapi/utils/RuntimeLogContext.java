package com.zjcxph.imgapi.utils;

public final class RuntimeLogContext {
    private static final ThreadLocal<Boolean> INGESTING = new ThreadLocal<>();
    public static final java.util.concurrent.atomic.AtomicLong SUPPRESSED = new java.util.concurrent.atomic.AtomicLong();
    private RuntimeLogContext() { }
    public static void enter() { INGESTING.set(true); }
    public static void leave() { INGESTING.remove(); }
    public static boolean ingesting() { return Boolean.TRUE.equals(INGESTING.get()); }
}
