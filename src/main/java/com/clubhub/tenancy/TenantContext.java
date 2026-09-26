package com.clubhub.tenancy;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * The club schema the current request is working in.
 *
 * Uses a Java 25 ScopedValue instead of a ThreadLocal: the value exists only while
 * {@link #runAs}/{@link #callAs} is executing and disappears automatically afterwards.
 * With a ThreadLocal, forgetting clear() in a finally block would leak one club's
 * schema into the next request served by the same pooled thread.
 */
public final class TenantContext {

    private static final ScopedValue<String> CURRENT_SCHEMA = ScopedValue.newInstance();

    private TenantContext() {
    }

    public static Optional<String> currentSchema() {
        return CURRENT_SCHEMA.isBound() ? Optional.of(CURRENT_SCHEMA.get()) : Optional.empty();
    }

    public static void runAs(String schema, Runnable action) {
        ScopedValue.where(CURRENT_SCHEMA, requireSchema(schema)).run(action);
    }

    public static <T> T callAs(String schema, Callable<T> action) throws Exception {
        return ScopedValue.where(CURRENT_SCHEMA, requireSchema(schema)).call(action::call);
    }

    public static <T> T supplyAs(String schema, Supplier<T> action) {
        return ScopedValue.where(CURRENT_SCHEMA, requireSchema(schema)).call(action::get);
    }

    private static String requireSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            throw new IllegalArgumentException("Tenant schema must not be blank");
        }
        return schema;
    }
}
