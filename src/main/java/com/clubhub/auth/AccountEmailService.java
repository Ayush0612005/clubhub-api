package com.clubhub.auth;

import com.clubhub.notification.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Verification and password-reset emails.
 *
 * Sent after the transaction commits (no email for a rolled-back signup) and off the request thread,
 * so "forgot password" answers in the same time whether or not the address has an account.
 */
@Service
public class AccountEmailService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AccountEmailService.class);

    private final EmailSender sender;
    private final String appUrl;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AccountEmailService(EmailSender sender, @Value("${clubhub.web.app-url}") String appUrl) {
        this.sender = sender;
        this.appUrl = appUrl.replaceAll("/+$", "");
    }

    public void sendVerification(String to, String name, String rawToken) {
        String link = appUrl + "/verify-email?token=" + encode(rawToken);
        sendAfterCommit(to, "Confirm your ClubHub email", """
                Hi %s,

                Confirm this is your email to finish creating your ClubHub account:

                %s

                The link works once and expires in 24 hours. If you didn't sign up, ignore this email.

                ClubHub""".formatted(name, link));
    }

    public void sendPasswordReset(String to, String name, String rawToken) {
        String link = appUrl + "/reset-password?token=" + encode(rawToken);
        sendAfterCommit(to, "Reset your ClubHub password", """
                Hi %s,

                Someone asked to reset the password for your ClubHub account. To choose a new one, open:

                %s

                The link works once and expires in 30 minutes. If it wasn't you, ignore this email:
                your password stays the same.

                ClubHub""".formatted(name, link));
    }

    private void sendAfterCommit(String to, String subject, String body) {
        Runnable send = () -> executor.execute(() -> {
            try {
                sender.send(to, subject, body);
            } catch (RuntimeException e) {
                // the user can ask for a new link; never fail the request because the mail provider hiccuped
                log.warn("Could not send \"{}\" email: {}", subject, e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void destroy() {
        executor.close();
    }
}
