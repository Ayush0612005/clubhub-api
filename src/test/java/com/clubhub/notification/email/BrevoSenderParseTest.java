package com.clubhub.notification.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrevoSenderParseTest {

    @Test
    void parsesNamedAndBareAddresses() {
        assertThat(EmailConfig.Sender.parse("ClubHub <team@example.com>"))
                .isEqualTo(new EmailConfig.Sender("ClubHub", "team@example.com"));
        assertThat(EmailConfig.Sender.parse("  SRM Clubs < clubs@example.com > "))
                .isEqualTo(new EmailConfig.Sender("SRM Clubs", "clubs@example.com"));
        assertThat(EmailConfig.Sender.parse("<only@example.com>"))
                .isEqualTo(new EmailConfig.Sender("ClubHub", "only@example.com"));
        assertThat(EmailConfig.Sender.parse("bare@example.com"))
                .isEqualTo(new EmailConfig.Sender("ClubHub", "bare@example.com"));
    }
}
