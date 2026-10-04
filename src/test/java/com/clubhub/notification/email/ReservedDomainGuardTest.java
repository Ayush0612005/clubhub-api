package com.clubhub.notification.email;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedDomainGuardTest {

    @Test
    void reservedTestDomainsNeverReachTheProvider() {
        List<String> delivered = new ArrayList<>();
        EmailSender guarded = EmailConfig.skippingReservedDomains((to, subject, body) -> delivered.add(to));

        guarded.send("kavya.nair@demo.clubhub.invalid", "s", "b");
        guarded.send("someone@example.test", "s", "b");
        guarded.send("ab1234@srmist.edu.in", "s", "b");
        guarded.send("Me@Gmail.com ", "s", "b");

        assertThat(delivered).containsExactly("ab1234@srmist.edu.in", "Me@Gmail.com ");
    }
}
