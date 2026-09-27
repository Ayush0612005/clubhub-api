package com.clubhub.notification;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Browsers can't set an Authorization header on the WebSocket handshake, so authentication happens
 * on the STOMP CONNECT frame instead: the client sends "Authorization: Bearer <access token>" as a
 * STOMP header, verified with the same JwtDecoder as the REST API. The session's user name is the
 * token's subject (user id), which is what /user/queue/... destinations are keyed by.
 *
 * Subscriptions are limited to the caller's own user queues, so nobody can listen to a shared
 * broker destination.
 */
@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    static final String OWN_QUEUES = "/user/queue/";

    private final JwtDecoder jwtDecoder;

    public StompAuthInterceptor(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor stomp = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (stomp == null || stomp.getCommand() == null) {
            return message;
        }
        switch (stomp.getCommand()) {
            case StompCommand.CONNECT -> stomp.setUser(authenticate(stomp.getFirstNativeHeader("Authorization")));
            case StompCommand.SUBSCRIBE -> {
                String destination = stomp.getDestination();
                if (stomp.getUser() == null || destination == null || !destination.startsWith(OWN_QUEUES)) {
                    throw new MessageDeliveryException("You can only subscribe to your own queues");
                }
            }
            case StompCommand.SEND -> throw new MessageDeliveryException("This channel is receive-only");
            default -> {
                // DISCONNECT, UNSUBSCRIBE, ACK...: nothing to check
            }
        }
        return message;
    }

    private JwtAuthenticationToken authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Missing bearer token");
        }
        try {
            return new JwtAuthenticationToken(jwtDecoder.decode(authorization.substring(7)));
        } catch (JwtException e) {
            throw new MessageDeliveryException("Invalid or expired token");
        }
    }
}
