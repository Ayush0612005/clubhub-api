package com.clubhub.notification.email;

/** Sends one plain-text email. Implementations: SES in production, a logger for local dev. */
public interface EmailSender {

    void send(String to, String subject, String body);
}
