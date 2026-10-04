package com.clubhub.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * clubhub.mail.provider=ses sends through Amazon SES; anything else (the local default) only logs,
 * so developers never email real students by accident.
 */
@Configuration(proxyBeanMethods = false)
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    @ConditionalOnProperty(name = "clubhub.mail.provider", havingValue = "ses")
    EmailSender sesEmailSender(@Value("${clubhub.mail.from}") String from,
                               @Value("${clubhub.storage.region}") String region,
                               AwsCredentialsProvider credentials) {
        SesV2Client ses = SesV2Client.builder().region(Region.of(region)).credentialsProvider(credentials).build();
        return skippingReservedDomains((to, subject, body) -> ses.sendEmail(request -> request
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder().text(Content.builder().data(body).charset("UTF-8").build()).build())
                        .build()).build())));
    }

    /**
     * Brevo's HTTPS API (not SMTP): free hosts like Render block outbound SMTP ports, HTTPS always works.
     * Free tier: 300 emails/day.
     */
    @Bean
    @ConditionalOnProperty(name = "clubhub.mail.provider", havingValue = "brevo")
    EmailSender brevoEmailSender(@Value("${clubhub.mail.from}") String from,
                                 @Value("${clubhub.mail.brevo.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("clubhub.mail.provider=brevo needs BREVO_API_KEY");
        }
        Sender sender = Sender.parse(from);
        RestClient brevo = RestClient.builder()
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", apiKey)
                .build();
        return skippingReservedDomains((to, subject, body) -> brevo.post()
                .uri("/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sender", Map.of("name", sender.name(), "email", sender.email()),
                        "to", List.of(Map.of("email", to)),
                        "subject", subject,
                        "textContent", body))
                .retrieve()
                .toBodilessEntity()); // non-2xx throws, and the caller logs it
    }

    /**
     * Never hand a provider an address under a reserved test TLD (RFC 2606: .invalid, .test, .example,
     * .localhost). The demo club's people live on .invalid; mailing them would only burn the daily quota
     * and, through bounces, the sender's reputation.
     */
    static EmailSender skippingReservedDomains(EmailSender real) {
        return (to, subject, body) -> {
            if (isReserved(to)) {
                log.debug("[email skipped: reserved domain] to={} subject=\"{}\"", to, subject);
                return;
            }
            real.send(to, subject, body);
        };
    }

    static boolean isReserved(String address) {
        String lower = address == null ? "" : address.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.isEmpty() || lower.endsWith(".invalid") || lower.endsWith(".test")
                || lower.endsWith(".example") || lower.endsWith(".localhost");
    }

    @Bean
    @ConditionalOnProperty(name = "clubhub.mail.provider", havingValue = "log", matchIfMissing = true)
    EmailSender loggingEmailSender() {
        // the body is logged so local dev can click verification/reset links; this provider never emails anyone
        return (to, subject, body) -> log.info("[email not sent: provider=log] to={} subject=\"{}\"\n{}", to, subject, body);
    }

    /** "ClubHub <no-reply@example.com>" or a bare address. */
    record Sender(String name, String email) {
        private static final Pattern NAMED = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>\\s]+)\\s*>\\s*$");

        static Sender parse(String from) {
            Matcher m = NAMED.matcher(from);
            if (m.matches()) {
                return new Sender(m.group(1).isBlank() ? "ClubHub" : m.group(1), m.group(2));
            }
            return new Sender("ClubHub", from.trim());
        }
    }
}
