package com.clubhub.notification;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit test: STOMP frames built by hand, JwtDecoder mocked. */
class StompAuthInterceptorTest {

    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final StompAuthInterceptor interceptor = new StompAuthInterceptor(decoder);
    private final String userId = UUID.randomUUID().toString();

    private static Message<byte[]> frame(StompCommand command, String authorization, String destination,
                                         JwtAuthenticationToken user) {
        StompHeaderAccessor stomp = StompHeaderAccessor.create(command);
        if (authorization != null) {
            stomp.addNativeHeader("Authorization", authorization);
        }
        if (destination != null) {
            stomp.setDestination(destination);
        }
        stomp.setUser(user);
        stomp.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], stomp.getMessageHeaders());
    }

    private JwtAuthenticationToken validUser() {
        Jwt jwt = Jwt.withTokenValue("good").header("alg", "HS256").subject(userId).build();
        return new JwtAuthenticationToken(jwt);
    }

    @Test
    void connectWithAValidTokenSetsTheUser() {
        when(decoder.decode("good")).thenReturn(validUser().getToken());

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good", null, null), null);

        var user = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class).getUser();
        assertThat(user).isNotNull();
        assertThat(user.getName()).isEqualTo(userId); // /user/queue/... is keyed by this
    }

    @Test
    void connectWithoutOrWithABadTokenIsRejected() {
        when(decoder.decode("forged")).thenThrow(new BadJwtException("bad signature"));

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer forged", null, null), null))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscriptionsAreLimitedToOwnQueues() {
        JwtAuthenticationToken user = validUser();

        interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, "/user/queue/notifications", user), null);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SUBSCRIBE, null, "/queue/notifications-user123", user), null))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                frame(StompCommand.SEND, null, "/app/anything", user), null))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
