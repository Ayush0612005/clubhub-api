package com.clubhub.event;

import com.clubhub.security.JwtProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/**
 * Personal event tickets: {@code CH1.<club schema>.<eventId>.<userId>.<HMAC-SHA256>}.
 *
 * The signature makes tickets unforgeable: changing the event or user id breaks it. The club schema
 * is part of the signed payload because event ids are per-club identities (club A and club B both
 * have an event 5), so a ticket can never be replayed at another club's event.
 *
 * No expiry is needed: a ticket only works during that event's check-in window and only while the
 * holder is still registered, so unregistering is the revocation.
 *
 * The key is derived from the JWT secret with a fixed label (domain separation), so a ticket
 * signature can never be confused with a JWT signature even though one secret is configured.
 */
@Component
public class TicketService {

    private static final String VERSION = "CH1";
    private static final String HMAC = "HmacSHA256";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    public record Ticket(String schema, long eventId, UUID userId) {
    }

    public static class InvalidTicketException extends RuntimeException {
        public InvalidTicketException(String message) {
            super(message);
        }
    }

    private final SecretKeySpec key;

    public TicketService(JwtProperties jwtProperties) {
        byte[] master = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(hmac(new SecretKeySpec(master, HMAC), "clubhub/event-ticket/v1"), HMAC);
    }

    public String issue(String schema, long eventId, UUID userId) {
        String payload = String.join(".", VERSION, schema, Long.toString(eventId), userId.toString());
        return payload + "." + B64.encodeToString(hmac(key, payload));
    }

    public Ticket verify(String token) {
        String[] parts = token == null ? new String[0] : token.strip().split("\\.");
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            throw new InvalidTicketException("Not a ClubHub ticket");
        }
        String payload = String.join(".", parts[0], parts[1], parts[2], parts[3]);
        byte[] expected = hmac(key, payload);
        byte[] actual;
        try {
            actual = Base64.getUrlDecoder().decode(parts[4]);
        } catch (IllegalArgumentException e) {
            throw new InvalidTicketException("Ticket signature is invalid");
        }
        // constant-time comparison: response timing reveals nothing about the correct signature
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidTicketException("Ticket signature is invalid");
        }
        try {
            return new Ticket(parts[1], Long.parseLong(parts[2]), UUID.fromString(parts[3]));
        } catch (IllegalArgumentException e) {
            throw new InvalidTicketException("Ticket is malformed"); // unreachable with a valid signature
        }
    }

    private static byte[] hmac(SecretKeySpec key, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
