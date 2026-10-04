package com.clubhub.demo;

import com.clubhub.common.NotFoundException;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.tenant.TenantRepository;
import com.clubhub.tenant.TenantSchemaMigrator;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * A shared, self-resetting club that anyone can explore as its admin with one click.
 *
 * Reset is lazy: the first demo login after {@code reset-every} rebuilds the club, instead of a
 * scheduler. Free hosts sleep when idle and a sleeping process runs no schedules, while a lazy
 * reset always happens exactly when someone is about to look.
 *
 * The tenants row (and so the tenant id inside visitors' JWTs) survives resets: only the club's
 * schema is dropped and rebuilt, so a visitor mid-session keeps a valid token.
 *
 * Single-instance assumption: the lock is in-process. With several API instances this would move to
 * a Postgres advisory lock and a reset timestamp in the database.
 */
@Service
public class DemoSandbox {

    private static final Logger log = LoggerFactory.getLogger(DemoSandbox.class);

    private final boolean enabled;
    private final Duration resetEvery;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenants;
    private final TenantProvisioningService provisioning;
    private final TenantSchemaMigrator migrator;
    private final DemoSeeder seeder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private Instant lastReset; // guarded by this

    public DemoSandbox(@Value("${clubhub.demo.enabled:true}") boolean enabled,
                       @Value("${clubhub.demo.reset-every:PT1H}") Duration resetEvery,
                       UserRepository users, PasswordEncoder passwordEncoder, TenantRepository tenants,
                       TenantProvisioningService provisioning, TenantSchemaMigrator migrator, DemoSeeder seeder,
                       JdbcTemplate jdbc, TransactionTemplate tx) {
        this.enabled = enabled;
        this.resetEvery = resetEvery;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tenants = tenants;
        this.provisioning = provisioning;
        this.migrator = migrator;
        this.seeder = seeder;
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Makes sure the demo club is fresh and returns the visitor account to sign in as. */
    public synchronized User prepareVisit() {
        if (!enabled) {
            throw new NotFoundException("The demo is switched off on this server");
        }
        Instant now = Instant.now();
        if (lastReset == null || now.isAfter(lastReset.plus(resetEvery))) {
            reset();
            lastReset = now;
        }
        return users.findByEmail(DemoAccounts.VISITOR_EMAIL).orElseThrow();
    }

    /** Tests: rebuild now, whatever the clock says. */
    synchronized void resetNow() {
        reset();
        lastReset = Instant.now();
    }

    private void reset() {
        long started = System.nanoTime();
        User visitor = ensureUser(DemoAccounts.VISITOR_EMAIL, "Demo Visitor");
        Map<String, UUID> people = new HashMap<>();
        DemoSeeder.CAST.forEach((handle, name) -> people.put(handle, ensureUser(emailFor(name), name).getId()));

        Tenant club = tenants.findBySlug(DemoAccounts.CLUB_SLUG).orElse(null);
        if (club == null) {
            club = provisioning.provision(DemoAccounts.CLUB_SLUG, DemoAccounts.CLUB_NAME);
        }
        String schema = club.getSchemaName();
        // a constant ("club_demo"), never user input; still double-checked before it goes into DDL
        if (!schema.matches("club_[a-z0-9_]+")) {
            throw new IllegalStateException("Unexpected demo schema name " + schema);
        }
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        migrator.migrate(schema);

        UUID tenantId = club.getId();
        tx.executeWithoutResult(status -> {
            // whatever visitors changed outside the schema goes too: renames, members they added, notifications
            jdbc.update("UPDATE public.tenants SET name = ?, plan = 'PRO', status = 'ACTIVE' WHERE id = ?",
                    DemoAccounts.CLUB_NAME, tenantId);
            jdbc.update("DELETE FROM public.tenant_features WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM public.notifications WHERE tenant_id = ?", tenantId);
            jdbc.update("DELETE FROM public.memberships WHERE tenant_id = ?", tenantId);
            seeder.seed(schema, tenantId, visitor.getId(), people);
        });
        log.info("Demo club reset in {} ms", Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    /** Demo people are created once and kept; none of them can log in with a password. */
    private User ensureUser(String email, String fullName) {
        return users.findByEmail(email).orElseGet(() -> {
            // a random secret nobody ever sees: the account exists, but no password opens it
            User user = new User(email, passwordEncoder.encode(UUID.randomUUID().toString()), fullName);
            user.markEmailVerified();
            return users.saveAndFlush(user);
        });
    }

    static String emailFor(String fullName) {
        return fullName.toLowerCase(Locale.ROOT).replace(' ', '.') + "@" + DemoAccounts.DOMAIN;
    }
}
