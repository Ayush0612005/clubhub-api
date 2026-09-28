package com.clubhub.auth;

import com.clubhub.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who may have an account: anyone on an allowed college domain (exact match, no subdomains),
 * plus a short list of named exceptions. The platform admin email is always an exception,
 * so the operator can use a personal address.
 *
 * This checks the CLAIM ("my address ends in srmist.edu.in"), not ownership of the inbox.
 * Ownership needs email verification, which is the next step.
 */
@Component
public class EmailPolicy {

    private final Set<String> allowedDomains;
    private final Set<String> allowedEmails;

    public EmailPolicy(
            @Value("${clubhub.auth.allowed-email-domains:srmist.edu.in}") List<String> allowedDomains,
            @Value("${clubhub.auth.extra-allowed-emails:}") List<String> extraAllowedEmails,
            @Value("${clubhub.bootstrap.platform-admin-email:}") String platformAdminEmail) {
        this.allowedDomains = allowedDomains.stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .map(d -> d.startsWith("@") ? d.substring(1) : d)
                .filter(d -> !d.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.allowedEmails = Stream.concat(extraAllowedEmails.stream(), Stream.of(platformAdminEmail))
                .filter(e -> e != null && !e.isBlank())
                .map(User::normalizeEmail)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** True if the address may register or sign in. An empty domain list means "no restriction". */
    public boolean isAllowed(String email) {
        String normalized = User.normalizeEmail(email);
        if (normalized == null) {
            return false;
        }
        if (allowedDomains.isEmpty() || allowedEmails.contains(normalized)) {
            return true;
        }
        int at = normalized.lastIndexOf('@');
        return at > 0 && allowedDomains.contains(normalized.substring(at + 1));
    }

    public void requireAllowed(String email) {
        if (!isAllowed(email)) {
            throw new AuthExceptions.EmailNotAllowedException(allowedDomains);
        }
    }
}
