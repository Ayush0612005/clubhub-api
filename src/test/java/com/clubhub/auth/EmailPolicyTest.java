package com.clubhub.auth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailPolicyTest {

    private final EmailPolicy policy = new EmailPolicy(
            List.of("srmist.edu.in"), List.of("Guest.Speaker@Outlook.com"), "owner@gmail.com");

    @Test
    void collegeDomainIsAllowedIgnoringCase() {
        assertThat(policy.isAllowed("ak4810@srmist.edu.in")).isTrue();
        assertThat(policy.isAllowed("  AK4810@SRMIST.EDU.IN ")).isTrue();
    }

    @Test
    void otherDomainsAndLookalikesAreRejected() {
        assertThat(policy.isAllowed("someone@gmail.com")).isFalse();
        assertThat(policy.isAllowed("x@fake-srmist.edu.in")).isFalse();
        assertThat(policy.isAllowed("x@srmist.edu.in.evil.com")).isFalse();
        assertThat(policy.isAllowed("x@mail.srmist.edu.in")).isFalse(); // exact domain, no subdomains
        assertThat(policy.isAllowed(null)).isFalse();
    }

    @Test
    void adminAndExtraEmailsAreExceptions() {
        assertThat(policy.isAllowed("OWNER@gmail.com")).isTrue();
        assertThat(policy.isAllowed("guest.speaker@outlook.com")).isTrue();
        assertThat(policy.isAllowed("other@gmail.com")).isFalse();
    }

    @Test
    void emptyDomainListMeansNoRestriction() {
        EmailPolicy open = new EmailPolicy(List.of(), List.of(), "");
        assertThat(open.isAllowed("anyone@gmail.com")).isTrue();
    }

    @Test
    void rejectionMessageNamesTheDomain() {
        assertThatThrownBy(() -> policy.requireAllowed("someone@gmail.com"))
                .isInstanceOf(AuthExceptions.EmailNotAllowedException.class)
                .hasMessageContaining("@srmist.edu.in");
    }
}
