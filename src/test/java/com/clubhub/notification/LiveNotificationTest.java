package com.clubhub.notification;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.security.JwtTokenService;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;

/** A real WebSocket client on a real port: event → Kafka → consumer → STOMP push to that user only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LiveNotificationTest {

    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired JwtTokenService tokens;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired DomainEventPublisher domainEvents;

    StompSession connect(User user, BlockingQueue<Map<String, Object>> inbox) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + tokens.issueAccessToken(user).value());

        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                connect, new StompSessionHandlerAdapter() { }).get(10, TimeUnit.SECONDS);
        session.subscribe("/user/queue/notifications", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
            }
        });
        Thread.sleep(500); // let the SUBSCRIBE frame reach the broker before anything is pushed
        return session;
    }

    @Test
    void notificationIsPushedOnlyToItsRecipient() throws Exception {
        Tenant club = provisioningService.provision("live_club", "Live Club", newUserId(users));
        User alice = users.findById(newUserId(users)).orElseThrow();
        User bob = users.findById(newUserId(users)).orElseThrow();
        BlockingQueue<Map<String, Object>> aliceInbox = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> bobInbox = new LinkedBlockingQueue<>();
        StompSession aliceSession = connect(alice, aliceInbox);
        StompSession bobSession = connect(bob, bobInbox);

        // outside a transaction: relayed to Kafka immediately (fallbackExecution)
        domainEvents.publish(new DomainEvent.CertificateIssued(UUID.randomUUID(), DomainEvent.Club.of(club),
                Instant.now(), alice.getId(), UUID.randomUUID(), "Certificate of Participation"));

        Map<String, Object> pushed = aliceInbox.poll(30, TimeUnit.SECONDS);
        assertThat(pushed).isNotNull();
        assertThat(pushed.get("type")).isEqualTo("CERTIFICATE_ISSUED");
        assertThat(pushed.get("title")).isEqualTo("New certificate from Live Club");
        assertThat(bobInbox.poll(2, TimeUnit.SECONDS)).isNull();

        aliceSession.disconnect();
        bobSession.disconnect();
    }
}
