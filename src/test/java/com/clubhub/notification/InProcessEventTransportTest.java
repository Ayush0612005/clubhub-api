package com.clubhub.notification;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.notification.email.EmailNotificationConsumer;
import com.clubhub.notification.email.EmailSender;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** The broker-less transport used on free hosting: same guarantees as Kafka for commit/rollback. */
@SpringBootTest(properties = "clubhub.events.transport=in-process")
@Import(TestcontainersConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InProcessEventTransportTest {

    @Autowired ApplicationContext context;
    @Autowired DomainEventPublisher domainEvents;
    @Autowired NotificationService notifications;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean EmailSender sender;

    Tenant club;
    TransactionTemplate tx;

    @BeforeAll
    void createClub() {
        club = provisioningService.provision("inproc_club", "InProc Club", newUserId(users));
        tx = new TransactionTemplate(transactionManager);
    }

    DomainEvent selected(UUID applicant) {
        return new DomainEvent.ApplicationStatusChanged(UUID.randomUUID(), DomainEvent.Club.of(club), Instant.now(),
                applicant, 3, "Design team", "SELECTED");
    }

    static boolean eventually(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    @Test
    void kafkaBeansAreNotCreated() {
        assertThat(context.getBeanNamesForType(InProcessEventRelay.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(KafkaEventRelay.class)).isEmpty();
        assertThat(context.getBeanNamesForType(NotificationConsumer.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EmailNotificationConsumer.class)).isEmpty();
    }

    @Test
    void committedEventReachesInboxAndEmail() throws Exception {
        UUID student = newUserId(users);
        tx.executeWithoutResult(status -> domainEvents.publish(selected(student)));

        assertThat(eventually(() -> notifications.unreadCount(student) == 1)).isTrue();
        String email = users.findById(student).orElseThrow().getEmail();
        verify(sender, timeout(5_000)).send(eq(email), eq("InProc Club: your application is selected"),
                contains("\"Design team\""));
    }

    @Test
    void rolledBackEventIsNeverDelivered() throws Exception {
        UUID student = newUserId(users);
        tx.executeWithoutResult(status -> {
            domainEvents.publish(selected(student));
            status.setRollbackOnly();
        });

        Thread.sleep(1_000);
        assertThat(notifications.unreadCount(student)).isZero();
        verify(sender, never()).send(eq(users.findById(student).orElseThrow().getEmail()), anyString(), anyString());
    }

    @Test
    void emailFailureDoesNotLoseTheInAppNotification() throws Exception {
        UUID student = newUserId(users);
        String email = users.findById(student).orElseThrow().getEmail();
        doThrow(new RuntimeException("SES down")).when(sender).send(eq(email), anyString(), anyString());

        tx.executeWithoutResult(status -> domainEvents.publish(selected(student)));

        assertThat(eventually(() -> notifications.unreadCount(student) == 1)).isTrue();
    }
}
