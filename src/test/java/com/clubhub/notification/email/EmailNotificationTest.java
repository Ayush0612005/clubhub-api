package com.clubhub.notification.email;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.notification.DomainEvent;
import com.clubhub.notification.DomainEventPublisher;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmailNotificationTest {

    @Autowired EmailNotificationService emails;
    @Autowired EmailLogRepository emailLog;
    @Autowired DomainEventPublisher domainEvents;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @MockitoBean EmailSender sender;

    Tenant club;

    @BeforeAll
    void createClub() {
        club = provisioningService.provision("mail_club", "Mail Club", newUserId(users));
    }

    DomainEvent.ApplicationStatusChanged selected(UUID applicant) {
        return new DomainEvent.ApplicationStatusChanged(UUID.randomUUID(), DomainEvent.Club.of(club), Instant.now(),
                applicant, 7, "Tech team", "SELECTED");
    }

    String emailOf(UUID userId) {
        return users.findById(userId).orElseThrow().getEmail();
    }

    @Test
    void sendsOneEmailPerEventEvenWhenRedelivered() {
        UUID student = newUserId(users);
        var event = selected(student);

        assertThat(emails.handle(event)).isTrue();
        assertThat(emails.handle(event)).isFalse(); // Kafka redelivery of the same event id

        verify(sender, times(1)).send(eq(emailOf(student)), eq("Mail Club: your application is selected"),
                contains("\"Tech team\""));
    }

    @Test
    void newEventFanOutsAreNotEmailed() {
        var published = new DomainEvent.EventPublished(UUID.randomUUID(), DomainEvent.Club.of(club), Instant.now(),
                1, "Meetup", "Hall", Instant.now());

        assertThat(emails.handle(published)).isFalse();
        verify(sender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void aFailedSendIsNotRecordedSoKafkaCanRetryIt() {
        UUID student = newUserId(users);
        var event = selected(student);
        doThrow(new RuntimeException("SES throttled")).when(sender).send(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> emails.handle(event)).hasMessage("SES throttled");
        assertThat(emailLog.countBySourceEventId(event.id())).isZero(); // rolled back with the failure
    }

    @Test
    void certificateEmailTravelsThroughKafka() {
        UUID student = newUserId(users);
        domainEvents.publish(new DomainEvent.CertificateIssued(UUID.randomUUID(), DomainEvent.Club.of(club),
                Instant.now(), student, UUID.randomUUID(), "Certificate of Participation"));

        verify(sender, timeout(30_000)).send(eq(emailOf(student)), eq("Your certificate from Mail Club"),
                contains("/clubs/mail_club/certificates/"));
    }
}
