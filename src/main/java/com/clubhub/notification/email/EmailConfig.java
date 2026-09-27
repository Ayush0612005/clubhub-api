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
        return (to, subject, body) -> ses.sendEmail(request -> request
                .fromEmailAddress(from)
                .destination(Destination.builder().toAddresses(to).build())
                .content(EmailContent.builder().simple(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder().text(Content.builder().data(body).charset("UTF-8").build()).build())
                        .build()).build()));
    }

    @Bean
    @ConditionalOnProperty(name = "clubhub.mail.provider", havingValue = "log", matchIfMissing = true)
    EmailSender loggingEmailSender() {
        return (to, subject, body) -> log.info("[email not sent: provider=log] to={} subject=\"{}\"", to, subject);
    }
}
